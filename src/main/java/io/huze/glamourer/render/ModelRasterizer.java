/*
 * The triangle fill in this file is derived from Creator's Kit's model viewer
 * (com.creatorskit.swing.renderer.RenderUtilities).
 *
 * Copyright (c) 2023, ScreteMonge
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.huze.glamourer.render;

import io.huze.glamourer.color.Shade;
import io.huze.glamourer.texture.SamplableTextureProvider;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.annotation.Nonnull;

/// Creator's Kit's triangle fill with a depth buffer per render priority, as in the GPU plugin.
/// Translucent faces skip the depth write.
/// Buffers are re-used for each render.
final class ModelRasterizer
{
	private static final int OPAQUE = 0xFF000000;
	private static final int OPAQUE_ALPHA = 255;
	private static final int HOLE = 0;
	private static final int[] NO_PIXELS = {};
	private static final float[] NO_DEPTHS = {};
	private static final byte[] NO_PRIORITIES = {};
	private static final int[] NO_TAGS = {};
	private static final float EMPTY_INVERSE_DEPTH = 0;
	/// The GPU plugin depth tests 100/depth + bias/128.
	private static final double BIAS_UNITS_PER_INVERSE_DEPTH = 100 * 128;

	@Nonnull
	private final SamplableTextureProvider textureProvider;
	private int[] raster = NO_PIXELS;
	/// 1/depth, so nearer is larger.
	private float[] inverseDepths = NO_DEPTHS;
	private byte[] priorities = NO_PRIORITIES;
	private int[] regionTags = NO_TAGS;

	ModelRasterizer(@Nonnull SamplableTextureProvider textureProvider)
	{
		this.textureProvider = textureProvider;
	}

	@Nonnull
	ModelRegions draw(@Nonnull ProjectedModel projected, long cycle, @Nonnull BufferedImage target)
	{
		int pixelCount = draw(projected, cycle, target, false);
		return new ModelRegions(projected.rasterSize, Arrays.copyOf(regionTags, pixelCount));
	}

	void drawOutlined(@Nonnull ProjectedModel projected, long cycle, @Nonnull BufferedImage target)
	{
		draw(projected, cycle, target, true);
	}

	private int draw(@Nonnull ProjectedModel projected, long cycle, @Nonnull BufferedImage target, boolean outlined)
	{
		final RasterSize size = projected.rasterSize;
		int[] pixels = size.getImagePixels(target);
		int[] raster = size.getSupersample() == 1 ? pixels : getRaster(size.getRasterWidth() * size.getRasterHeight());
		int pixelCount = rasterize(projected, cycle, raster);
		if (outlined)
		{
			Outliner.drawIconOutline(raster, size);
		}
		if (raster != pixels)
		{
			size.downscale(raster, pixels);
		}
		return pixelCount;
	}

	private int rasterize(@Nonnull ProjectedModel projected, long cycle, @Nonnull int[] pixels)
	{
		final RasterSize size = projected.rasterSize;
		final int pixelCount = size.getRasterWidth() * size.getRasterHeight();
		Arrays.fill(pixels, 0, pixelCount, 0);
		if (inverseDepths.length < pixelCount)
		{
			inverseDepths = new float[pixelCount];
			priorities = new byte[pixelCount];
			regionTags = new int[pixelCount];
		}
		Arrays.fill(inverseDepths, 0, pixelCount, EMPTY_INVERSE_DEPTH);
		Arrays.fill(priorities, 0, pixelCount, (byte) 0);
		Arrays.fill(regionTags, 0, pixelCount, 0);
		for (int face = 0; face < projected.faceCount; face++)
		{
			fill(projected, face, cycle, pixels, inverseDepths, priorities, regionTags);
		}
		return pixelCount;
	}

	@Nonnull
	private int[] getRaster(int pixelCount)
	{
		if (raster.length < pixelCount)
		{
			raster = new int[pixelCount];
		}
		return raster;
	}

	private void fill(@Nonnull ProjectedModel model, int face, long cycle, @Nonnull int[] pixels,
					  @Nonnull float[] depths, @Nonnull byte[] priorities, @Nonnull int[] tags)
	{
		double area = model.getWindingArea(face);
		if (!(area > 0))
		{
			return;
		}
		int corner = face * 3;
		double x1 = model.x[corner];
		double y1 = model.y[corner];
		double x2 = model.x[corner + 1];
		double y2 = model.y[corner + 1];
		double x3 = model.x[corner + 2];
		double y3 = model.y[corner + 2];
		int color1 = model.colors[corner];
		int color2 = model.colors[corner + 1];
		int color3 = model.colors[corner + 2];

		int width = model.rasterSize.getRasterWidth();
		int height = model.rasterSize.getRasterHeight();
		int minX = (int) Math.max(0, Math.ceil(Math.min(x1, Math.min(x2, x3))));
		int maxX = (int) Math.min(width - 1, Math.floor(Math.max(x1, Math.max(x2, x3))));
		int minY = (int) Math.max(0, Math.ceil(Math.min(y1, Math.min(y2, y3))));
		int maxY = (int) Math.min(height - 1, Math.floor(Math.max(y1, Math.max(y2, y3))));

		double step1 = (y3 - y2) / area;
		double step2 = (y1 - y3) / area;
		double step3 = (y2 - y1) / area;
		int alpha = model.alphas[face];
		boolean blend = alpha != OPAQUE_ALPHA;
		byte priority = model.priorities[face];
		int tag = ModelRegions.tag(model.colorIndices[face], model.textureIndices[face]);
		// Bias affects only the depth test.
		double nearness = 1 + model.biases[face] / BIAS_UNITS_PER_INVERSE_DEPTH;

		// Perspective correct: 1/depth and uv/depth interpolate linearly.
		double inverseDepth1 = 1 / model.depth[corner];
		double inverseDepth2 = 1 / model.depth[corner + 1];
		double inverseDepth3 = 1 / model.depth[corner + 2];
		var texture = model.textures[face] == ProjectedModel.UNTEXTURED ? null : textureProvider.get(model.textures[face]);
		double columnOverDepth1 = 0;
		double columnOverDepth2 = 0;
		double columnOverDepth3 = 0;
		double rowOverDepth1 = 0;
		double rowOverDepth2 = 0;
		double rowOverDepth3 = 0;
		if (texture != null)
		{
			int size = texture.getSize();
			columnOverDepth1 = model.us[corner] * size * inverseDepth1;
			columnOverDepth2 = model.us[corner + 1] * size * inverseDepth2;
			columnOverDepth3 = model.us[corner + 2] * size * inverseDepth3;
			rowOverDepth1 = model.vs[corner] * size * inverseDepth1;
			rowOverDepth2 = model.vs[corner + 1] * size * inverseDepth2;
			rowOverDepth3 = model.vs[corner + 2] * size * inverseDepth3;
		}

		for (int y = minY; y <= maxY; y++)
		{
			double weight1 = ((y - y3) * (x2 - x3) + (y2 - y3) * (x3 - minX)) / area;
			double weight2 = ((y - y1) * (x3 - x1) + (y3 - y1) * (x1 - minX)) / area;
			double weight3 = ((y - y2) * (x1 - x2) + (y1 - y2) * (x2 - minX)) / area;
			boolean entered = false;
			for (int x = minX; x <= maxX; x++, weight1 += step1, weight2 += step2, weight3 += step3)
			{
				if (weight1 < 0 || weight2 < 0 || weight3 < 0)
				{
					if (entered)
					{
						break;
					}
					continue;
				}
				entered = true;

				int index = y * width + x;
				double inverseDepth = weight1 * inverseDepth1 + weight2 * inverseDepth2 + weight3 * inverseDepth3;
				float biasedInverseDepth = (float) (inverseDepth * nearness);
				// Ties go to the later face
				if (priorities[index] == priority && biasedInverseDepth < depths[index])
				{
					continue;
				}

				int rgb;
				if (texture == null)
				{
					rgb = blendColors(color1, color2, color3, weight1, weight2, weight3);
				}
				else
				{
					double depthScale = 1 / inverseDepth;
					int column = (int) Math.floor(
						(weight1 * columnOverDepth1 + weight2 * columnOverDepth2 + weight3 * columnOverDepth3) * depthScale);
					int row = (int) Math.floor(
						(weight1 * rowOverDepth1 + weight2 * rowOverDepth2 + weight3 * rowOverDepth3) * depthScale);
					int texel = texture.sample(column, row, cycle);
					if (texel == HOLE)
					{
						continue;
					}
					rgb = shadeTexel(texel, (int) (color1 * weight1 + color2 * weight2 + color3 * weight3));
				}
				tags[index] = tag;
				if (blend)
				{
					pixels[index] = blend(rgb, alpha, pixels[index]);
				}
				else
				{
					pixels[index] = OPAQUE | rgb;
					depths[index] = biasedInverseDepth;
					priorities[index] = priority;
				}
			}
		}
	}

	private static int blendColors(int rgb1, int rgb2, int rgb3, double weight1, double weight2, double weight3)
	{
		int red = (int) (((rgb1 >> 16) & 0xFF) * weight1 + ((rgb2 >> 16) & 0xFF) * weight2 + ((rgb3 >> 16) & 0xFF) * weight3);
		int green = (int) (((rgb1 >> 8) & 0xFF) * weight1 + ((rgb2 >> 8) & 0xFF) * weight2 + ((rgb3 >> 8) & 0xFF) * weight3);
		int blue = (int) ((rgb1 & 0xFF) * weight1 + (rgb2 & 0xFF) * weight2 + (rgb3 & 0xFF) * weight3);
		int rgb = (red << 16) | (green << 8) | blue;
		return rgb == HOLE ? 1 : rgb;
	}

	private static int shadeTexel(int texel, int shade)
	{
		int red = Shade.scale((texel >> 16) & 0xFF, shade);
		int green = Shade.scale((texel >> 8) & 0xFF, shade);
		int blue = Shade.scale(texel & 0xFF, shade);
		int shaded = (red << 16) | (green << 8) | blue;
		return shaded == HOLE ? 1 : shaded;
	}

	private static int blend(int sourceRgb, int sourceAlpha, int destinationArgb)
	{
		int destinationAlpha = (destinationArgb >>> 24) * (255 - sourceAlpha) / 255;
		int alpha = sourceAlpha + destinationAlpha;
		if (alpha == 0)
		{
			return 0;
		}
		int red = (((sourceRgb >> 16) & 0xFF) * sourceAlpha + ((destinationArgb >> 16) & 0xFF) * destinationAlpha) / alpha;
		int green = (((sourceRgb >> 8) & 0xFF) * sourceAlpha + ((destinationArgb >> 8) & 0xFF) * destinationAlpha) / alpha;
		int blue = ((sourceRgb & 0xFF) * sourceAlpha + (destinationArgb & 0xFF) * destinationAlpha) / alpha;
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}
}
