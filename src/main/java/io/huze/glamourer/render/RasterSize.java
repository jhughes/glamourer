package io.huze.glamourer.render;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import javax.annotation.Nonnull;
import lombok.Value;

@Value
class RasterSize
{
	int imageWidth;
	int imageHeight;
	int supersample;

	int getRasterWidth()
	{
		return imageWidth * supersample;
	}

	int getRasterHeight()
	{
		return imageHeight * supersample;
	}

	double toRaster(double imagePosition)
	{
		return imagePosition * supersample;
	}

	/// Throws unless the image is ARGB at the image size.
	@Nonnull
	int[] getImagePixels(@Nonnull BufferedImage image)
	{
		if (image.getType() != BufferedImage.TYPE_INT_ARGB
			|| image.getWidth() != imageWidth || image.getHeight() != imageHeight)
		{
			throw new IllegalArgumentException("Expected a " + imageWidth + "x" + imageHeight + " ARGB image");
		}
		return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
	}

	void downscale(@Nonnull int[] raster, @Nonnull int[] pixels)
	{
		int rasterWidth = getRasterWidth();
		int samples = supersample * supersample;

		for (int y = 0; y < imageHeight; y++)
		{
			for (int x = 0; x < imageWidth; x++)
			{
				int alphaSum = 0;
				int redSum = 0;
				int greenSum = 0;
				int blueSum = 0;
				for (int sampleY = 0; sampleY < supersample; sampleY++)
				{
					int row = (y * supersample + sampleY) * rasterWidth + x * supersample;
					for (int sampleX = 0; sampleX < supersample; sampleX++)
					{
						int pixel = raster[row + sampleX];
						int alpha = pixel >>> 24;
						alphaSum += alpha;
						redSum += ((pixel >> 16) & 0xFF) * alpha;
						greenSum += ((pixel >> 8) & 0xFF) * alpha;
						blueSum += (pixel & 0xFF) * alpha;
					}
				}
				if (alphaSum == 0)
				{
					pixels[y * imageWidth + x] = 0;
					continue;
				}
				pixels[y * imageWidth + x] = ((alphaSum / samples) << 24)
					| ((redSum / alphaSum) << 16)
					| ((greenSum / alphaSum) << 8)
					| (blueSum / alphaSum);
			}
		}
	}
}
