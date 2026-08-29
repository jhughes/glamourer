package io.huze.glamourer.npc;

import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.texture.TextureReplacement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Immutable NPC glamour
 */
final class NpcGlamour
{
	/// The client's marker for a face carrying no texture.
	private static final short NO_TEXTURE = -1;

	private static final NpcGlamour EMPTY = new NpcGlamour(List.of(), List.of());

	private final List<ColorReplacement> colors;
	private final List<TextureReplacement> textures;

	/// For simple equality comparison
	private final int[] packed;

	private NpcGlamour(List<ColorReplacement> colors, List<TextureReplacement> textures)
	{
		this.colors = colors;
		this.textures = textures;
		packed = new int[1 + colors.size() + textures.size()];
		packed[0] = colors.size();
		int next = 1;
		for (ColorReplacement color : colors)
		{
			packed[next++] = pack(color.getOriginal(), color.getReplacement());
		}
		for (TextureReplacement texture : textures)
		{
			packed[next++] = pack(texture.getOriginal(), texture.getReplacement());
		}
	}

	static NpcGlamour empty()
	{
		return EMPTY;
	}

	static NpcGlamour of(@Nullable List<ColorReplacement> colors, @Nullable List<TextureReplacement> textures)
	{
		if (isNullOrEmpty(colors) && isNullOrEmpty(textures))
		{
			return EMPTY;
		}
		return new NpcGlamour(copyColors(colors), copyTextures(textures));
	}

	private static List<ColorReplacement> copyColors(@Nullable List<ColorReplacement> colors)
	{
		if (isNullOrEmpty(colors))
		{
			return List.of();
		}
		List<ColorReplacement> copies = new ArrayList<>(colors.size());
		for (ColorReplacement color : colors)
		{
			ColorReplacement copy = new ColorReplacement(color.getOriginal(), color.getReplacement());
			copy.setModel(color.getModel());
			copies.add(copy);
		}
		return copies;
	}

	private static List<TextureReplacement> copyTextures(@Nullable List<TextureReplacement> textures)
	{
		if (isNullOrEmpty(textures))
		{
			return List.of();
		}
		List<TextureReplacement> copies = new ArrayList<>(textures.size());
		for (TextureReplacement texture : textures)
		{
			copies.add(new TextureReplacement(texture.getOriginal(), texture.getReplacement()));
		}
		return copies;
	}

	private static boolean isNullOrEmpty(@Nullable List<?> list)
	{
		return list == null || list.isEmpty();
	}

	boolean isEmpty()
	{
		return colors.isEmpty() && textures.isEmpty();
	}

	List<ColorReplacement> getColors()
	{
		return colors;
	}

	void applyTextures(@Nullable short[] faceTextures)
	{
		if (faceTextures == null || textures.isEmpty())
		{
			return;
		}
		for (int face = 0; face < faceTextures.length; face++)
		{
			final short current = faceTextures[face];
			if (current == NO_TEXTURE)
			{
				continue;
			}
			for (TextureReplacement texture : textures)
			{
				if (texture.hasChanged() && texture.getOriginal() == current
					&& texture.getReplacement() != NO_TEXTURE)
				{
					faceTextures[face] = texture.getReplacement();
					break;
				}
			}
		}
	}

	@Override
	public boolean equals(Object other)
	{
		return other instanceof NpcGlamour && Arrays.equals(packed, ((NpcGlamour) other).packed);
	}

	@Override
	public int hashCode()
	{
		return Arrays.hashCode(packed);
	}

	private static int pack(short original, short replacement)
	{
		return (original << 16) | (replacement & 0xFFFF);
	}
}
