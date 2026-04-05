package io.huze.glamourer.ui.colorpicker;

import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.color.Colors;
import io.huze.glamourer.ui.DialogUtil;
import io.huze.glamourer.ui.Hoverable;
import io.huze.glamourer.ui.ImageIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.Setter;

public abstract class ColorLabel extends JPanel implements Hoverable
{
	public static final int ROW_HEIGHT = 24;

	@Setter
	@Nonnull private Consumer<Short> onColorChange = x -> {};
	@Setter
	@Nonnull private Consumer<Short> onColorPreview = x -> {};
	@Setter
	@Nonnull private Runnable onHoverStart = () -> {};
	@Setter
	@Nonnull private Runnable onHoverEnd = () -> {};
	private final String pickerName;
	private final short originalHsl;
	protected final JLabel colorDisplay;
	protected final JButton revertButton;
	private final JPanel revertWrapper;
	private JDialog activeDialog;

	protected ColorLabel(String name, short originalHsl)
	{
		this.pickerName = name;
		this.originalHsl = originalHsl;

		setLayout(new BorderLayout(3, 0));
		setOpaque(false);

		// Main color display label (clickable to open picker)
		colorDisplay = new JLabel()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				var replacementShadedColors = getColorReplacements().stream()
					.map(r -> Colors.hslToShadedColor(r.getReplacement()))
					.toArray(Color[]::new);
				ColorUtil.paintColorSpread(g, 0, 0, getWidth(), getHeight(), replacementShadedColors);
				super.paintComponent(g);
			}
		};
		colorDisplay.setOpaque(false);
		colorDisplay.setBorder(new EmptyBorder(5, 5, 5, 5));
		colorDisplay.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		colorDisplay.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
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
		add(colorDisplay, BorderLayout.CENTER);

		// Revert button (double-click to revert)
		revertButton = new JButton()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				var originalShadedColors = getColorReplacements().stream()
					.map(r -> Colors.hslToShadedColor(r.getOriginal()))
					.toArray(Color[]::new);
				ColorUtil.paintColorSpread(g, 0, 0, getWidth(), getHeight(), originalShadedColors);
				super.paintComponent(g);
			}
		};
		revertButton.setPreferredSize(new Dimension(ROW_HEIGHT, ROW_HEIGHT));
		revertButton.setToolTipText("Double-click to revert to original color");
		revertButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		revertButton.setBorderPainted(false);
		revertButton.setFocusPainted(false);
		revertButton.setContentAreaFilled(false);
		Color originalColor = Colors.hslToColor(originalHsl);
		revertButton.setBackground(originalColor);
		ImageIcons.setResetIcon(revertButton, originalColor);
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
		revertWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		revertWrapper.setOpaque(false);
		revertWrapper.add(revertButton);
		add(revertWrapper, BorderLayout.EAST);
	}

	protected void updateColorDisplay()
	{
		List<ColorReplacement> colors = getColorReplacements();
		short medianHsl = colors.get(colors.size() / 2).getReplacement();
		Color displayColor = Colors.hslToShadedColor(medianHsl);
		colorDisplay.setBackground(displayColor);
		colorDisplay.setForeground(getContrastColor(displayColor));
		colorDisplay.setText(getDisplayText());

		boolean hasChanges = colors.stream().anyMatch(ColorReplacement::hasChanged);
		revertWrapper.setVisible(hasChanges);
		short medianOriginalHsl = colors.get(colors.size() / 2).getOriginal();
		ImageIcons.setResetIcon(revertButton, Colors.hslToShadedColor(medianOriginalHsl));
		revertButton.repaint();
	}

	private static Color getContrastColor(Color color)
	{
		return ImageIcons.shouldUseDarkForeground(color) ? Color.BLACK : Color.WHITE;
	}

	protected void notifyColorChange(short newColor)
	{
		onColorChange.accept(newColor);
	}

	public void setViewOnly()
	{
		colorDisplay.setCursor(Cursor.getDefaultCursor());
		revertButton.setCursor(Cursor.getDefaultCursor());
		revertButton.setToolTipText(null);
		revertButton.setIcon(null);
		revertButton.setRolloverIcon(null);
		for (var listener : colorDisplay.getMouseListeners()) {
			colorDisplay.removeMouseListener(listener);
		}
	}

	public void showPicker()
	{
		if (activeDialog != null && activeDialog.isVisible())
		{
			activeDialog.toFront();
			return;
		}

		final var initialColor = getPickerInitialColor();
		HslColorPicker picker = new HslColorPicker(originalHsl, initialColor);
		picker.setOnChange(onColorPreview);
		final AtomicBoolean okPressed = new AtomicBoolean();
		activeDialog = DialogUtil.showModelessDialogNearCursor(
			this,
			picker,
			pickerName,
			picker.getModeControls(),
			d -> {
				okPressed.set(true);
				if (picker.getColor() != initialColor)
				{
					onColorChanged(picker.getColor());
				}
			}
		);
		activeDialog.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosed(WindowEvent e)
			{
				if (!okPressed.get() && picker.getColor() != initialColor)
				{
					onColorPreview.accept(initialColor);
				}
				activeDialog = null;
			}
		});
	}

	protected abstract List<ColorReplacement> getColorReplacements();

	protected abstract String getDisplayText();

	protected abstract void revertToOriginal();

	protected abstract void onColorChanged(short newColor);

	protected abstract short getPickerInitialColor();
}
