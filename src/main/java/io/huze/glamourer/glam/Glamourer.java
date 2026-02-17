package io.huze.glamourer.glam;

import io.huze.glamourer.item.DedupeItemManager;
import io.huze.glamourer.item.ItemSheet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.events.PostItemComposition;
import net.runelite.client.callback.ClientThread;

@Slf4j
@Singleton
public class Glamourer
{
	private static final int CACHE_REFRESH_DELAY_MS = 30;
	final Client client;
	final DedupeItemManager ddItemManager;
	final ItemSheet itemSheet;
	final ScheduledExecutorService executor;
	final ClientThread clientThread;
	final Map<Integer, Glamour> activeGlamourMap;
	private volatile Future<?> cacheResetFuture;

	@Inject
	public Glamourer(Client client, DedupeItemManager ddItemManager, ItemSheet itemSheet, ScheduledExecutorService executor, ClientThread clientThread)
	{
		this.client = client;
		this.ddItemManager = ddItemManager;
		this.itemSheet = itemSheet;
		this.executor = executor;
		this.clientThread = clientThread;
		activeGlamourMap = new HashMap<>();
	}

	public void onPostItemComposition(PostItemComposition event)
	{
		final ItemComposition itemComp = event.getItemComposition();
		Glamour glamour;
		if ((glamour = activeGlamourMap.get(itemComp.getId())) != null)
		{
			log.debug("Applying glamour to item {} ({})", itemComp.getId(), itemComp.getMembersName());
			glamour.apply(itemComp);
			scheduleCacheReset();
		}
	}

	public void scheduleCacheReset()
	{
		if (cacheResetFuture == null)
		{
			cacheResetFuture = executor.schedule(() -> clientThread.invokeLater(this::immediateCacheReset), CACHE_REFRESH_DELAY_MS, TimeUnit.MILLISECONDS);
		}
	}

	public boolean isCacheResetPending()
	{
		return cacheResetFuture != null;
	}

	private void immediateCacheReset()
	{
		client.getItemModelCache().reset();
		client.getItemSpriteCache().reset();
		Player player = client.getLocalPlayer();
		if (player != null)
		{
			player.getPlayerComposition().setHash();
		}
		cacheResetFuture = null;
	}

	public Glamour startGlamour(int itemId)
	{
		var itemComp = ddItemManager.getItemComposition(itemId);
		var glam = activeGlamourMap.get(itemId);
		if (glam == null)
		{
			glam = new Glamour(itemSheet, itemComp);
		}
		return glam;
	}

	public Glamour loadGlamour(GlamourData glamourData)
	{
		var key = glamourData.getItemKey();
		var itemComp = ddItemManager.getItemComposition(key);
		if (activeGlamourMap.containsKey(itemComp.getId())) {
			throw new IllegalStateException("Item " + key + " already has a glamour applied");
		}
		return Glamour.load(itemSheet, itemComp, glamourData, client, clientThread, this::isCacheResetPending);
	}

	public void apply(Glamour glam)
	{
		for (int key : glam.getItemIds())
		{
			var existingGlam = activeGlamourMap.get(key);
			if (existingGlam != null && existingGlam != glam)
			{
				throw new IllegalStateException("Item " + key + " already has a glamour applied: " + existingGlam.getItemName());
			}
		}
		log.debug("Applying glamour to {} ({} items)", glam.getItemName(), glam.getItemIds().size());
		// Schedule cache reset first so pending flag is set before image loading checks it
		scheduleCacheReset();
		glam.apply(client, clientThread, this::isCacheResetPending);
		for (int key : glam.getItemIds())
		{
			activeGlamourMap.putIfAbsent(key, glam);
		}
	}

	public void revert(Glamour glam)
	{
		log.debug("Reverting glamour {} {}", glam.getItemName(), glam.getItemIds());
		for (int key : glam.getItemIds())
		{
			if (!activeGlamourMap.containsKey(key))
			{
				throw new IllegalStateException("Cannot revert item " + key + " (" + glam.getItemName() + "): no glamour applied");
			}
		}
		glam.revert();
		for (int key : glam.getItemIds())
		{
			activeGlamourMap.remove(key);
		}
		scheduleCacheReset();
	}

	public void revertAll()
	{
		activeGlamourMap.values().forEach(Glamour::revert);
		activeGlamourMap.clear();
		immediateCacheReset();
	}
}
