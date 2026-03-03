package io.huze.glamourer.glam;

import io.huze.glamourer.item.DedupeItemManager;
import io.huze.glamourer.item.ItemSheet;
import io.huze.glamourer.plate.DisplayStyle;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
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
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.SpritePixels;
import net.runelite.api.events.PlayerChanged;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PostItemComposition;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class GlamourEngine
{
	private static final int IMAGE_BATCH_DELAY_MS = 1;
	private static final int RECONCILE_DELAY_MS = 1;

	private final Client client;
	private final ClientThread clientThread;
	private final DedupeItemManager ddItemManager;
	private final ItemSheet itemSheet;
	private final ScheduledExecutorService executor;

	// --- Glamour state ---
	/// What should be applied — written on main thread, read on client thread during reconcile.
	/// All access to staged maps must hold stageLock.
	private final Object stageLock = new Object();
	private final Map<Integer, Glamour> stagedGlamourMap = new HashMap<>();
	private final Map<Integer, Glamour> stagedDefaultEquipMap = new HashMap<>();
	/// What is actually applied — only touched on the client thread.
	private final Map<Integer, Glamour> appliedGlamourMap = new HashMap<>();
	private final Map<Integer, Glamour> appliedDefaultEquipMap = new HashMap<>();
	private final Map<Player, Set<KitType>> activePlayerOverrides = new HashMap<>();
	private volatile Future<?> reconcileFuture;
	private volatile boolean batchMode;

	// --- Icon state ---
	/// Stores the ItemID -> Glamours for all pending icon creations in the batch.
	/// Only one icon for an ItemID can be created at a time because creation uses a shared ItemComposition.
	private final ConcurrentHashMap<Integer, IconPending> pendingIconBatch = new ConcurrentHashMap<>();
	private volatile Future<?> createIconBatchFuture;

	private void resetItemCaches()
	{
		client.getItemModelCache().reset();
		client.getItemSpriteCache().reset();
	}

	/* ==================== Glamour operations ==================== */

	@Subscribe(priority = Float.MAX_VALUE)
	public void onPostItemComposition(PostItemComposition event)
	{
		final ItemComposition itemComp = event.getItemComposition();
		Glamour glamour;
		if ((glamour = appliedGlamourMap.get(itemComp.getId())) != null)
		{
			glamour.apply(itemComp);
		}
	}

	@Subscribe
	public void onPlayerChanged(PlayerChanged event)
	{
		var player = event.getPlayer();
		var overrides = reconcileEquipmentOverrides(player);
		activePlayerOverrides.put(player, overrides);
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		activePlayerOverrides.remove(event.getPlayer());
	}

	/**
	 * Get glamour overrides for player.
	 */
	private Map<Integer, Glamour> getOverrides(@Nonnull Player player)
	{
		// TODO this is where sync should load per-player overrides.
		if (player == client.getLocalPlayer())
		{
			return appliedGlamourMap;
		}
		return appliedDefaultEquipMap;
	}

	/**
	 * Reconcile player equipment overrides. Client thread only.
	 */
	private Set<KitType> reconcileEquipmentOverrides(@Nonnull Player player)
	{
		var oldKit = activePlayerOverrides.getOrDefault(player, EnumSet.noneOf(KitType.class));
		var activeKit = EnumSet.noneOf(KitType.class);
		var overrides = getOverrides(player);
		var comp = player.getPlayerComposition();
		var equipmentIds = comp.getEquipmentIds();
		for (int kitIdx = 0; kitIdx < equipmentIds.length; kitIdx++)
		{
			// Skip non item equipment
			KitType kit = KitType.values()[kitIdx];
			int equipmentId = equipmentIds[kitIdx];
			if (equipmentId < PlayerComposition.ITEM_OFFSET)
			{
				// Players unequipping items automatically removes their override in that slot, so no cleanup necessary.
				continue;
			}
			int itemId = equipmentId - PlayerComposition.ITEM_OFFSET;

			// Apply override if one exists
			var override = overrides.get(itemId);
			if (override != null)
			{
				override.applyReplacement(comp.createColorTextureOverride(kit, itemId));
				activeKit.add(kit);
				continue;
			}

			// Apply original if item is glamoured
			var applied = appliedGlamourMap.get(itemId);
			if (applied != null)
			{
				applied.applyOriginal(comp.createColorTextureOverride(kit, itemId));
				activeKit.add(kit);
				continue;
			}

			// Remove old override if kit is no longer glamoured
			if (oldKit.contains(kit))
			{
				comp.removeColorTextureOverride(kit);
			}
		}
		comp.setHash();
		return activeKit;
	}

	/**
	 * Backfill player state from missed onPlayerChanged events.
	 * Only necessary if plugin startUp happens after login.
	 * Main thread only.
	 */
	public void backfillPlayerState()
	{
		clientThread.invokeLater(() -> {
			if (client.getGameState().getState() < GameState.LOGGED_IN.getState())
			{
				return false;
			}
			var worldView = client.getTopLevelWorldView();
			for (var player : worldView.players())
			{
				onPlayerChanged(new PlayerChanged(player));
			}
			return true;
		});
	}


	/**
	 * Revert any active glamour on the item, run the supplier on the clean item composition, then re-apply.
	 * Client thread only.
	 */
	private <T> T runOnCleanItemComp(int itemId, Supplier<T> supplier)
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

	/**
	 * Start glamour for item ID. Client thread only.
	 */
	Glamour startGlamour(int itemId)
	{
		var itemComp = ddItemManager.getItemComposition(itemId);
		return runOnCleanItemComp(itemComp.getId(), () -> Glamour.start(itemSheet, itemComp));
	}

	/**
	 * Start glamour for item ID. Client thread only.
	 */
	Glamour loadGlamour(GlamourData glamourData)
	{
		var itemComp = ddItemManager.getItemComposition(glamourData.getItemKey());
		return runOnCleanItemComp(itemComp.getId(), () -> Glamour.load(itemSheet, itemComp, glamourData));
	}

	/**
	 * Stage apply glamour. Callable from any thread.
	 */
	void stageApply(Glamour glam, DisplayStyle displayStyle)
	{
		boolean changed = glam.isDirty();
		synchronized (stageLock)
		{
			for (int key : glam.getItemIds())
			{
				if (displayStyle == DisplayStyle.GLOBAL)
				{
					changed |= stagedDefaultEquipMap.putIfAbsent(key, glam) == null;
				}
				changed |= stagedGlamourMap.putIfAbsent(key, glam) == null;
			}
		}
		if (changed)
		{
			scheduleReconcile();
		}
	}

	private void clearAllStaged()
	{
		synchronized (stageLock)
		{
			stagedGlamourMap.clear();
			stagedDefaultEquipMap.clear();
		}
	}

	/**
	 * Check the staged visibility of a glamour. Callable from any thread.
	 */
	GlamourVisibility getStagedVisibility(Glamour glam)
	{
		for (int key : glam.getItemIds())
		{
			if (stagedGlamourMap.get(key) == glam)
			{
				return GlamourVisibility.VISIBLE;
			}
		}
		for (int key : glam.getItemIds())
		{
			if (stagedDefaultEquipMap.get(key) == glam)
			{
				return GlamourVisibility.OTHERS;
			}
		}
		return GlamourVisibility.HIDDEN;
	}

	/**
	 * Clear all staged glamours, run the action to re-stage, then schedule a single reconcile.
	 */
	void batch(Runnable action)
	{
		batchMode = true;
		clearAllStaged();
		action.run();
		batchMode = false;
		scheduleReconcile();
	}

	private void scheduleReconcile()
	{
		if (batchMode)
		{
			return;
		}
		Future<?> existing = reconcileFuture;
		if (existing == null || existing.isDone())
		{
			reconcileFuture = executor.schedule(
				() -> clientThread.invokeLater(this::reconcile),
				RECONCILE_DELAY_MS, TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * Reconcile any differences between staged and applied glamours.
	 */
	private void reconcile()
	{
		Map<Integer, Glamour> staged;
		Map<Integer, Glamour> stagedEquip;
		synchronized (stageLock)
		{
			staged = new HashMap<>(stagedGlamourMap);
			stagedEquip = new HashMap<>(stagedDefaultEquipMap);
		}
		var currentlyApplied = new HashSet<>(appliedGlamourMap.values());
		var shouldBeApplied = new HashSet<>(staged.values());

		boolean itemsChanged = false;
		for (Glamour glam : currentlyApplied)
		{
			if (!shouldBeApplied.contains(glam))
			{
				glam.revert();
				itemsChanged = true;
			}
		}
		for (Glamour glam : shouldBeApplied)
		{
			if (!currentlyApplied.contains(glam) || glam.clearDirty())
			{
				glam.apply();
				itemsChanged = true;
			}
		}

		if (itemsChanged)
		{
			appliedGlamourMap.clear();
			appliedGlamourMap.putAll(staged);
			resetItemCaches();
		}

		var equipChanged = !stagedEquip.equals(appliedDefaultEquipMap);
		if (itemsChanged || equipChanged)
		{
			appliedDefaultEquipMap.clear();
			appliedDefaultEquipMap.putAll(stagedEquip);

			for (var entry : activePlayerOverrides.entrySet())
			{
				var player = entry.getKey();
				if (player.getPlayerComposition() == null)
				{
					continue;
				}
				var existingKit = entry.getValue();
				var newKit = reconcileEquipmentOverrides(player);
				existingKit.clear();
				existingKit.addAll(newKit);
			}
		}
	}

	/**
	 * Revert all applied glamours for shutdown.
	 * Main thread only.
	 */
	void revertAll()
	{
		clientThread.invokeLater(() -> {
			for (var entry : activePlayerOverrides.entrySet())
			{
				var player = entry.getKey();
				var kitOverrides = entry.getValue();
				var comp = player.getPlayerComposition();
				if (comp != null)
				{
					kitOverrides.forEach(comp::removeColorTextureOverride);
					comp.setHash();
				}
			}
			activePlayerOverrides.clear();
			clearAllStaged();
			appliedGlamourMap.values().forEach(Glamour::revert);
			appliedGlamourMap.clear();
			appliedDefaultEquipMap.clear();
			resetItemCaches();
		});
	}

	/* ==================== Icon operations ==================== */

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
