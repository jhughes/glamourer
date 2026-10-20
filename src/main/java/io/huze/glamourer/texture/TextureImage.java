package io.huze.glamourer.texture;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TextureImage
{
	@Nonnull
	private final SamplableTexture texture;
	@Nonnull
	private final int[] argb;

	@Nullable
	private BufferedImage frame;
	private long frameCycle;

	TextureImage(@Nonnull SamplableTexture texture)
	{
		this.texture = texture;
		int[] pixels = texture.getPixels();
		this.argb = new int[pixels.length];
		for (int i = 0; i < pixels.length; i++)
		{
			argb[i] = pixels[i] == 0 ? 0 : 0xFF000000 | pixels[i];
		}
	}

	public boolean isAnimated()
	{
		return texture.getAnimation().isAnimated();
	}

	@Nonnull
	public BufferedImage getFrame(long cycle)
	{
		if (frame == null)
		{
			int size = texture.getSize();
			frame = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
			drawFrame(cycle);
		}
		else if (frameCycle != cycle && isAnimated())
		{
			drawFrame(cycle);
		}
		return frame;
	}

	private void drawFrame(long cycle)
	{
		assert frame != null;
		int[] target = ((DataBufferInt) frame.getRaster().getDataBuffer()).getData();
		texture.getAnimation().drawFrame(argb, texture.getSize(), cycle, target);
		frameCycle = cycle;
	}
}
