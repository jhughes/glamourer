package io.huze.glamourer.render;

import javax.annotation.Nonnull;

final class ModelProjector
{
	private static final int NEAR_CLIP = 50;

	@Nonnull
	private final ProjectedModel projected = new ProjectedModel();
	@Nonnull
	private double[] rasterX = new double[0];
	@Nonnull
	private double[] rasterY = new double[0];
	@Nonnull
	private double[] depth = new double[0];

	@Nonnull
	private int[] visibleFaces = new int[0];
	@Nonnull
	private int[] visibleDepths = new int[0];
	@Nonnull
	private byte[] visiblePriorities = new byte[0];

	@Nonnull
	ProjectedModel project(@Nonnull ModelSnapshot snapshot, @Nonnull ModelCamera camera,
						   @Nonnull RasterSize rasterSize)
	{
		projectVertices(snapshot, camera, rasterSize);
		int visibleCount = cullFaces(snapshot, camera, rasterSize);
		int[] order = FaceOrder.order(visibleDepths, snapshot.faceRenderPriorities == null ? null : visiblePriorities, visibleCount);

		projected.reset(rasterSize);
		for (int visible : order)
		{
			int face = visibleFaces[visible];
			int corner1 = snapshot.faceIndices1[face];
			int corner2 = snapshot.faceIndices2[face];
			int corner3 = snapshot.faceIndices3[face];
			int added = projected.addFace(
				rasterX[corner1], rasterY[corner1], depth[corner1],
				rasterX[corner2], rasterY[corner2], depth[corner2],
				rasterX[corner3], rasterY[corner3], depth[corner3],
				snapshot.getFaceAlpha(face),
				snapshot.faceColors1[face], snapshot.faceColors2[face], snapshot.faceColors3[face]);
			byte priority = snapshot.getFaceRenderPriority(face);
			if (priority != 0)
			{
				projected.prioritizeFace(added, priority);
			}
			int bias = snapshot.getFaceBias(face);
			if (bias != 0)
			{
				projected.biasFace(added, bias);
			}
			short texture = snapshot.getFaceTexture(face);
			if (texture != ModelSnapshot.UNTEXTURED)
			{
				projected.textureFace(added, texture,
					snapshot.faceU1[face], snapshot.faceV1[face],
					snapshot.faceU2[face], snapshot.faceV2[face],
					snapshot.faceU3[face], snapshot.faceV3[face]);
			}
			short colorIndex = snapshot.getFaceColorIndex(face);
			short textureIndex = snapshot.getFaceTextureIndex(face);
			if (colorIndex != ModelSnapshot.NO_INDEX || textureIndex != ModelSnapshot.NO_INDEX)
			{
				projected.indexFace(added, colorIndex, textureIndex);
			}
		}
		return projected;
	}

	private void projectVertices(@Nonnull ModelSnapshot snapshot, @Nonnull ModelCamera camera,
								 @Nonnull RasterSize rasterSize)
	{
		final int vertexCount = snapshot.getVertexCount();
		if (depth.length < vertexCount)
		{
			rasterX = new double[vertexCount];
			rasterY = new double[vertexCount];
			depth = new double[vertexCount];
		}
		for (int vertex = 0; vertex < vertexCount; vertex++)
		{
			double x = snapshot.verticesX[vertex];
			double y = snapshot.verticesY[vertex];
			double z = snapshot.verticesZ[vertex];
			depth[vertex] = camera.toDepth(x, y, z);
			rasterX[vertex] = rasterSize.toRaster(camera.toImageX(camera.toCameraX(x, y, z), depth[vertex]));
			rasterY[vertex] = rasterSize.toRaster(camera.toImageY(camera.toCameraY(x, y, z), depth[vertex]));
		}
	}

	private int cullFaces(@Nonnull ModelSnapshot snapshot, @Nonnull ModelCamera camera, @Nonnull RasterSize rasterSize)
	{
		final int faceCount = snapshot.getFaceCount();
		if (visibleFaces.length < faceCount)
		{
			visibleFaces = new int[faceCount];
			visibleDepths = new int[faceCount];
			visiblePriorities = new byte[faceCount];
		}
		final int rasterWidth = rasterSize.getRasterWidth();
		final int rasterHeight = rasterSize.getRasterHeight();
		final int originDepth = (int) camera.toDepth(0, 0, 0);
		final int reach = snapshot.getBounds().getReach();

		int visible = 0;
		for (int face = 0; face < faceCount; face++)
		{
			int corner1 = snapshot.faceIndices1[face];
			int corner2 = snapshot.faceIndices2[face];
			int corner3 = snapshot.faceIndices3[face];
			if (depth[corner1] < NEAR_CLIP || depth[corner2] < NEAR_CLIP || depth[corner3] < NEAR_CLIP)
			{
				continue;
			}

			double x1 = rasterX[corner1];
			double y1 = rasterY[corner1];
			double x2 = rasterX[corner2];
			double y2 = rasterY[corner2];
			double x3 = rasterX[corner3];
			double y3 = rasterY[corner3];
			if (!(ProjectedModel.getWindingArea(x1, y1, x2, y2, x3, y3) > 0)
				|| isOffRaster(x1, x2, x3, rasterWidth)
				|| isOffRaster(y1, y2, y3, rasterHeight))
			{
				continue;
			}

			visibleFaces[visible] = face;
			visibleDepths[visible] = reach
				+ ((int) depth[corner1] - originDepth + (int) depth[corner2] - originDepth + (int) depth[corner3] - originDepth) / 3;
			visiblePriorities[visible] = snapshot.faceRenderPriorities == null ? 0 : snapshot.faceRenderPriorities[face];
			visible++;
		}
		return visible;
	}

	private static boolean isOffRaster(double a, double b, double c, int rasterExtent)
	{
		return (a < 0 && b < 0 && c < 0) || (a >= rasterExtent && b >= rasterExtent && c >= rasterExtent);
	}
}
