package io.huze.glamourer.render;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Getter;

/// A lit model snapshot from the client.
/// Can be drawn on any thread.
public final class ModelSnapshot
{
	public static final short UNTEXTURED = -1;
	/// The index of a face that draws from none of the glamour's colors or textures.
	public static final short NO_INDEX = -1;
	private static final int OPAQUE = 255;

	final float[] verticesX;
	final float[] verticesY;
	final float[] verticesZ;

	final int[] faceIndices1;
	final int[] faceIndices2;
	final int[] faceIndices3;
	/// Each vertex RGB or shade for textures.
	final int[] faceColors1;
	final int[] faceColors2;
	final int[] faceColors3;
	/// 0 for opaque up to 254, or null for a model with no transparent faces.
	@Nullable
	final byte[] faceTransparencies;
	/// Each face's texture or [#UNTEXTURED], or null for a model with no textured faces.
	@Nullable
	final short[] faceTextures;
	/// Each corner's place in the face's texture, as a share of its width and of its height. Null with faceTextures.
	@Nullable
	final float[] faceU1;
	@Nullable
	final float[] faceU2;
	@Nullable
	final float[] faceU3;
	@Nullable
	final float[] faceV1;
	@Nullable
	final float[] faceV2;
	@Nullable
	final float[] faceV3;
	/// The client's render priority of each face, or null for a model painted by depth alone.
	@Nullable
	final byte[] faceRenderPriorities;
	/// How far the client's GPU renderers nudge each face towards the camera, unsigned, so detail
	/// lying on a surface wins the depth test against it. Null for a model with no biased faces.
	@Nullable
	final byte[] faceBias;
	/// Which of a glamour's colors and textures each face draws from, by index, or null for a model
	/// read without a glamour.
	@Nullable
	final short[] faceColorIndices;
	@Nullable
	final short[] faceTextureIndices;
	/// Whether any face draws with a texture that scrolls, so a viewer knows to keep redrawing.
	@Getter
	private final boolean animated;

	@Getter
	@Nonnull
	private final ModelBounds bounds;

	@Builder
	private ModelSnapshot(@Nonnull float[] verticesX, @Nonnull float[] verticesY, @Nonnull float[] verticesZ,
						  @Nonnull int[] faceIndices1, @Nonnull int[] faceIndices2, @Nonnull int[] faceIndices3,
						  @Nonnull int[] faceColors1, @Nonnull int[] faceColors2, @Nonnull int[] faceColors3,
						  @Nullable byte[] faceTransparencies, @Nullable short[] faceTextures,
						  @Nullable float[] faceU1, @Nullable float[] faceU2, @Nullable float[] faceU3,
						  @Nullable float[] faceV1, @Nullable float[] faceV2, @Nullable float[] faceV3,
						  @Nullable byte[] faceRenderPriorities, @Nullable byte[] faceBias,
						  @Nullable short[] faceColorIndices, @Nullable short[] faceTextureIndices, boolean animated)
	{
		this.verticesX = verticesX;
		this.verticesY = verticesY;
		this.verticesZ = verticesZ;
		this.faceIndices1 = faceIndices1;
		this.faceIndices2 = faceIndices2;
		this.faceIndices3 = faceIndices3;
		this.faceColors1 = faceColors1;
		this.faceColors2 = faceColors2;
		this.faceColors3 = faceColors3;
		this.faceTransparencies = faceTransparencies;
		this.faceTextures = faceTextures;
		this.faceU1 = faceU1;
		this.faceU2 = faceU2;
		this.faceU3 = faceU3;
		this.faceV1 = faceV1;
		this.faceV2 = faceV2;
		this.faceV3 = faceV3;
		this.faceRenderPriorities = faceRenderPriorities;
		this.faceBias = faceBias;
		this.faceColorIndices = faceColorIndices;
		this.faceTextureIndices = faceTextureIndices;
		this.animated = animated;
		this.bounds = ModelBounds.of(verticesX, verticesY, verticesZ);
	}

	int getVertexCount()
	{
		return verticesX.length;
	}

	int getFaceCount()
	{
		return faceIndices1.length;
	}

	short getFaceTexture(int face)
	{
		return faceTextures == null ? UNTEXTURED : faceTextures[face];
	}

	int getFaceAlpha(int face)
	{
		return faceTransparencies == null ? OPAQUE : OPAQUE - (faceTransparencies[face] & 0xFF);
	}

	int getFaceBias(int face)
	{
		return faceBias == null ? 0 : faceBias[face] & 0xFF;
	}

	byte getFaceRenderPriority(int face)
	{
		return faceRenderPriorities == null ? 0 : faceRenderPriorities[face];
	}

	short getFaceColorIndex(int face)
	{
		return faceColorIndices == null ? NO_INDEX : faceColorIndices[face];
	}

	short getFaceTextureIndex(int face)
	{
		return faceTextureIndices == null ? NO_INDEX : faceTextureIndices[face];
	}
}
