package io.huze.glamourer.ui;

import io.huze.glamourer.render.ModelRegions;
import io.huze.glamourer.render.ModelRenderer;
import io.huze.glamourer.render.OrbitGizmo;
import io.huze.glamourer.render.OrbitView;
import io.huze.glamourer.render.PosedModel;
import io.huze.glamourer.render.RegionSelection;
import io.huze.glamourer.texture.TextureAnimation;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.Setter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

public class ModelViewPanel extends JPanel
{
	private static final double ZOOM_PER_NOTCH = 1.1;
	private static final double CLOSEST_ZOOM = 0.2;
	private static final double FURTHEST_ZOOM = 5;
	private static final int SUPERSAMPLE = 2;
	private static final int DRAG_SUPERSAMPLE = 1;
	private static final int MOST_SUPERSAMPLED_PIXELS = 400_000;
	private static final Dimension MINIMUM_SIZE = new Dimension(120, 120);

	private static final float RING_WIDTH = 1.5f;
	private static final float ACTIVE_RING_WIDTH = 2.5f;
	private static final int RING_ALPHA = 200;
	private static final int BEHIND_RING_ALPHA = 70;
	private static final int ACTIVE_RING_ALPHA = 255;
	private static final Map<OrbitGizmo.Ring, Color> RING_COLORS = Map.of(
		OrbitGizmo.Ring.X, new Color(255, 0, 0),
		OrbitGizmo.Ring.Y, new Color(0, 255, 0),
		OrbitGizmo.Ring.Z, new Color(0, 0, 255),
		OrbitGizmo.Ring.VIEW, new Color(255, 255, 255));

	private final Executor executor;
	private final ModelRenderer renderer;

	@Nullable
	private PosedModel model;
	private String emptyMessage = "Loading model";
	@Nonnull
	private OrbitView view = OrbitView.INITIAL;

	@Nullable
	private Point dragOrigin;
	private boolean panning;
	private boolean hovering;
	private boolean gizmoEnabled;
	@Nullable
	private OrbitGizmo.Ring hoveredRing;
	@Nullable
	private OrbitGizmo.Drag gizmoDrag;

	@Setter
	@Nonnull
	private UnaryOperator<RegionSelection> rowSelectionOf = picked -> picked;
	@Setter
	@Nonnull
	private Consumer<RegionSelection> onPicked = picked -> {};
	@Nullable
	private RegionSelection selection;
	@Nullable
	private RegionSelection hoveredSelection;

	@Nullable
	private BufferedImage shown;
	@Nonnull
	private OrbitView shownView = OrbitView.INITIAL;
	@Nullable
	private ModelRegions shownRegions;
	@Nullable
	private BufferedImage spare;
	private boolean rendering;
	private boolean renderDue;

	@Nullable
	private BufferedImage outline;
	@Nullable
	private ModelRegions outlinedRegions;
	@Nullable
	private RegionSelection outlinedSelection;

	private final Timer animationTimer = new Timer(TextureAnimation.REDRAW_INTERVAL_MS, event -> requestRender());

	public ModelViewPanel(@Nonnull Executor executor, @Nonnull ModelRenderer renderer)
	{
		this.executor = executor;
		this.renderer = renderer;
		setMinimumSize(MINIMUM_SIZE);
		setPreferredSize(new Dimension(320, 320));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setCursor(getIdleCursor());

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				var gizmo = getGizmo();
				var ring = gizmo == null || !SwingUtilities.isLeftMouseButton(event)
					? null
					: gizmo.hitTest(event.getX(), event.getY());
				if (ring != null)
				{
					gizmoDrag = gizmo.startDrag(ring, event.getX(), event.getY());
					return;
				}
				if (SwingUtilities.isLeftMouseButton(event))
				{
					return;
				}
				dragOrigin = event.getPoint();
				panning = SwingUtilities.isRightMouseButton(event);
			}

			@Override
			public void mouseReleased(MouseEvent event)
			{
				dragOrigin = null;
				gizmoDrag = null;
				requestRender();
			}

			@Override
			public void mouseClicked(MouseEvent event)
			{
				if (SwingUtilities.isLeftMouseButton(event) && hoveredSelection != null)
				{
					onPicked.accept(hoveredSelection);
				}
			}

			@Override
			public void mouseEntered(MouseEvent event)
			{
				hovering = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				hovering = false;
				hoveredRing = null;
				hoveredSelection = null;
				repaint();
			}
		});

		addMouseMotionListener(new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent event)
			{
				var gizmo = getGizmo();
				var ring = gizmo == null ? null : gizmo.hitTest(event.getX(), event.getY());
				if (ring != hoveredRing)
				{
					hoveredRing = ring;
					setCursor(ring == null ? getIdleCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
					repaint();
				}
				var under = ring != null ? null : getSelectionUnder(event.getX(), event.getY());
				if (!Objects.equals(under, hoveredSelection))
				{
					hoveredSelection = under;
					repaint();
				}
			}

			@Override
			public void mouseDragged(MouseEvent event)
			{
				if (gizmoDrag != null)
				{
					setView(gizmoDrag.dragTo(view, event.getX(), event.getY()));
					return;
				}
				if (dragOrigin == null)
				{
					return;
				}
				int dx = event.getX() - dragOrigin.x;
				int dy = event.getY() - dragOrigin.y;
				dragOrigin = event.getPoint();
				if (panning)
				{
					setView(view.panned(dx, dy));
				}
				else
				{
					turn(dx, dy);
				}
			}
		});

		addMouseWheelListener(event -> zoom(event.getWheelRotation()));

		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent event)
			{
				requestRender();
			}
		});
	}

	public void setModel(@Nullable PosedModel model, String emptyMessage)
	{
		this.model = model;
		this.emptyMessage = emptyMessage;
		updateAnimationTimer();
		requestRender();
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		updateAnimationTimer();
	}

	@Override
	public void removeNotify()
	{
		animationTimer.stop();
		super.removeNotify();
	}

	private void updateAnimationTimer()
	{
		if (model != null && model.getSnapshot().isAnimated() && isDisplayable())
		{
			animationTimer.start();
		}
		else
		{
			animationTimer.stop();
		}
	}

	@Nonnull
	public OrbitView getView()
	{
		return view;
	}

	public void setView(@Nonnull OrbitView view)
	{
		this.view = view;
		requestRender();
	}

	public void resetView()
	{
		setView(OrbitView.INITIAL);
	}

	public void setGizmoEnabled(boolean enabled)
	{
		gizmoEnabled = enabled;
		hoveredRing = null;
		setCursor(getIdleCursor());
		repaint();
	}

	public void setSelection(@Nullable RegionSelection selection)
	{
		this.selection = selection;
		repaint();
	}

	@Nonnull
	private Cursor getIdleCursor()
	{
		return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
	}

	@Nullable
	private RegionSelection getSelectionUnder(int x, int y)
	{
		if (shownRegions == null)
		{
			return null;
		}
		int textureIndex = shownRegions.getTextureIndexAt(x, y);
		if (textureIndex != ModelRegions.NONE)
		{
			return rowSelectionOf.apply(RegionSelection.ofTexture(textureIndex));
		}
		int colorIndex = shownRegions.getColorIndexAt(x, y);
		return colorIndex == ModelRegions.NONE ? null : rowSelectionOf.apply(RegionSelection.ofColor(colorIndex));
	}

	private void turn(int dx, int dy)
	{
		double turnSign = Math.cos(view.getTilt()) >= 0 ? -1 : 1;
		double radiansPerPixel = getRadiansPerPixel();
		setView(view.turned(dx * radiansPerPixel * turnSign, dy * radiansPerPixel));
	}

	private double getRadiansPerPixel()
	{
		return 2 * Math.PI / Math.max(1, Math.min(getWidth(), getHeight()));
	}

	private void zoom(int notches)
	{
		double zoom = Math.max(CLOSEST_ZOOM,
			Math.min(FURTHEST_ZOOM, view.getZoom() * Math.pow(ZOOM_PER_NOTCH, notches)));
		setView(view.zoomedTo(zoom));
	}

	private void requestRender()
	{
		renderDue = true;
		if (!rendering)
		{
			startRender();
		}
	}

	private void startRender()
	{
		renderDue = false;
		final PosedModel model = this.model;
		final int width = getWidth();
		final int height = getHeight();
		if (model == null || width <= 0 || height <= 0)
		{
			repaint();
			return;
		}

		final int supersample = dragOrigin != null || gizmoDrag != null || width * height > MOST_SUPERSAMPLED_PIXELS
			? DRAG_SUPERSAMPLE
			: SUPERSAMPLE;
		final OrbitView view = this.view;
		final BufferedImage target = spare != null && spare.getWidth() == width && spare.getHeight() == height
			? spare
			: new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		spare = null;
		rendering = true;
		executor.execute(() -> {
			ModelRegions regions = null;
			try
			{
				regions = renderer.drawOrbit(model, view, supersample, target);
			}
			finally
			{
				final ModelRegions drawnRegions = regions;
				SwingUtilities.invokeLater(() -> {
					rendering = false;
					spare = shown;
					shown = target;
					shownView = view;
					shownRegions = drawnRegions;
					repaint();
					if (renderDue)
					{
						startRender();
					}
				});
			}
		});
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g;
		g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
		g2.fillRect(0, 0, getWidth(), getHeight());

		if (model == null)
		{
			paintMessage(g2);
			return;
		}
		if (shown != null)
		{
			g2.drawImage(shown, 0, 0, null);
		}
		var outlined = selection != null ? selection : hoveredSelection;
		if (outlined != null && shownRegions != null)
		{
			g2.drawImage(getOutline(shownRegions, outlined), 0, 0, null);
		}
		if (hovering || gizmoDrag != null)
		{
			paintGizmo(g2);
		}
	}

	@Nonnull
	private BufferedImage getOutline(@Nonnull ModelRegions regions, @Nonnull RegionSelection selection)
	{
		if (outline == null || regions != outlinedRegions || !selection.equals(outlinedSelection))
		{
			int width = shown == null ? getWidth() : shown.getWidth();
			int height = shown == null ? getHeight() : shown.getHeight();
			if (outline == null || outline.getWidth() != width || outline.getHeight() != height)
			{
				outline = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			}
			regions.outline(selection, outline);
			outlinedRegions = regions;
			outlinedSelection = selection;
		}
		return outline;
	}

	@Nullable
	private OrbitGizmo getGizmo()
	{
		if (!gizmoEnabled || model == null || shown == null)
		{
			return null;
		}
		return OrbitGizmo.of(model, shownView, shown.getWidth(), shown.getHeight());
	}

	private void paintGizmo(Graphics2D g2)
	{
		var gizmo = getGizmo();
		if (gizmo == null)
		{
			return;
		}
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		var active = gizmoDrag != null ? gizmoDrag.getRing() : hoveredRing;
		for (var shape : gizmo.getRings())
		{
			boolean isActive = shape.getRing() == active;
			g2.setStroke(new BasicStroke(isActive ? ACTIVE_RING_WIDTH : RING_WIDTH));
			var color = RING_COLORS.get(shape.getRing());
			int count = shape.getPointCount();
			for (int point = 0; point < count; point++)
			{
				int next = (point + 1) % count;
				boolean faint = shape.isBehind(point) && shape.isBehind(next);
				g2.setColor(withAlpha(color, isActive ? ACTIVE_RING_ALPHA : faint ? BEHIND_RING_ALPHA : RING_ALPHA));
				g2.draw(new Line2D.Double(shape.getX(point), shape.getY(point), shape.getX(next), shape.getY(next)));
			}
		}
	}

	private static Color withAlpha(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

	private void paintMessage(Graphics2D g2)
	{
		g2.setFont(new Font(FontManager.getRunescapeBoldFont().getName(), Font.PLAIN, 14));
		g2.setColor(ColorScheme.BRAND_ORANGE);
		FontMetrics metrics = g2.getFontMetrics();
		g2.drawString(emptyMessage,
			getWidth() / 2 - metrics.stringWidth(emptyMessage) / 2,
			getHeight() / 2 + metrics.getHeight() / 2);
	}
}
