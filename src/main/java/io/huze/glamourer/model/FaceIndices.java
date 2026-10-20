package io.huze.glamourer.model;

import javax.annotation.Nonnull;
import net.runelite.api.ModelData;

final class FaceIndices
{
	static final short NONE = -1;

	@Nonnull
	final short[] colors;
	@Nonnull
	final short[] textures;

	private FaceIndices(@Nonnull short[] colors, @Nonnull short[] textures)
	{
		this.colors = colors;
		this.textures = textures;
	}

	@Nonnull
	static FaceIndices of(@Nonnull ModelData modelData, @Nonnull short[] colorFind, @Nonnull short[] textureFind)
	{
		final int faceCount = modelData.getFaceCount();
		short[] faceColors = modelData.getFaceColors();
		short[] faceTextures = modelData.getFaceTextures();
		short[] colors = new short[faceCount];
		short[] textures = new short[faceCount];
		for (int face = 0; face < faceCount; face++)
		{
			colors[face] = indexOf(colorFind, faceColors[face]);
			textures[face] = faceTextures == null ? NONE : indexOf(textureFind, faceTextures[face]);
		}
		return new FaceIndices(colors, textures);
	}

	private static short indexOf(@Nonnull short[] values, short value)
	{
		for (short index = 0; index < values.length; index++)
		{
			if (values[index] == value)
			{
				return index;
			}
		}
		return NONE;
	}
}
