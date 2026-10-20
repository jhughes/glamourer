package io.huze.glamourer.render;

import javax.annotation.Nonnull;
import lombok.experimental.UtilityClass;

@UtilityClass
final class Outliner
{
	private static final int ICON_OUTLINE_ARGB = 0xFF000000;

	static void drawIconOutline(@Nonnull int[] raster, @Nonnull RasterSize size)
	{
		boolean[] isModel = new boolean[raster.length];
		for (int index = 0; index < isModel.length; index++)
		{
			isModel[index] = (raster[index] >>> 24) != 0;
		}
		paintEdge(raster, isModel, size, ICON_OUTLINE_ARGB);
	}

	static void paintEdge(@Nonnull int[] raster, @Nonnull boolean[] marked, @Nonnull RasterSize size, int argb)
	{
		final int width = size.getRasterWidth();
		final int height = size.getRasterHeight();
		final int reach = size.getSupersample();
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				if (!marked[y * width + x] && isNear(marked, width, height, x, y, reach))
				{
					raster[y * width + x] = argb;
				}
			}
		}
	}

	private static boolean isNear(@Nonnull boolean[] marked, int width, int height, int x, int y, int reach)
	{
		for (int dy = -reach; dy <= reach; dy++)
		{
			int neighbourY = y + dy;
			if (neighbourY < 0 || neighbourY >= height)
			{
				continue;
			}
			for (int dx = -reach; dx <= reach; dx++)
			{
				int neighbourX = x + dx;
				if (neighbourX >= 0 && neighbourX < width && dx * dx + dy * dy <= reach * reach
					&& marked[neighbourY * width + neighbourX])
				{
					return true;
				}
			}
		}
		return false;
	}
}
