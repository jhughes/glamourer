package io.huze.glamourer.ui.texturepicker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TexturePicker extends JPanel
{
	private static final int BORDER_SIZE = 2;
	private static final int GAP = 2;
	private static final Border SELECTED_BORDER = BorderFactory.createLineBorder(Color.WHITE, BORDER_SIZE);
	private static final Border HOVER_BORDER = BorderFactory.createLineBorder(Color.LIGHT_GRAY, BORDER_SIZE);
	private static final Border DEFAULT_BORDER = BorderFactory.createLineBorder(Color.DARK_GRAY, BORDER_SIZE);
	private static final int[] SIZES = {32, 64, 128};

	private static Map<Short, BufferedImage> textureCache;
	private static int lastSizeIndex = 1;
	private static Dimension lastWindowSize = new Dimension(430, 400);

	@Getter
	private short selectedTextureId;
	@Getter
	private final JPanel sizeControls;
	private int thumbSize = SIZES[lastSizeIndex];
	private final JPanel gridPanel;
	private final JScrollPane scrollPane;

	public TexturePicker(short currentTextureId)
	{
		this.selectedTextureId = currentTextureId;
		setLayout(new BorderLayout());

		loadTextures();

		JComboBox<String> sizeCombo = new JComboBox<>(new String[]{"Small", "Medium", "Full"});
		sizeCombo.setSelectedIndex(lastSizeIndex);
		sizeCombo.addActionListener(e -> {
			lastSizeIndex = sizeCombo.getSelectedIndex();
			thumbSize = SIZES[lastSizeIndex];
			rebuildGrid();
		});
		sizeControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		sizeControls.add(new JLabel("Size:"));
		sizeControls.add(sizeCombo);

		gridPanel = new JPanel();
		scrollPane = new JScrollPane(gridPanel);
		scrollPane.setPreferredSize(lastWindowSize);
		scrollPane.addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				updateGridSize();
				lastWindowSize = scrollPane.getSize();
			}
		});
		add(scrollPane, BorderLayout.CENTER);

		rebuildGrid();
	}

	private void rebuildGrid()
	{
		gridPanel.removeAll();
		gridPanel.setLayout(new FlowLayout(FlowLayout.LEFT, GAP, GAP));
		previousSelected = null;

		int cellSize = thumbSize + BORDER_SIZE * 2;

		for (var entry : textureCache.entrySet())
		{
			short textureId = entry.getKey();
			BufferedImage img = entry.getValue();
			JLabel cell = createTextureCell(textureId, img, cellSize);
			gridPanel.add(cell);
		}

		updateGridSize();
		scrollPane.getVerticalScrollBar().setUnitIncrement(thumbSize);
	}

	private void updateGridSize()
	{
		int cellSize = thumbSize + BORDER_SIZE * 2;
		int viewWidth = scrollPane.getViewport().getWidth();
		if (viewWidth <= 0)
		{
			viewWidth = scrollPane.getPreferredSize().width - 20;
		}
		int cols = Math.max(1, (viewWidth - GAP) / (cellSize + GAP));
		int rows = (textureCache.size() + cols - 1) / cols;
		gridPanel.setPreferredSize(new Dimension(viewWidth, rows * (cellSize + GAP) + GAP));

		gridPanel.revalidate();
		gridPanel.repaint();
	}

	private JLabel createTextureCell(short textureId, BufferedImage img, int cellSize)
	{
		Image scaled = img.getScaledInstance(thumbSize, thumbSize, Image.SCALE_SMOOTH);
		JLabel cell = new JLabel(new ImageIcon(scaled));
		cell.setPreferredSize(new Dimension(cellSize, cellSize));
		cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		cell.setToolTipText("Texture " + textureId);
		cell.setBorder(textureId == selectedTextureId ? SELECTED_BORDER : DEFAULT_BORDER);

		cell.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (textureId != selectedTextureId)
				{
					cell.setBorder(HOVER_BORDER);
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (textureId != selectedTextureId)
				{
					cell.setBorder(DEFAULT_BORDER);
				}
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				selectTexture(textureId, cell);
			}
		});

		return cell;
	}

	private JLabel previousSelected;

	private void selectTexture(short textureId, JLabel cell)
	{
		if (previousSelected != null)
		{
			previousSelected.setBorder(DEFAULT_BORDER);
		}
		selectedTextureId = textureId;
		cell.setBorder(SELECTED_BORDER);
		previousSelected = cell;
	}

	private static synchronized void loadTextures()
	{
		if (textureCache != null)
		{
			return;
		}
		textureCache = new LinkedHashMap<>();
		int consecutiveMisses = 0;
		for (int i = 0; consecutiveMisses < 3; i++)
		{
			try (InputStream is = TexturePicker.class.getResourceAsStream(i + ".png"))
			{
				if (is != null)
				{
					textureCache.put((short) i, ImageIO.read(is));
					consecutiveMisses = 0;
				}
				else
				{
					consecutiveMisses++;
				}
			}
			catch (IOException e)
			{
				log.warn("Failed to load texture {}", i, e);
				consecutiveMisses++;
			}
		}
	}

	public static BufferedImage getTextureImage(short textureId)
	{
		loadTextures();
		return textureCache.get(textureId);
	}
}
