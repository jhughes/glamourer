package io.huze.glamourer.npc;

import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.color.Colors;
import io.huze.glamourer.model.NpcModel;
import javax.annotation.Nullable;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;

final class GlamouredModelData
{
	private final NpcModel npcModel;
	private final ModelData data;
	private final short[] originalColors;
	@Nullable
	private final short[] originalTextures;
	@Getter
	private final int[][] originalLitColors;

	private GlamouredModelData(NpcModel npcModel, int[][] originalLitColors)
	{
		this.npcModel = npcModel;
		this.data = npcModel.getData();
		this.originalColors = data.getFaceColors().clone();
		this.originalTextures = data.getFaceTextures() != null ? data.getFaceTextures().clone() : null;
		this.originalLitColors = originalLitColors;
	}

	@Nullable
	static GlamouredModelData load(Client client, NpcSheet npcSheet, int npcId)
	{
		NpcModel npcModel = NpcModel.load(client, npcSheet, npcId);
		if (npcModel == null)
		{
			return null;
		}

		ModelData data = npcModel.getData();
		data.cloneVertices()
			.cloneTransparencies();

		Model originalLit = npcModel.getLighting().light(data);
		if (originalLit == null)
		{
			return null;
		}
		return new GlamouredModelData(npcModel, getFaceColorsOf(originalLit));
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
				from[next] = npcModel.getColorAfterComposition(replacement.getModel());
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
		return npcModel.getLighting().light(data);
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
}
