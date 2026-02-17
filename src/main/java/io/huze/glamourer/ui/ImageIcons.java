package io.huze.glamourer.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

public class ImageIcons
{
	private static final ImageIcon EXPAND_ICON = loadImageIcon("expand.png");
	private static final ImageIcon COLLAPSE_ICON = loadImageIcon("collapse.png");
	private static final ImageIcon COLLAPSE_ALL_ICON = loadImageIcon("collapse_all.png");
	private static final ImageIcon EXPAND_ALL_ICON = loadImageIcon("expand_all.png");
	private static final ImageIcon CREATE_ICON = loadImageIcon("create.png");
	private static final ImageIcon EDIT_ICON = loadImageIcon("edit.png");
	private static final ImageIcon CLOSE_ICON = loadImageIcon("close.png");
	private static final ImageIcon COPY_ICON = loadImageIcon("copy.png");
	private static final ImageIcon RESET_ICON = loadImageIcon("reset.png");
	private static final ImageIcon RESET_ICON_DARK = invertImageIcon(RESET_ICON);
	private static final ImageIcon BIN_ICON = loadImageIcon("bin.png");

	private static final ImageIcon EXPAND_ICON_HOVERED = brightenImageIcon(EXPAND_ICON);
	private static final ImageIcon COLLAPSE_ICON_HOVERED = brightenImageIcon(COLLAPSE_ICON);
	private static final ImageIcon COLLAPSE_ALL_ICON_HOVERED = brightenImageIcon(COLLAPSE_ALL_ICON);
	private static final ImageIcon EXPAND_ALL_ICON_HOVERED = brightenImageIcon(EXPAND_ALL_ICON);
	private static final ImageIcon CREATE_ICON_HOVERED = brightenImageIcon(CREATE_ICON);
	private static final ImageIcon EDIT_ICON_HOVERED = brightenImageIcon(EDIT_ICON);
	private static final ImageIcon CLOSE_ICON_HOVERED = brightenImageIcon(CLOSE_ICON);
	private static final ImageIcon COPY_ICON_HOVERED = brightenImageIcon(COPY_ICON);
	private static final ImageIcon RESET_ICON_HOVERED = brightenImageIcon(RESET_ICON);
	private static final ImageIcon RESET_ICON_DARK_HOVERED = brightenImageIcon(RESET_ICON_DARK);
	private static final ImageIcon BIN_ICON_HOVERED = brightenImageIcon(BIN_ICON);

	public static final ImageIcon ON_SWITCHER;
	public static final ImageIcon OFF_SWITCHER;

	static
	{
		BufferedImage onSwitcher = ImageUtil.loadImageResource(ImageIcons.class, "switcher_on.png");
		ON_SWITCHER = new ImageIcon(onSwitcher);
		OFF_SWITCHER = new ImageIcon(ImageUtil.flipImage(
			ImageUtil.luminanceScale(
				ImageUtil.grayscaleImage(onSwitcher),
				0.61f
			),
			true,
			false
		));
	}

	private static void configureIconButton(JButton button)
	{
		button.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		button.setContentAreaFilled(false);
		button.setFocusPainted(false);
	}

	public static void setExpandIcon(JButton button, boolean expanded)
	{
		configureIconButton(button);
		button.setIcon(expanded ? COLLAPSE_ICON : EXPAND_ICON);
		button.setRolloverIcon(expanded ? COLLAPSE_ICON_HOVERED : EXPAND_ICON_HOVERED);
	}

	public static void setEditIcon(JButton button)
	{
		configureIconButton(button);
		button.setIcon(EDIT_ICON);
		button.setRolloverIcon(EDIT_ICON_HOVERED);
	}

	public static void setCloseIcon(JButton button)
	{
		configureIconButton(button);
		button.setIcon(CLOSE_ICON);
		button.setRolloverIcon(CLOSE_ICON_HOVERED);
	}

	public static void setCopyIcon(JButton button)
	{
		configureIconButton(button);
		button.setIcon(COPY_ICON);
		button.setRolloverIcon(COPY_ICON_HOVERED);
	}

	public static void setExpandCollapseAllIcon(JButton button, boolean showCollapseAll)
	{
		configureIconButton(button);
		button.setIcon(showCollapseAll ? COLLAPSE_ALL_ICON : EXPAND_ALL_ICON);
		button.setRolloverIcon(showCollapseAll ? COLLAPSE_ALL_ICON_HOVERED : EXPAND_ALL_ICON_HOVERED);
	}

	public static void setCreateIcon(JButton button)
	{
		configureIconButton(button);
		button.setIcon(CREATE_ICON);
		button.setRolloverIcon(CREATE_ICON_HOVERED);
	}

	public static void setBinIcon(JButton button)
	{
		configureIconButton(button);
		button.setIcon(BIN_ICON);
		button.setRolloverIcon(BIN_ICON_HOVERED);
	}

	public static void setResetIcon(JButton button, Color backgroundColor)
	{
		configureIconButton(button);
		boolean useDark = shouldUseDarkForeground(backgroundColor);
		button.setIcon(useDark ? RESET_ICON_DARK : RESET_ICON);
		button.setRolloverIcon(useDark ? RESET_ICON_DARK_HOVERED : RESET_ICON_HOVERED);
	}

	public static void setScaledIcon(JLabel label, BufferedImage image, float iconScale)
	{
		if (image == null)
		{
			return;
		}
		int w = (int) (image.getWidth() * iconScale);
		int h = (int) (image.getHeight() * iconScale);
		var dimension = new Dimension(w, h);
		label.setPreferredSize(dimension);
		label.setMinimumSize(dimension);

		if (image instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) image).onLoaded(() ->
				SwingUtilities.invokeLater(() -> applyScaledIcon(label, image, dimension)));
		}
		else
		{
			applyScaledIcon(label, image, dimension);
		}
	}

	private static void applyScaledIcon(JLabel label, BufferedImage image, Dimension dimension)
	{
		label.setIcon(new ImageIcon(image.getScaledInstance(dimension.width, dimension.height, Image.SCALE_SMOOTH)));
	}

	public static boolean shouldUseDarkForeground(Color color)
	{
		double luma = 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
		return luma >= 128;
	}

	private static ImageIcon loadImageIcon(String path)
	{
		return new ImageIcon(ImageUtil.loadImageResource(ImageIcons.class, path));
	}

	private static ImageIcon brightenImageIcon(ImageIcon icon)
	{
		Image img = icon.getImage();
		BufferedImage bi = new BufferedImage(
			img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

		Graphics g = bi.createGraphics();
		g.drawImage(img, 0, 0, null);
		g.dispose();

		RescaleOp op = new RescaleOp(1.2f, 0, null);
		bi = op.filter(bi, null);

		return new ImageIcon(bi);
	}

	private static ImageIcon invertImageIcon(ImageIcon icon)
	{
		Image img = icon.getImage();
		int width = img.getWidth(null);
		int height = img.getHeight(null);
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		Graphics g = bi.createGraphics();
		g.drawImage(img, 0, 0, null);
		g.dispose();

		// Invert RGB while preserving alpha
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int rgba = bi.getRGB(x, y);
				int a = (rgba >> 24) & 0xFF;
				int r = 255 - ((rgba >> 16) & 0xFF);
				int g2 = 255 - ((rgba >> 8) & 0xFF);
				int b = 255 - (rgba & 0xFF);
				bi.setRGB(x, y, (a << 24) | (r << 16) | (g2 << 8) | b);
			}
		}

		return new ImageIcon(bi);
	}
}
