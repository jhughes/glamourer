package io.huze.glamourer.render;

import java.util.Arrays;
import javax.annotation.Nonnull;

/// Visible faces in painting order, three corner entries per face.
/// Buffers are re-used for each projection.
final class ProjectedModel
{
	static final short UNTEXTURED = ModelSnapshot.UNTEXTURED;
	static final short NO_INDEX = ModelSnapshot.NO_INDEX;
	private static final int CORNERS = 3;
	private static final int FIRST_CAPACITY = 256;

	@Nonnull
	RasterSize rasterSize = new RasterSize(0, 0, 1);
	int faceCount;
	double[] x = new double[0];
	double[] y = new double[0];
	double[] depth = new double[0];
	/// RGB, or the shade on a textured face.
	int[] colors = new int[0];
	double[] us = new double[0];
	double[] vs = new double[0];
	int[] alphas = new int[0];
	byte[] priorities = new byte[0];
	int[] biases = new int[0];
	short[] textures = new short[0];
	short[] colorIndices = new short[0];
	short[] textureIndices = new short[0];

	void reset(@Nonnull RasterSize rasterSize)
	{
		this.rasterSize = rasterSize;
		this.faceCount = 0;
	}

	int addFace(double x1, double y1, double depth1,
				double x2, double y2, double depth2,
				double x3, double y3, double depth3,
				int alpha, int color1, int color2, int color3)
	{
		int face = faceCount++;
		if (face >= alphas.length)
		{
			grow();
		}
		int corner = face * CORNERS;
		x[corner] = x1;
		y[corner] = y1;
		depth[corner] = depth1;
		x[corner + 1] = x2;
		y[corner + 1] = y2;
		depth[corner + 1] = depth2;
		x[corner + 2] = x3;
		y[corner + 2] = y3;
		depth[corner + 2] = depth3;
		colors[corner] = color1;
		colors[corner + 1] = color2;
		colors[corner + 2] = color3;
		alphas[face] = alpha;
		priorities[face] = 0;
		biases[face] = 0;
		textures[face] = UNTEXTURED;
		colorIndices[face] = NO_INDEX;
		textureIndices[face] = NO_INDEX;
		return face;
	}

	void indexFace(int face, short colorIndex, short textureIndex)
	{
		colorIndices[face] = colorIndex;
		textureIndices[face] = textureIndex;
	}

	void prioritizeFace(int face, byte priority)
	{
		priorities[face] = priority;
	}

	void biasFace(int face, int bias)
	{
		biases[face] = bias;
	}

	void textureFace(int face, short texture, double u1, double v1, double u2, double v2, double u3, double v3)
	{
		int corner = face * CORNERS;
		textures[face] = texture;
		us[corner] = u1;
		vs[corner] = v1;
		us[corner + 1] = u2;
		vs[corner + 1] = v2;
		us[corner + 2] = u3;
		vs[corner + 2] = v3;
	}

	/// Twice the signed area. Negative is back facing.
	double getWindingArea(int face)
	{
		int corner = face * CORNERS;
		return getWindingArea(x[corner], y[corner], x[corner + 1], y[corner + 1], x[corner + 2], y[corner + 2]);
	}

	static double getWindingArea(double x1, double y1, double x2, double y2, double x3, double y3)
	{
		return (y1 - y3) * (x2 - x3) + (y2 - y3) * (x3 - x1);
	}

	private void grow()
	{
		int faces = Math.max(FIRST_CAPACITY, alphas.length * 2);
		x = Arrays.copyOf(x, faces * CORNERS);
		y = Arrays.copyOf(y, faces * CORNERS);
		depth = Arrays.copyOf(depth, faces * CORNERS);
		colors = Arrays.copyOf(colors, faces * CORNERS);
		us = Arrays.copyOf(us, faces * CORNERS);
		vs = Arrays.copyOf(vs, faces * CORNERS);
		alphas = Arrays.copyOf(alphas, faces);
		priorities = Arrays.copyOf(priorities, faces);
		biases = Arrays.copyOf(biases, faces);
		textures = Arrays.copyOf(textures, faces);
		colorIndices = Arrays.copyOf(colorIndices, faces);
		textureIndices = Arrays.copyOf(textureIndices, faces);
	}
}
