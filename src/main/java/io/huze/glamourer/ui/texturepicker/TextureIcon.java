package io.huze.glamourer.ui.texturepicker;

import io.huze.glamourer.texture.TextureAnimation;
import io.huze.glamourer.texture.TextureImage;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.Icon;

final class TextureIcon implements Icon
{
	private final int size;
	@Nullable
	private final TextureImage texture;

	@Nullable
	private BufferedImage scaled;
	private long scaledCycle;

	TextureIcon(int size, @Nullable TextureImage texture)
	{
		this.size = size;
		this.texture = texture;
	}

	boolean isAnimated()
	{
		return texture != null && texture.isAnimated();
	}

	@Override
	public void paintIcon(Component component, Graphics g, int x, int y)
	{
		paintScaled(g, x, y, size, size);
	}

	void paintScaled(@Nonnull Graphics g, int x, int y, int width, int height)
	{
		if (texture == null)
		{
			return;
		}
		long cycle = TextureAnimation.currentCycle();
		int target = Math.max(width, height);
		boolean newFrame = texture.isAnimated() && cycle != scaledCycle;
		if (scaled == null || scaled.getWidth() != target || newFrame)
		{
			scaled = scaleSmooth(texture.getFrame(cycle), target);
			scaledCycle = cycle;
		}
		g.drawImage(scaled, x, y, width, height, null);
	}

	@Override
	public int getIconWidth()
	{
		return size;
	}

	@Override
	public int getIconHeight()
	{
		return size;
	}

	/// Halves bilinearly until one step from the target, so every texel is averaged in.
	@Nonnull
	private static BufferedImage scaleSmooth(@Nonnull BufferedImage source, int target)
	{
		BufferedImage current = source;
		int currentSize = source.getWidth();
		while (currentSize != target)
		{
			int nextSize = Math.max(currentSize / 2, target);
			var next = new BufferedImage(nextSize, nextSize, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = next.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, nextSize < currentSize
				? RenderingHints.VALUE_INTERPOLATION_BILINEAR
				: RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(current, 0, 0, nextSize, nextSize, null);
			g.dispose();
			current = next;
			currentSize = nextSize;
		}
		return current;
	}
}
