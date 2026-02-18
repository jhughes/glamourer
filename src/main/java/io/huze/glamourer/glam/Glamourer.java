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
	final IconService iconService;
	final Map<Integer, Glamour> activeGlamourMap;
	private volatile Future<?> cacheResetFuture;

	@Inject
	public Glamourer(Client client, DedupeItemManager ddItemManager, ItemSheet itemSheet, ScheduledExecutorService executor, ClientThread clientThread, IconService iconService)
	{
		this.client = client;
		this.ddItemManager = ddItemManager;
		this.itemSheet = itemSheet;
		this.executor = executor;
		this.clientThread = clientThread;
		this.iconService = iconService;
		activeGlamourMap = new HashMap<>();
	}

	public void onPostItemComposition(PostItemComposition event)
	{
		final ItemComposition itemComp = event.getItemComposition();
		Glamour glamour;
		if ((glamour = activeGlamourMap.get(itemComp.getId())) != null)
		{
			log.debug("Glamouring item {} ({})", itemComp.getMembersName(), itemComp.getId());
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

	private void immediateCacheReset()
	{
		client.getItemModelCache().reset();
		client.getItemSpriteCache().reset();
		Player player = client.getLocalPlayer();
		if (player != null && player.getPlayerComposition() != null)
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
			glam = new Glamour(itemSheet, itemComp, null);
		}
		return glam;
	}

	public Glamour loadGlamour(GlamourData glamourData)
	{
		var key = glamourData.getItemKey();
		var itemComp = ddItemManager.getItemComposition(key);
		return Glamour.load(itemSheet, itemComp, activeGlamourMap.get(itemComp.getId()), glamourData);
	}

	public void apply(Glamour glam) throws GlamourConflictException
	{
		log.debug("Applying glamour on {} ({} items)", glam.getItemName(), glam.getItemIds().size());
		for (int key : glam.getItemIds())
		{
			var existingGlam = activeGlamourMap.get(key);
			if (existingGlam != null && existingGlam != glam)
			{
				log.warn("Glamour conflict on item ID {}", key);
				throw new GlamourConflictException(glam, key);
			}
		}
		for (int key : glam.getItemIds())
		{
			activeGlamourMap.putIfAbsent(key, glam);
		}
		glam.apply();
		scheduleCacheReset();
	}

	public void revert(Glamour glam)
	{
		log.debug("Reverting glamour on {} ({} items)", glam.getItemName(), glam.getItemIds().size());
		for (int key : glam.getItemIds())
		{
			if (activeGlamourMap.containsKey(key))
			{
				glam.revert();
				activeGlamourMap.remove(key);
			} else {
				log.warn("Cannot revert item ID {}: no glamour applied", key);
			}
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
