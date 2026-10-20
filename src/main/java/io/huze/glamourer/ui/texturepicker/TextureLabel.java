package io.huze.glamourer.ui.texturepicker;

import io.huze.glamourer.texture.TextureAnimation;
import io.huze.glamourer.texture.TextureImage;
import io.huze.glamourer.texture.TextureImages;
import io.huze.glamourer.texture.TextureReplacement;
import io.huze.glamourer.ui.DialogUtil;
import io.huze.glamourer.ui.Hoverable;
import io.huze.glamourer.ui.ImageIcons;
import io.huze.glamourer.ui.colorpicker.ColorLabel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.Setter;

public class TextureLabel extends JPanel implements Hoverable
{
	private static final int ICON_SIZE = ColorLabel.ROW_HEIGHT;

	@Setter
	@Nonnull private Consumer<Short> onTextureChange = x -> {};
	@Setter
	@Nonnull private Consumer<Short> onTexturePreview = x -> {};
	@Setter
	@Nonnull private Runnable onHoverStart = () -> {};
	@Setter
	@Nonnull private Runnable onHoverEnd = () -> {};
	private final String pickerName;
	private final TextureReplacement textureReplacement;
	@Nonnull
	private final TextureImages textureImageSource;
	@Nonnull
	private Map<Short, TextureImage> textureImages = Map.of();
	private boolean pickerLoading;
	private final JLabel textureDisplay;
	private final JButton revertButton;
	@Nonnull
	private TextureIcon replacementIcon = new TextureIcon(ICON_SIZE, null);
	@Nonnull
	private TextureIcon originalIcon = new TextureIcon(ICON_SIZE, null);
	private final Timer animationTimer = new Timer(TextureAnimation.REDRAW_INTERVAL_MS, e -> repaintTextures());
	private JDialog activeDialog;

	public TextureLabel(String name, TextureReplacement textureReplacement, @Nonnull TextureImages textureImageSource)
	{
		this.pickerName = name;
		this.textureReplacement = textureReplacement;
		this.textureImageSource = textureImageSource;

		setLayout(new BorderLayout(3, 0));
		setOpaque(false);

		textureDisplay = new JLabel();
		textureDisplay.setOpaque(true);
		textureDisplay.setBackground(Color.BLACK);
		textureDisplay.setBorder(null);
		textureDisplay.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		textureDisplay.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				showPicker();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				onHoverStart.run();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				onHoverEnd.run();
			}
		});
		add(textureDisplay, BorderLayout.CENTER);

		revertButton = new JButton()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				originalIcon.paintScaled(g, 0, 0, getWidth(), getHeight());
				super.paintComponent(g);
			}
		};
		revertButton.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
		revertButton.setToolTipText("Double-click to revert to original texture");
		revertButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		revertButton.setBorderPainted(false);
		revertButton.setFocusPainted(false);
		revertButton.setContentAreaFilled(false);
		ImageIcons.setResetIcon(revertButton, Color.DARK_GRAY);
		revertButton.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2)
				{
					revertToOriginal();
				}
			}
		});
		add(revertButton, BorderLayout.EAST);

		updateDisplay();
		textureImageSource.loadAsync().thenAccept(images -> SwingUtilities.invokeLater(() -> {
			textureImages = images;
			updateDisplay();
		}));
	}

	private void updateDisplay()
	{
		replacementIcon = new TextureIcon(ICON_SIZE, textureImages.get(textureReplacement.getReplacement()));
		originalIcon = new TextureIcon(ICON_SIZE, textureImages.get(textureReplacement.getOriginal()));
		textureDisplay.setIcon(replacementIcon);
		updateAnimationTimer();
		textureDisplay.setText("Texture " + textureReplacement.getReplacement());
		textureDisplay.setForeground(Color.WHITE);

		revertButton.setVisible(textureReplacement.hasChanged());
		revertButton.repaint();
	}

	public void showPicker()
	{
		if (activeDialog != null && activeDialog.isVisible())
		{
			activeDialog.toFront();
			return;
		}
		if (pickerLoading)
		{
			return;
		}
		pickerLoading = true;
		textureImageSource.loadAsync().thenAccept(images -> SwingUtilities.invokeLater(() -> {
			pickerLoading = false;
			textureImages = images;
			if (isDisplayable())
			{
				updateDisplay();
				openPicker();
			}
		}));
	}

	private void openPicker()
	{
		final short initialTexture = textureReplacement.getReplacement();
		TexturePicker picker = new TexturePicker(initialTexture, textureImages);
		picker.setOnChange(onTexturePreview);
		final AtomicBoolean okPressed = new AtomicBoolean();
		activeDialog = DialogUtil.showModelessDialogNearCursor(
			this,
			picker,
			pickerName,
			picker.getSizeControls(),
			d -> {
				okPressed.set(true);
				short selected = picker.getSelectedTextureId();
				if (selected != initialTexture)
				{
					textureReplacement.setReplacement(selected);
					updateDisplay();
					onTextureChange.accept(selected);
				}
			}
		);
		activeDialog.setResizable(true);
		activeDialog.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosed(WindowEvent e)
			{
				if (!okPressed.get() && picker.getSelectedTextureId() != initialTexture)
				{
					onTexturePreview.accept(initialTexture);
				}
				activeDialog = null;
			}
		});
	}

	private void revertToOriginal()
	{
		if (textureReplacement.hasChanged())
		{
			textureReplacement.setReplacement(textureReplacement.getOriginal());
			updateDisplay();
			onTextureChange.accept(textureReplacement.getReplacement());
		}
	}

	public void setViewOnly()
	{
		textureDisplay.setCursor(Cursor.getDefaultCursor());
		revertButton.setCursor(Cursor.getDefaultCursor());
		revertButton.setToolTipText(null);
		revertButton.setIcon(null);
		revertButton.setRolloverIcon(null);
		for (var listener : textureDisplay.getMouseListeners())
		{
			textureDisplay.removeMouseListener(listener);
		}
	}

	private void repaintTextures()
	{
		textureDisplay.repaint();
		revertButton.repaint();
	}

	private void updateAnimationTimer()
	{
		boolean animated = replacementIcon.isAnimated()
			|| (textureReplacement.hasChanged() && originalIcon.isAnimated());
		if (animated && isDisplayable())
		{
			animationTimer.start();
		}
		else
		{
			animationTimer.stop();
		}
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
		onHoverEnd.run();
		if (activeDialog != null)
		{
			activeDialog.dispose();
			activeDialog = null;
		}
		super.removeNotify();
	}
}
