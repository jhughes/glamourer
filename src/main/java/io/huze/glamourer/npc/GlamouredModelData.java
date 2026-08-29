package io.huze.glamourer.npc;

import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.color.Colors;
import javax.annotation.Nullable;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;

final class GlamouredModelData
{
	/// Constants match RuneLite's NPC lighting before per-NPC Lighting adjustments.
	private static final int NPC_AMBIENT = 64;
	private static final int NPC_CONTRAST = 850;
	private static final int LIGHT_X = -30;
	private static final int LIGHT_Y = -50;
	private static final int LIGHT_Z = -30;

	private final ModelData data;
	private final short[] originalColors;
	@Nullable
	private final short[] originalTextures;
	@Getter
	private final int[][] originalLitColors;
	@Nullable
	private final short[] compositionFind;
	@Nullable
	private final short[] compositionReplace;
	private final int ambient;
	private final int contrast;

	private GlamouredModelData(ModelData data, int[][] originalLitColors,
		@Nullable short[] compositionFind, @Nullable short[] compositionReplace, int ambient, int contrast)
	{
		this.data = data;
		this.originalColors = data.getFaceColors().clone();
		this.originalTextures = data.getFaceTextures() != null ? data.getFaceTextures().clone() : null;
		this.originalLitColors = originalLitColors;
		this.compositionFind = compositionFind;
		this.compositionReplace = compositionReplace;
		this.ambient = ambient;
		this.contrast = contrast;
	}

	@Nullable
	static GlamouredModelData load(Client client, NpcSheet npcSheet, int npcId)
	{
		NPCComposition composition = client.getNpcDefinition(npcId);
		if (composition == null)
		{
			return null;
		}
		int[] modelIds = composition.getModels();
		if (modelIds == null || modelIds.length == 0)
		{
			return null;
		}

		ModelData[] parts = new ModelData[modelIds.length];
		for (int i = 0; i < modelIds.length; i++)
		{
			ModelData part = client.loadModelData(modelIds[i]);
			if (part == null)
			{
				return null;
			}
			parts[i] = part;
		}

		// Don't merge a solo part because it re-derives normals which causes minor lighting differences.
		ModelData merged = parts.length == 1 ? parts[0].shallowCopy() : client.mergeModels(parts);
		if (merged == null)
		{
			return null;
		}

		merged.cloneColors()
			.cloneVertices()
			.cloneTransparencies();
		// cloneTextures throws on a model with no textures.
		if (merged.getFaceTextures() != null)
		{
			merged.cloneTextures();
		}

		short[] find = composition.getColorToReplace();
		short[] replace = composition.getColorToReplaceWith();
		applyCompositionRecolors(find, replace, merged);

		NpcSheet.Lighting lighting = npcSheet.getLighting(npcId);
		final int ambient = NPC_AMBIENT + lighting.getAmbient();
		final int contrast = NPC_CONTRAST + lighting.getContrast();
		Model originalLit = merged.light(ambient, contrast, LIGHT_X, LIGHT_Y, LIGHT_Z);
		if (originalLit == null)
		{
			return null;
		}
		return new GlamouredModelData(merged, getFaceColorsOf(originalLit), find, replace, ambient, contrast);
	}

	void applyGlamour(NpcGlamour glamour)
	{
		System.arraycopy(originalColors, 0, data.getFaceColors(), 0, originalColors.length);
		short[] textures = data.getFaceTextures();
		if (textures != null && originalTextures != null)
		{
			System.arraycopy(originalTextures, 0, textures, 0, originalTextures.length);
		}

		short[] from = new short[countChanged(glamour)];
		short[] to = new short[from.length];
		int next = 0;
		for (ColorReplacement replacement : glamour.getColors())
		{
			if (replacement.hasChanged())
			{
				from[next] = getOverriddenColor(getModelColor(replacement));
				to[next] = replacement.getReplacement();
				next++;
			}
		}
		Colors.breakColorChains(from, to);
		for (int i = 0; i < from.length; i++)
		{
			data.recolor(from[i], to[i]);
		}

		glamour.applyTextures(textures);
	}

	@Nullable
	Model light()
	{
		return data.light(ambient, contrast, LIGHT_X, LIGHT_Y, LIGHT_Z);
	}

	@Nullable
	Model copyPose(Model posed)
	{
		final int count = data.getVerticesCount();
		System.arraycopy(posed.getVerticesX(), 0, data.getVerticesX(), 0, count);
		System.arraycopy(posed.getVerticesY(), 0, data.getVerticesY(), 0, count);
		System.arraycopy(posed.getVerticesZ(), 0, data.getVerticesZ(), 0, count);

		byte[] src = posed.getFaceTransparencies();
		byte[] dst = data.getFaceTransparencies();
		if (src != null && dst != null)
		{
			System.arraycopy(src, 0, dst, 0, Math.min(src.length, dst.length));
		}
		return light();
	}

	int getVerticesCount()
	{
		return data.getVerticesCount();
	}

	@Nullable
	short[] getFaceTextures()
	{
		return data.getFaceTextures();
	}

	@Nullable
	short[] getOriginalTextures()
	{
		return originalTextures;
	}

	static int[][] getFaceColorsOf(Model model)
	{
		return new int[][]{model.getFaceColors1(), model.getFaceColors2(), model.getFaceColors3()};
	}

	private static void applyCompositionRecolors(@Nullable short[] find, @Nullable short[] replace, ModelData merged)
	{
		if (find == null || replace == null)
		{
			return;
		}
		for (int i = 0; i < find.length && i < replace.length; i++)
		{
			merged.recolor(find[i], replace[i]);
		}
	}

	private static int countChanged(NpcGlamour glamour)
	{
		int changed = 0;
		for (ColorReplacement replacement : glamour.getColors())
		{
			if (replacement.hasChanged())
			{
				changed++;
			}
		}
		return changed;
	}

	private static short getModelColor(ColorReplacement replacement)
	{
		Short model = replacement.getModel();
		return model != null ? model : replacement.getOriginal();
	}

	private short getOverriddenColor(short color)
	{
		if (compositionFind == null || compositionReplace == null)
		{
			return color;
		}
		for (int i = 0; i < compositionFind.length && i < compositionReplace.length; i++)
		{
			if (compositionFind[i] == color)
			{
				return compositionReplace[i];
			}
		}
		return color;
	}
}
