package io.huze.glamourer.render;

import javax.annotation.Nonnull;
import lombok.Value;

@Value
class ModelBounds
{
	private static final ModelBounds EMPTY = new ModelBounds(0, 0, 0, 1, 0, 0);

	double centerX;
	double centerY;
	double centerZ;
	double radius;
	/// Above the origin. The client centres icons on it.
	int height;
	/// Furthest vertex from the origin. The client offsets face depths by it.
	int reach;

	@Nonnull
	static ModelBounds of(@Nonnull float[] verticesX, @Nonnull float[] verticesY, @Nonnull float[] verticesZ)
	{
		if (verticesX.length == 0)
		{
			return EMPTY;
		}

		double minX, minY, minZ, maxX, maxY, maxZ;
		minX = minY = minZ = Double.MAX_VALUE;
		maxX = maxY = maxZ = -Double.MAX_VALUE;
		int height = 0;
		double reach = 0;
		for (int vertex = 0; vertex < verticesX.length; vertex++)
		{
			minX = Math.min(minX, verticesX[vertex]);
			maxX = Math.max(maxX, verticesX[vertex]);
			minY = Math.min(minY, verticesY[vertex]);
			maxY = Math.max(maxY, verticesY[vertex]);
			minZ = Math.min(minZ, verticesZ[vertex]);
			maxZ = Math.max(maxZ, verticesZ[vertex]);
			height = Math.max(height, (int) -verticesY[vertex]);
			reach = Math.max(reach, verticesX[vertex] * verticesX[vertex]
				+ verticesY[vertex] * verticesY[vertex] + verticesZ[vertex] * verticesZ[vertex]);
		}
		double centerX = (minX + maxX) / 2;
		double centerY = (minY + maxY) / 2;
		double centerZ = (minZ + maxZ) / 2;

		double furthest = 0;
		for (int vertex = 0; vertex < verticesX.length; vertex++)
		{
			double dx = verticesX[vertex] - centerX;
			double dy = verticesY[vertex] - centerY;
			double dz = verticesZ[vertex] - centerZ;
			furthest = Math.max(furthest, dx * dx + dy * dy + dz * dz);
		}
		return new ModelBounds(centerX, centerY, centerZ, Math.max(1, Math.sqrt(furthest)), height,
			(int) Math.ceil(Math.sqrt(reach)));
	}
}
