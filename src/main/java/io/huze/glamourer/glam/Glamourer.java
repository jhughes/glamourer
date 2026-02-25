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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.events.PostItemComposition;
import net.runelite.client.callback.ClientThread;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class Glamourer
{
	private static final int CACHE_REFRESH_DELAY_MS = 30;
	final Client client;
	final DedupeItemManager ddItemManager;
	final ItemSheet itemSheet;
	final ScheduledExecutorService executor;
	final ClientThread clientThread;
	final Map<Integer, Glamour> appliedGlamourMap = new HashMap<>();
	private volatile Future<?> cacheResetFuture;

	public void onPostItemComposition(PostItemComposition event)
	{
		final ItemComposition itemComp = event.getItemComposition();
		Glamour glamour;
		if ((glamour = appliedGlamourMap.get(itemComp.getId())) != null)
		{
			log.debug("Glamouring item {} ({})", itemComp.getMembersName(), itemComp.getId());
			glamour.apply(itemComp);
			scheduleCacheReset();
		}
	}

	private void scheduleCacheReset()
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
		return Glamour.start(itemSheet, itemComp, appliedGlamourMap.get(itemId));
	}

	public Glamour loadGlamour(GlamourData glamourData)
	{
		var key = glamourData.getItemKey();
		var itemComp = ddItemManager.getItemComposition(key);
		return Glamour.load(itemSheet, itemComp, appliedGlamourMap.get(itemComp.getId()), glamourData);
	}

	/**
	 * Apply the specified glamour
	 * @return true if applied; false if hidden (unable to apply due to conflict)
	 */
	public boolean apply(Glamour glam)
	{
		log.debug("Applying glamour on {} ({} items)", glam.getItemName(), glam.getItemIds().size());
		for (int key : glam.getItemIds())
		{
			var existingGlam = appliedGlamourMap.get(key);
			if (existingGlam != null && existingGlam != glam)
			{
				return false;
			}
		}
		for (int key : glam.getItemIds())
		{
			appliedGlamourMap.putIfAbsent(key, glam);
		}
		glam.apply();
		scheduleCacheReset();
		return true;
	}

	public void revert(Glamour glam)
	{
		log.debug("Reverting glamour on {} ({} items)", glam.getItemName(), glam.getItemIds().size());
		for (int key : glam.getItemIds())
		{
			if (appliedGlamourMap.containsKey(key))
			{
				glam.revert();
				appliedGlamourMap.remove(key);
			} else {
				log.warn("Cannot revert item ID {}: no glamour applied", key);
			}
		}
		scheduleCacheReset();
	}

	public void revertAll()
	{
		appliedGlamourMap.values().forEach(Glamour::revert);
		appliedGlamourMap.clear();
		immediateCacheReset();
	}
}
