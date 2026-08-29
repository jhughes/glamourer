package io.huze.glamourer.npc;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;

/**
 * Patches the client's lit model in place without copying colors.
 * This is the same technique that CG recolour plugin uses to modify NPC colors without using RLOs.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
class KindDirector
{
	private final Client client;
	private final NpcSheet npcSheet;

	/// Holds only the kinds patched this frame; {@link #endFrame} drops the rest.
	private final Map<Integer, GlamouredKind> kinds = new HashMap<>();
	private int frameCounter;
	/// Bumped when any kind glamour changes, so existing kinds reglamour lazily.
	private int generation;

	void beginFrame()
	{
		frameCounter++;
	}

	void applyKindGlamour(NPC npc, NpcGlamour glamour)
	{
		final int npcId = npc.getId();
		GlamouredKind kind = kinds.get(npcId);
		if (kind != null && kind.isPatchedInFrame(frameCounter))
		{
			return;
		}
		if (kind == null)
		{
			var modelData = GlamouredModelData.load(client, npcSheet, npcId);
			if (modelData == null)
			{
				return;
			}
			kind = new GlamouredKind(modelData);
			kinds.put(npcId, kind);
		}
		kind.setLastPatchedFrame(frameCounter);

		Model live = npc.getModel();
		if (live == null)
		{
			return;
		}
		if (kind.needsGlamourForGeneration(generation) && !kind.updateGlamour(glamour, generation))
		{
			return;
		}
		kind.apply(live);
	}

	void endFrame()
	{
		kinds.values().removeIf(kind -> kind.revertIfNotPatched(frameCounter));
	}

	void restoreAllOriginals()
	{
		for (GlamouredKind kind : kinds.values())
		{
			kind.revert();
		}
		kinds.clear();
	}

	void invalidateGlamours()
	{
		++generation;
	}
}
