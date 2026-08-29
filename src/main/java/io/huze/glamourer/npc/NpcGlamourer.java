package io.huze.glamourer.npc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.WorldView;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class NpcGlamourer
{
	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;
	private final KindDirector kindDirector;
	private final InstanceDirector instanceDirector;

	private final Map<Integer, NpcGlamour> kindGlamours = new HashMap<>();

	@Setter(AccessLevel.PACKAGE)
	private Map<NpcInstanceKey, NpcGlamour> instanceGlamours = Map.of();
	@Nullable
	private Highlight highlight;

	/// In a highlight, matches every copy of the kinds rather than one NPC in one view.
	private static final int ANY_INDEX = -1;
	private static final int ANY_VIEW = -1;

	private static final class Highlight
	{
		final NpcGlamour glamour;
		final Set<Integer> npcIds;
		final int viewId;
		final int npcIndex;

		Highlight(NpcGlamour glamour, Set<Integer> npcIds, int viewId, int npcIndex)
		{
			this.glamour = glamour;
			this.npcIds = npcIds;
			this.viewId = viewId;
			this.npcIndex = npcIndex;
		}

		boolean matches(int viewId, int npcIndex, int npcId)
		{
			if (this.npcIndex != ANY_INDEX && (npcIndex != this.npcIndex || this.viewId != viewId))
			{
				return false;
			}
			return npcIds.contains(npcId);
		}
	}

	private boolean running;

	public void startUp()
	{
		if (running)
		{
			return;
		}
		running = true;
		eventBus.register(this);
		instanceDirector.startUp();
	}

	public void shutDown()
	{
		if (!running)
		{
			return;
		}
		running = false;
		eventBus.unregister(this);
		instanceDirector.shutDown();
		clientThread.invoke(() -> {
			kindGlamours.clear();
			instanceGlamours = Map.of();
			highlight = null;
			destroyAll();
		});
	}

	void setKindGlamour(int npcId, NpcGlamour glamour)
	{
		if (glamour.isEmpty())
		{
			clearKindGlamour(npcId);
			return;
		}
		if (glamour.equals(kindGlamours.get(npcId)))
		{
			return;
		}
		kindGlamours.put(npcId, glamour);
		invalidatePatches();
	}

	private void clearKindGlamour(int npcId)
	{
		if (kindGlamours.remove(npcId) != null)
		{
			invalidatePatches();
		}
	}

	void setHighlight(Collection<Integer> npcIds, int viewId, int npcIndex, NpcGlamour glamour)
	{
		highlight = new Highlight(glamour, Set.copyOf(npcIds), viewId, npcIndex);
	}

	void setHighlight(Collection<Integer> npcIds, NpcGlamour glamour)
	{
		setHighlight(npcIds, ANY_VIEW, ANY_INDEX, glamour);
	}

	void clearHighlight()
	{
		highlight = null;
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		instanceDirector.beginFrame();
		kindDirector.beginFrame();
		{
			final Highlight highlight = this.highlight;
			final boolean idle = kindGlamours.isEmpty() && instanceGlamours.isEmpty() && highlight == null;
			if (idle || client.getGameState() != GameState.LOGGED_IN)
			{
				instanceDirector.hideAllInstances();
				kindDirector.restoreAllOriginals();
				return;
			}
			glamourNpcsInView(client.getTopLevelWorldView(), highlight);
		}
		instanceDirector.endFrame();
		kindDirector.endFrame();
	}

	private void glamourNpcsInView(WorldView worldView, @Nullable Highlight highlight)
	{
		final int viewId = worldView.getId();
		for (NPC npc : worldView.npcs())
		{
			if (npc == null)
			{
				continue;
			}
			final int npcId = npc.getId();

			NpcGlamour glamour = highlight != null && highlight.matches(viewId, npc.getIndex(), npcId)
				? highlight.glamour
				: null;

			boolean isInstanceGlamour = true;
			if (glamour == null && !instanceGlamours.isEmpty())
			{
				glamour = instanceGlamours.get(new NpcInstanceKey(viewId, npc.getIndex(), npcId));
			}
			if (glamour == null)
			{
				isInstanceGlamour = false;
				glamour = kindGlamours.get(npcId);
			}
			if (glamour == null)
			{
				continue;
			}

			if (isInstanceGlamour)
			{
				instanceDirector.drawInstanceFor(npc, glamour);
			}
			else
			{
				kindDirector.applyKindGlamour(npc, glamour);
			}
		}

		for (WorldView nested : worldView.worldViews())
		{
			if (nested != null)
			{
				glamourNpcsInView(nested, highlight);
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOADING:
			case HOPPING:
			case CONNECTION_LOST:
			case LOGIN_SCREEN:
				destroyAll();
				break;
			default:
				break;
		}
	}

	private void destroyAll()
	{
		kindDirector.restoreAllOriginals();
		instanceDirector.destroyAllInstances();
	}

	private void invalidatePatches()
	{
		clientThread.invoke(kindDirector::invalidateGlamours);
	}
}
