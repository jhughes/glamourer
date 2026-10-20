package io.huze.glamourer.model;

import io.huze.glamourer.color.Colors;
import io.huze.glamourer.npc.NpcSheet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.ModelData;

public final class NpcModel
{
	@Getter
	@Nonnull
	private final ModelData data;
	@Getter
	@Nonnull
	private final Lighting lighting;
	@Nullable
	private final short[] compositionFind;
	@Nullable
	private final short[] compositionReplace;

	private NpcModel(@Nonnull ModelData data, @Nonnull Lighting lighting,
					 @Nullable short[] compositionFind, @Nullable short[] compositionReplace)
	{
		this.data = data;
		this.lighting = lighting;
		this.compositionFind = compositionFind;
		this.compositionReplace = compositionReplace;
	}

	/// Client thread only. Null while loading, or when the NPC has no models.
	@Nullable
	public static NpcModel load(@Nonnull Client client, @Nonnull NpcSheet npcSheet, int npcId)
	{
		var composition = client.getNpcDefinition(npcId);
		if (composition == null || composition.getModels() == null)
		{
			return null;
		}
		var data = ModelLoader.merge(client, composition.getModels());
		if (data == null)
		{
			return null;
		}

		data = data.cloneColors();
		// cloneTextures throws on a model with no textures.
		if (data.getFaceTextures() != null)
		{
			data = data.cloneTextures();
		}
		var find = composition.getColorToReplace();
		var replace = composition.getColorToReplaceWith();
		if (find != null && replace != null)
		{
			for (int i = 0; i < find.length && i < replace.length; i++)
			{
				data = data.recolor(find[i], replace[i]);
			}
		}

		var offsets = npcSheet.getLighting(npcId);
		return new NpcModel(data, Lighting.forActor(offsets.getAmbient(), offsets.getContrast()), find, replace);
	}

	public short getColorAfterComposition(short color)
	{
		return Colors.applyReplacements(color, compositionFind, compositionReplace);
	}

	@Nonnull
	short[] getColorsAfterComposition(@Nonnull short[] colors)
	{
		short[] after = new short[colors.length];
		for (int i = 0; i < colors.length; i++)
		{
			after[i] = getColorAfterComposition(colors[i]);
		}
		return after;
	}
}
