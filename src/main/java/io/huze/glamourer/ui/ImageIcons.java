package io.huze.glamourer.ui;

import io.huze.glamourer.glam.GlamourVisibility;
import io.huze.glamourer.plate.DisplayStyle;
import io.huze.glamourer.plate.IconStyle;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import javax.annotation.Nonnull;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

public class ImageIcons
{
	private static final String ICON_GENERATION_KEY = "glamourer.iconGeneration";

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
	private static final ImageIcon IMPORT_ICON = loadImageIcon("import.png");
	private static final ImageIcon EXPORT_ICON = loadImageIcon("export.png");
	private static final ImageIcon DISCORD_ICON = loadImageIcon("discord.png");
	private static final ImageIcon LOCAL_ICON = loadImageIcon("local.png");
	private static final ImageIcon GLOBAL_ICON = loadImageIcon("global.png");
	private static final ImageIcon EYE_ICON = loadImageIcon("eye.png");
	private static final ImageIcon EYE_STRIKE_BASE_ICON = loadImageIcon("eye_strike.png");
	private static final ImageIcon EYE_STRIKE_ICON = tintImageIcon(EYE_STRIKE_BASE_ICON, new Color(255, 80, 80));
	private static final ImageIcon EYE_DISABLED_ICON = tintImageIcon(EYE_STRIKE_BASE_ICON, Color.GRAY);
	private static final ImageIcon EYE_WORN_ONLY_ICON = tintImageIcon(EYE_STRIKE_BASE_ICON, new Color(80, 200, 80));
	private static final ImageIcon EYE_VISIBLE_ICON = tintImageIcon(EYE_ICON, new Color(80, 255, 80));
	private static final ImageIcon EYE_OTHERS_ICON = tintImageIcon(EYE_ICON, new Color(255, 220, 50));

	private static final ImageIcon EXPAND_ICON_HOVERED = darkenImageIcon(EXPAND_ICON);
	private static final ImageIcon COLLAPSE_ICON_HOVERED = darkenImageIcon(COLLAPSE_ICON);
	private static final ImageIcon COLLAPSE_ALL_ICON_HOVERED = darkenImageIcon(COLLAPSE_ALL_ICON);
	private static final ImageIcon EXPAND_ALL_ICON_HOVERED = darkenImageIcon(EXPAND_ALL_ICON);
	private static final ImageIcon CREATE_ICON_HOVERED = darkenImageIcon(CREATE_ICON);
	private static final ImageIcon CLOSE_ICON_HOVERED = darkenImageIcon(CLOSE_ICON);
	private static final ImageIcon COPY_ICON_HOVERED = darkenImageIcon(COPY_ICON);
	private static final ImageIcon RESET_ICON_HOVERED = darkenImageIcon(RESET_ICON);
	private static final ImageIcon RESET_ICON_DARK_HOVERED = darkenImageIcon(RESET_ICON_DARK);
	private static final ImageIcon BIN_ICON_HOVERED = darkenImageIcon(BIN_ICON);
	private static final ImageIcon IMPORT_ICON_HOVERED = darkenImageIcon(IMPORT_ICON);
	private static final ImageIcon DISCORD_ICON_HOVERED = darkenImageIcon(DISCORD_ICON);

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
		SwingUtil.removeButtonDecorations(button);
		button.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
	}

	public static void setExpandIcon(JButton button, boolean expanded)
	{
		configureIconButton(button);
		button.setIcon(expanded ? COLLAPSE_ICON : EXPAND_ICON);
		button.setRolloverIcon(expanded ? COLLAPSE_ICON_HOVERED : EXPAND_ICON_HOVERED);
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

	public static void setImportIcon(JButton button)
	{
		configureIconButton(button);
		button.setIcon(IMPORT_ICON);
		button.setRolloverIcon(IMPORT_ICON_HOVERED);
	}

	public static void setDiscordIcon(JButton button)
	{
		configureIconButton(button);
		button.setIcon(DISCORD_ICON);
		button.setRolloverIcon(DISCORD_ICON_HOVERED);
	}

	public static ImageIcon getDisplayStyleIcon(DisplayStyle displayStyle)
	{
		return displayStyle == DisplayStyle.LOCAL ? LOCAL_ICON : GLOBAL_ICON;
	}

	public static ImageIcon getEditIcon()
	{
		return EDIT_ICON;
	}

	public static ImageIcon getExportIcon()
	{
		return EXPORT_ICON;
	}

	public static ImageIcon getBinIcon()
	{
		return BIN_ICON;
	}

	public static ImageIcon getWornOnlyIcon()
	{
		return EYE_WORN_ONLY_ICON;
	}

	public static ImageIcon getIconStyleIcon(IconStyle iconStyle)
	{
		return iconStyle == IconStyle.NORMAL ? EYE_ICON : EYE_STRIKE_BASE_ICON;
	}

	public static ImageIcon getEyeIcon()
	{
		return EYE_ICON;
	}

	public static ImageIcon getEyeStrikeIcon()
	{
		return EYE_STRIKE_BASE_ICON;
	}

	public static ImageIcon getVisibilityIcon(GlamourVisibility visibility)
	{
		switch (visibility)
		{
			case DISABLED:
				return EYE_DISABLED_ICON;
			case HIDDEN:
				return EYE_STRIKE_ICON;
			case VISIBLE:
				return EYE_VISIBLE_ICON;
			case OTHERS:
				return EYE_OTHERS_ICON;
			default:
				return EYE_STRIKE_ICON;
		}
	}

	public static void setResetIcon(JButton button, Color backgroundColor)
	{
		configureIconButton(button);
		boolean useDark = shouldUseDarkForeground(backgroundColor);
		button.setIcon(useDark ? RESET_ICON_DARK : RESET_ICON);
		button.setRolloverIcon(useDark ? RESET_ICON_DARK_HOVERED : RESET_ICON_HOVERED);
	}

	public static void setIconWithComponentHeight(@Nonnull JLabel label, @Nonnull BufferedImage image, @Nonnull JComponent component)
	{
		setScaledIcon(label, image, (float) component.getPreferredSize().height / image.getHeight());
	}

	public static void setScaledIcon(@Nonnull JLabel label, @Nonnull BufferedImage image, float iconScale)
	{
		int w = (int) (image.getWidth() * iconScale);
		int h = (int) (image.getHeight() * iconScale);
		var dimension = new Dimension(w, h);
		label.setPreferredSize(dimension);
		label.setMinimumSize(dimension);

		if (image instanceof AsyncBufferedImage)
		{
			// Each async image gets a unique generation. Stale async callbacks are skipped.
			final var generation = new Object();
			label.putClientProperty(ICON_GENERATION_KEY, generation);

			((AsyncBufferedImage) image).onLoaded(() ->
				SwingUtilities.invokeLater(() ->
				{
					if (label.getClientProperty(ICON_GENERATION_KEY) == generation)
					{
						applyScaledIcon(label, image, dimension);
					}
				}));
		}
		else
		{
			label.putClientProperty(ICON_GENERATION_KEY, null);
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

	private static ImageIcon darkenImageIcon(ImageIcon icon)
	{
		Image img = icon.getImage();
		BufferedImage bi = new BufferedImage(
			img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

		Graphics g = bi.createGraphics();
		g.drawImage(img, 0, 0, null);
		g.dispose();

		RescaleOp op = new RescaleOp(0.8f, 0, null);
		bi = op.filter(bi, null);

		return new ImageIcon(bi);
	}

	private static ImageIcon tintImageIcon(ImageIcon icon, Color tint)
	{
		Image img = icon.getImage();
		int width = img.getWidth(null);
		int height = img.getHeight(null);
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		Graphics g = bi.createGraphics();
		g.drawImage(img, 0, 0, null);
		g.dispose();

		float tr = tint.getRed() / 255f;
		float tg = tint.getGreen() / 255f;
		float tb = tint.getBlue() / 255f;

		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int rgba = bi.getRGB(x, y);
				int a = (rgba >> 24) & 0xFF;
				int r = Math.round(((rgba >> 16) & 0xFF) * tr);
				int g2 = Math.round(((rgba >> 8) & 0xFF) * tg);
				int b = Math.round((rgba & 0xFF) * tb);
				bi.setRGB(x, y, (a << 24) | (r << 16) | (g2 << 8) | b);
			}
		}

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

	public static BufferedImage blendImages(BufferedImage base, BufferedImage peak, float alpha)
	{
		int w = base.getWidth();
		int h = base.getHeight();
		BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = result.createGraphics();
		g.drawImage(base, 0, 0, null);
		g.setComposite(AlphaComposite.SrcOver.derive(alpha));
		g.drawImage(peak, 0, 0, null);
		g.dispose();
		return result;
	}
}
