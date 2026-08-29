package io.huze.glamourer.npc;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

/// Track's the local player's follower and emits {@link FollowerChanged}.
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class FollowerTracker
{
	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;

	/// The world view the follower is in, -1 with no follower.
	@Getter
	private volatile int followerViewId = -1;

	/// The index within the world view; -1 with no follower.
	@Getter
	private volatile int followerIndex = -1;

	/// The follower's NPC id; -1 with no follower.
	@Getter
	private volatile int followerId = -1;

	private boolean refreshQueued;

	/// Waits a tick before reporting a despawn, so a pet that immediately respawns at a different
	/// index posts one event rather than two.
	private boolean changePending;

	public void start()
	{
		clientThread.invoke(this::refresh);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		maybeRefresh(event.getNpc());
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		maybeRefresh(event.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		maybeRefresh(event.getNpc());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			clientThread.invoke(() -> {
				changePending = false;
				if (followerIndex != -1)
				{
					postEvent(-1, -1, -1);
				}
			});
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (changePending)
		{
			clientThread.invoke(() -> {
				changePending = false;
				if (client.getFollower() == null && followerIndex != -1)
				{
					postEvent(-1, -1, -1);
				}
			});
		}
	}

	private void maybeRefresh(NPC npc)
	{
		if (npc.getIndex() == followerIndex || npc == client.getFollower())
		{
			queueRefresh();
		}
	}

	private void queueRefresh()
	{
		if (!refreshQueued)
		{
			refreshQueued = true;
			clientThread.invokeAtTickEnd(() -> {
				refreshQueued = false;
				refresh();
			});
		}
	}

	private void refresh()
	{
		int viewId = -1;
		int index = -1;
		int id = -1;
		NPC follower = client.getFollower();
		if (follower != null)
		{
			viewId = follower.getWorldView().getId();
			index = follower.getIndex();
			id = follower.getId();
		}

		if (index == followerIndex && id == followerId && viewId == followerViewId)
		{
			changePending = false;
			return;
		}
		if (index == -1 && followerIndex != -1)
		{
			changePending = true;
			return;
		}
		changePending = false;
		postEvent(viewId, index, id);
	}

	/// Client thread only.
	private void postEvent(int viewId, int index, int id)
	{
		final boolean moved = followerIndex != index || this.followerViewId != viewId;
		final int oldIndex = moved ? followerIndex : -1;
		final int oldViewId = moved ? this.followerViewId : -1;
		followerIndex = index;
		followerId = id;
		this.followerViewId = viewId;
		log.debug("Follower changed {} {} {} {}", viewId, index, oldViewId, oldIndex);
		eventBus.post(new FollowerChanged(viewId, index, oldViewId, oldIndex));
	}
}
