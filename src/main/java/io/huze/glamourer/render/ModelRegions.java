package io.huze.glamourer.render;

import java.awt.image.BufferedImage;
import javax.annotation.Nonnull;

public final class ModelRegions
{
	public static final int NONE = -1;
	private static final int OUTLINE_ARGB = 0xFFFFFFFF;

	@Nonnull
	private final RasterSize size;
	/// Per raster pixel, the color index plus one in the low half and the texture index plus one in
	/// the high half, so a pixel showing nothing of the glamour is 0.
	@Nonnull
	private final int[] tags;

	ModelRegions(@Nonnull RasterSize size, @Nonnull int[] tags)
	{
		this.size = size;
		this.tags = tags;
	}

	static int tag(short colorIndex, short textureIndex)
	{
		return ((textureIndex + 1) << 16) | ((colorIndex + 1) & 0xFFFF);
	}

	private static int colorIndexOf(int tag)
	{
		return (tag & 0xFFFF) - 1;
	}

	private static int textureIndexOf(int tag)
	{
		return (tag >>> 16) - 1;
	}

	/// The index in the glamour of the color drawn at an image pixel, or [#NONE].
	public int getColorIndexAt(int imageX, int imageY)
	{
		return colorIndexOf(tagAt(imageX, imageY));
	}

	/// The index in the glamour of the texture drawn at an image pixel, or [#NONE].
	public int getTextureIndexAt(int imageX, int imageY)
	{
		return textureIndexOf(tagAt(imageX, imageY));
	}

	private int tagAt(int imageX, int imageY)
	{
		int supersample = size.getSupersample();
		int x = imageX * supersample + supersample / 2;
		int y = imageY * supersample + supersample / 2;
		if (imageX < 0 || imageY < 0 || x >= size.getRasterWidth() || y >= size.getRasterHeight())
		{
			return 0;
		}
		return tags[y * size.getRasterWidth() + x];
	}

	public void outline(@Nonnull RegionSelection selection, @Nonnull BufferedImage target)
	{
		int[] pixels = size.getImagePixels(target);
		boolean[] outside = new boolean[tags.length];
		for (int index = 0; index < outside.length; index++)
		{
			outside[index] = !selection.selects(colorIndexOf(tags[index]), textureIndexOf(tags[index]));
		}
		int[] raster = new int[tags.length];
		Outliner.paintEdge(raster, outside, size, OUTLINE_ARGB);
		size.downscale(raster, pixels);
	}
}
