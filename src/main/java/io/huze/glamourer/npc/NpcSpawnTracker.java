package io.huze.glamourer.npc;

import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.NPC;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
class NpcSpawnTracker
{
	private final EventBus eventBus;
	private final Map<NPC, Integer> spawnOrder = new IdentityHashMap<>();
	private int spawnCounter;

	void startUp()
	{
		eventBus.register(this);
	}

	void shutDown()
	{
		eventBus.unregister(this);
		spawnOrder.clear();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		spawnOrder.put(event.getNpc(), ++spawnCounter);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		spawnOrder.remove(event.getNpc());
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
				spawnOrder.clear();
				spawnCounter = 0;
				break;
			default:
				break;
		}
	}

	/// When this NPC appeared, or -1 if unknown. Lower is older.
	public int getSpawnOrder(NPC npc)
	{
		return spawnOrder.getOrDefault(npc, -1);
	}
}
