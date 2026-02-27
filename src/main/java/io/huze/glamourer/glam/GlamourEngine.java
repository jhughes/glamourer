package io.huze.glamourer.glam;

import io.huze.glamourer.item.DedupeItemManager;
import io.huze.glamourer.item.ItemSheet;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.SpritePixels;
import net.runelite.api.events.PostItemComposition;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class GlamourEngine
{
	private static final int CACHE_REFRESH_DELAY_MS = 1;
	private static final int IMAGE_BATCH_DELAY_MS = 1;

	final Client client;
	final ClientThread clientThread;
	final DedupeItemManager ddItemManager;
	final ItemSheet itemSheet;
	final ScheduledExecutorService executor;
	final EventBus eventBus;

	@Inject
	@SuppressWarnings("unused")
	void register()
	{
		eventBus.register(this);
	}

	private static final int RECONCILE_DELAY_MS = 1;

	// --- Glamour state ---
	/// What should be applied — written on main thread, read on client thread during reconcile.
	private final ConcurrentHashMap<Integer, Glamour> stagedGlamourMap = new ConcurrentHashMap<>();
	/// What is actually applied to ItemCompositions — only touched on the client thread.
	private final Map<Integer, Glamour> appliedGlamourMap = new HashMap<>();
	private volatile Future<?> cacheResetFuture;
	private volatile Future<?> reconcileFuture;

	// --- Icon state ---
	/// Stores the ItemID -> Glamours for all pending icon creations in the batch.
	/// Only one icon for an ItemID can be created at a time because creation uses a shared ItemComposition.
	private final ConcurrentHashMap<Integer, IconPending> pendingIconBatch = new ConcurrentHashMap<>();
	private volatile Future<?> createIconBatchFuture;

	// ==================== Glamour operations ====================

	@Subscribe(priority = Float.MAX_VALUE)
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
		resetItemCaches();
		immediatePlayerModelCacheReset();
		cacheResetFuture = null;
	}

	private void immediatePlayerModelCacheReset()
	{
		Player player = client.getLocalPlayer();
		if (player != null && player.getPlayerComposition() != null)
		{
			var comp = player.getPlayerComposition();
			var equipmentIds = comp.getEquipmentIds();
			for (int kitIdx = 0; kitIdx < equipmentIds.length; kitIdx++)
			{
				int equipmentId = equipmentIds[kitIdx];
				if (equipmentId < PlayerComposition.ITEM_OFFSET)
				{
					continue;
				}

				int itemId = equipmentId - PlayerComposition.ITEM_OFFSET;
				KitType kit = KitType.values()[kitIdx];
				comp.createColorTextureOverride(kit, itemId);
			}
			player.getPlayerComposition().setHash();
		}
	}

	/// Revert any active glamour on the item, run the supplier on the clean item composition, then re-apply.
	private <T> T runOnPureItemComp(int itemId, Supplier<T> supplier)
	{
		var appliedGlam = appliedGlamourMap.get(itemId);
		if (appliedGlam == null)
		{
			return supplier.get();
		}
		appliedGlam.revert();
		try
		{
			return supplier.get();
		}
		finally
		{
			appliedGlam.apply();
		}
	}

	public Glamour startGlamour(int itemId)
	{
		var itemComp = ddItemManager.getItemComposition(itemId);
		return runOnPureItemComp(itemComp.getId(), () -> Glamour.start(itemSheet, itemComp));
	}

	public Glamour loadGlamour(GlamourData glamourData)
	{
		var itemComp = ddItemManager.getItemComposition(glamourData.getItemKey());
		return runOnPureItemComp(itemComp.getId(), () -> Glamour.load(itemSheet, itemComp, glamourData));
	}

	/**
	 * Stage apply glamour. Callable from any thread.
	 * @return true if staged; false if hidden (another glamour already claims at least one item ID)
	 */
	boolean stageApply(Glamour glam)
	{
		log.debug("Apply glamour on {} ({} items)", glam.getItemName(), glam.getItemIds().size());
		for (int key : glam.getItemIds())
		{
			var existing = stagedGlamourMap.get(key);
			if (existing != null && existing != glam)
			{
				return false;
			}
		}
		for (int key : glam.getItemIds())
		{
			stagedGlamourMap.putIfAbsent(key, glam);
		}
		scheduleReconcile();
		return true;
	}

	/**
	 * Stage revert glamour (should no longer be applied). Callable from any thread.
	 */
	void stageRevert(Glamour glam)
	{
		boolean revertedAny = false;
		for (int key : glam.getItemIds())
		{
			revertedAny |= stagedGlamourMap.remove(key, glam);
		}
		if (revertedAny)
		{
			log.debug("Reverted glamour on {} ({} items)", glam.getItemName(), glam.getItemIds().size());
			scheduleReconcile();
		}
	}

	/**
	 * Immediately revert all applied glamours and clear both maps. For shutdown/profile change.
	 */
	void revertAll()
	{
		clientThread.invokeLater(() -> {
			stagedGlamourMap.clear();
			appliedGlamourMap.values().forEach(Glamour::revert);
			appliedGlamourMap.clear();
			immediateCacheReset();
		});
	}

	private void scheduleReconcile()
	{
		Future<?> existing = reconcileFuture;
		if (existing == null || existing.isDone())
		{
			reconcileFuture = executor.schedule(
				() -> clientThread.invokeLater(this::reconcile),
				RECONCILE_DELAY_MS, TimeUnit.MILLISECONDS);
		}
	}

	private void reconcile()
	{
		Map<Integer, Glamour> staged = new HashMap<>(stagedGlamourMap);
		Set<Glamour> currentlyApplied = new HashSet<>(appliedGlamourMap.values());
		Set<Glamour> shouldBeApplied = new HashSet<>(staged.values());

		boolean changed = false;
		for (Glamour glam : currentlyApplied)
		{
			if (!shouldBeApplied.contains(glam))
			{
				glam.revert();
				changed = true;
			}
		}
		for (Glamour glam : shouldBeApplied)
		{
			if (!currentlyApplied.contains(glam))
			{
				glam.apply();
				changed = true;
			}
		}

		appliedGlamourMap.clear();
		appliedGlamourMap.putAll(staged);
		if (changed)
		{
			immediateCacheReset();
		}
	}

	// ==================== Icon operations ====================

	/**
	 * Returns an AsyncBufferedImage that will populate with the icon at the next available opportunity.
	 */
	@Nonnull
	public AsyncBufferedImage getIcon(Glamour glamour)
	{
		AsyncBufferedImage img = new AsyncBufferedImage(
			clientThread, Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		IconPending IconPending = new IconPending(img, glamour.getCurrentState());
		executor.execute(() -> queueIconCreation(glamour.getPrimaryItemId(), IconPending));
		return img;
	}

	private void queueIconCreation(final int itemId, final IconPending IconPending)
	{
		// Retry with a small delay while another icon is being created for the same item ID
		var entry = pendingIconBatch.putIfAbsent(itemId, IconPending);
		if (entry != null)
		{
			executor.schedule(() -> queueIconCreation(itemId, IconPending), IMAGE_BATCH_DELAY_MS, TimeUnit.MILLISECONDS);
			return;
		}

		scheduleIconBatch();
	}

	private void scheduleIconBatch()
	{
		if (createIconBatchFuture == null)
		{
			createIconBatchFuture = executor.schedule(
				() -> clientThread.invokeLater(this::executeIconBatch),
				IMAGE_BATCH_DELAY_MS, TimeUnit.MILLISECONDS);
		}
	}

	private void executeIconBatch()
	{
		resetItemCaches();
		for (var entry : pendingIconBatch.entrySet())
		{
			final var itemId = entry.getKey();
			final var iconState = entry.getValue().state;
			final var image = entry.getValue().image;
			final var itemComp = ddItemManager.getItemDefinition(itemId);
			final var originalState = GlamState.backup(itemComp);
			iconState.applyTo(itemComp);
			createSprite(itemId, image);
			originalState.applyTo(itemComp);

			image.loaded();
		}
		resetItemCaches();
		pendingIconBatch.clear();
		createIconBatchFuture = null;
	}

	private void resetItemCaches()
	{
		client.getItemModelCache().reset();
		client.getItemSpriteCache().reset();
	}

	private void createSprite(int itemId, @Nonnull BufferedImage target)
	{
		var spritePixels = client.createItemSprite(
			itemId,
			10000,
			1,
			SpritePixels.DEFAULT_SHADOW_COLOR,
			ItemQuantityMode.NEVER,
			false,
			Constants.CLIENT_DEFAULT_ZOOM
		);
		if (spritePixels != null)
		{
			spritePixels.toBufferedImage(target);
		}
	}
}
