package io.huze.glamourer.npc;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.runelite.api.Model;

final class GlamouredKind
{
	private final GlamouredModelData modelData;
	private int generation;
	private int frameCounter;
	@Nullable
	private int[][] glamouredLitFaces;
	@Nullable
	private int[][] modelFaces;
	@Nullable
	private int[][] lastWrittenFaces;
	@Nullable
	private short[] appliedTextures;

	GlamouredKind(GlamouredModelData modelData)
	{
		this.modelData = modelData;
	}

	boolean isPatchedInFrame(int frameCounter)
	{
		return this.frameCounter == frameCounter;
	}

	void setLastPatchedFrame(int frameCounter)
	{
		this.frameCounter = frameCounter;
	}

	boolean needsGlamourForGeneration(int generation)
	{
		return glamouredLitFaces == null || this.generation != generation;
	}

	boolean updateGlamour(NpcGlamour glamour, int generation)
	{
		modelData.applyGlamour(glamour);
		Model lit = modelData.light();
		if (lit == null)
		{
			return false;
		}
		glamouredLitFaces = GlamouredModelData.getFaceColorsOf(lit);
		this.generation = generation;
		return true;
	}

	void apply(Model live)
	{
		int[][] target = GlamouredModelData.getFaceColorsOf(live);
		if (this.modelFaces != null && this.modelFaces[0] == target[0] && lastWrittenFaces == glamouredLitFaces)
		{
			return;
		}
		final int[][] original = modelData.getOriginalLitColors();
		final int n = original[0].length;
		for (int i = 1; i < target.length; i++)
		{
			if (target[i].length < n)
			{
				return;
			}
		}
		if (!startsWith(target[0], original[0])
			&& (lastWrittenFaces == null || !startsWith(target[0], lastWrittenFaces[0])))
		{
			return;
		}
		for (int i = 0; i < target.length; i++)
		{
			System.arraycopy(glamouredLitFaces[i], 0, target[i], 0, n);
		}
		this.modelFaces = target;
		lastWrittenFaces = glamouredLitFaces;
		applyTextures(live);
	}

	private void applyTextures(Model live)
	{
		short[] target = live.getFaceTextures();
		short[] glamoured = modelData.getFaceTextures();
		if (target == null || glamoured == null)
		{
			return;
		}
		System.arraycopy(glamoured, 0, target, 0, Math.min(glamoured.length, target.length));
		appliedTextures = target;
	}

	boolean revertIfNotPatched(int frame)
	{
		if (frameCounter == frame)
		{
			return false;
		}
		revert();
		return true;
	}

	void revert()
	{
		if (modelFaces != null && lastWrittenFaces != null && startsWith(modelFaces[0], lastWrittenFaces[0]))
		{
			int[][] original = modelData.getOriginalLitColors();
			for (int i = 0; i < original.length; i++)
			{
				System.arraycopy(original[i], 0, modelFaces[i], 0, original[i].length);
			}
			short[] originalTextures = modelData.getOriginalTextures();
			if (appliedTextures != null && originalTextures != null)
			{
				System.arraycopy(originalTextures, 0, appliedTextures, 0,
					Math.min(originalTextures.length, appliedTextures.length));
			}
		}
		modelFaces = null;
		lastWrittenFaces = null;
		appliedTextures = null;
	}

	private static boolean startsWith(int[] live, int[] expected)
	{
		return live.length >= expected.length
			&& Arrays.equals(live, 0, expected.length, expected, 0, expected.length);
	}
}
