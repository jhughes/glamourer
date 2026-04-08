package io.huze.glamourer.glam;

import io.huze.glamourer.Extensions;
import io.huze.glamourer.item.DedupeItemComposition;
import io.huze.glamourer.item.DedupeItemManager;
import io.huze.glamourer.item.ItemSheet;
import io.huze.glamourer.plate.DisplayStyle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.experimental.ExtensionMethod;
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
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.PostItemComposition;
import net.runelite.api.events.WorldChanged;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
@ExtensionMethod({Extensions.class})
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
	private final Map<Integer, PrimedItem> primedItemMap = new HashMap<>();
	/// What should be applied — written on main thread, read on client thread during reconcile.
	/// All access to staged maps must hold stageLock.
	private final Object stageLock = new Object();
	private final Map<Integer, Glamour> stagedGlamourMap = new HashMap<>();
	private final Map<Integer, Glamour> stagedDefaultEquipMap = new HashMap<>();
	/// What is actually applied — only touched on the client thread.
	private final Map<Integer, Glamour> appliedGlamourMap = new HashMap<>();
	private final Map<Integer, Glamour> appliedDefaultEquipMap = new HashMap<>();
	/// Per-player overrides
	private final Map<String, Map<Integer, Glamour>> playerOverrides = new HashMap<>();
	private final Map<Player, Set<KitType>> activePlayerOverrides = new HashMap<>();
	private volatile Glamour localEquipOverride;

	private volatile Future<?> reconcileFuture;
	private volatile boolean batchMode;

	// --- Icon state ---
	/// Stores the ItemID -> Glamours for all pending icon creations in the batch.
	/// Only one icon for an ItemID can be created at a time because creation uses a shared ItemComposition.
	private ConcurrentHashMap<Integer, IconPending> pendingIconBatch = new ConcurrentHashMap<>();
	private volatile Future<?> createIconBatchFuture;

	private void resetItemCaches()
	{
		client.getItemModelCache().reset();
		client.getItemSpriteCache().reset();
	}

	/* ==================== Glamour operations ==================== */

	PrimedItem getPrimedItem(int itemId)
	{
		return getAndUpdatePrimedItem(itemId, null);
	}

	private PrimedItem getAndUpdatePrimedItem(int itemId, @Nullable ItemComposition itemComposition)
	{
		var primedItem = primedItemMap.get(itemId);
		if (primedItem == null)
		{
			itemComposition = itemComposition != null ? itemComposition : ddItemManager.getItemDefinition(itemId);
			primedItem = PrimedItem.of(itemComposition, itemSheet);
			primedItemMap.put(itemId, primedItem);
		}
		else if (itemComposition != null)
		{
			primedItem.reprime(itemComposition);
		}
		return primedItem;
	}

	@Subscribe(priority = Float.MAX_VALUE)
	public void onPostItemComposition(PostItemComposition event)
	{
		final var itemComp = event.getItemComposition();
		final var itemId = itemComp.getId();

		if (primedItemMap.containsKey(itemId))
		{
			getAndUpdatePrimedItem(itemId, itemComp);
		}

		Glamour glamour;
		if ((glamour = appliedGlamourMap.get(itemId)) != null)
		{
			glamour.apply(itemComp);
		}
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		reconcilePlayer(event.getPlayer());
	}

	@Subscribe
	public void onPlayerChanged(PlayerChanged event)
	{
		var player = event.getPlayer();
		if (activePlayerOverrides.containsKey(player))
		{
			reconcilePlayer(event.getPlayer());
		}
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		clearPlayerOverrides(event.getPlayer());
	}

	@Subscribe
	public void onWorldChanged(WorldChanged event)
	{
		activePlayerOverrides.clear();
	}

	/**
	 * Get glamours for player.
	 */
	private Map<Integer, Glamour> getPlayerGlamours(@Nonnull Player player)
	{
		if (player == client.getLocalPlayer())
		{
			var override = localEquipOverride;
			if (override != null)
			{
				var merged = new HashMap<>(appliedGlamourMap);
				for (int itemId : override.getItemIds())
				{
					merged.put(itemId, override);
				}
				return merged;
			}
			return appliedGlamourMap;
		}
		var basePlayerOverrides = playerOverrides.get(player.getName());
		if (basePlayerOverrides != null)
		{
			var playerOverrides = new HashMap<>(appliedDefaultEquipMap);
			playerOverrides.putAll(basePlayerOverrides);
			return playerOverrides;
		}
		return appliedDefaultEquipMap;
	}

	private void reconcilePlayerByName(@Nonnull String playerName)
	{
		for (var entry : activePlayerOverrides.entrySet())
		{
			var player = entry.getKey();
			if (playerName.equals(player.getName()))
			{
				reconcilePlayer(player);
			}
		}
	}

	/**
	 * Reconcile player equipment overrides. Client thread only.
	 */
	private void reconcilePlayer(@Nonnull Player player)
	{
		var empty = EnumSet.noneOf(KitType.class);
		var currentKit = activePlayerOverrides.putIfAbsent(player, empty);
		currentKit = currentKit == null ? empty : currentKit;

		var activeKit = EnumSet.noneOf(KitType.class);
		var overrides = getPlayerGlamours(player);
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

			// Apply original if item is primed
			var primedItem = primedItemMap.get(itemId);
			if (primedItem != null)
			{
				primedItem.prime(comp.createColorTextureOverride(kit, itemId));
				activeKit.add(kit);
				continue;
			}

			// Remove old override if kit is no longer glamoured (can this even happen anymore?)
			if (currentKit.contains(kit))
			{
				comp.removeColorTextureOverride(kit);
			}
		}
		comp.setHash();
		currentKit.clear();
		currentKit.addAll(activeKit);
	}

	private void clearPlayerOverrides(Player player)
	{
		var overrides = activePlayerOverrides.get(player);
		if (overrides != null && player.getPlayerComposition() != null)
		{
			var comp = player.getPlayerComposition();
			overrides.forEach(comp::removeColorTextureOverride);
			comp.setHash();
		}
	}

	/**
	 * Backfill player state from missed onPlayerSpawned events.
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
				onPlayerSpawned(new PlayerSpawned(player));
			}
			for (var subWorldView : worldView.worldViews())
			{
				for (var player : subWorldView.players())
				{
					onPlayerSpawned(new PlayerSpawned(player));
				}
			}
			return true;
		});
	}

	/**
	 * Start glamour for item ID. Client thread only.
	 */
	Glamour startGlamour(int itemId)
	{
		var itemComp = ddItemManager.getItemComposition(itemId);
		return Glamour.start(getPrimedItem(itemId), itemComp.getIds());
	}

	/**
	 * Load glamour from data. Client thread only.
	 */
	Glamour loadGlamour(GlamourData data)
	{
		DedupeItemComposition comp;
		if (data.getItemKey() != null)
		{
			comp = ddItemManager.getItemComposition(data.getItemKey());
			if (comp == null)
			{
				// No item was found for this glamour's key; the key is corrupted.
				// Try to repair the corruption by finding a new item that most closely resembles the original glamour.
				data = DataRepairer.tryRepairOrThrow(this, ddItemManager, data);
				comp = ddItemManager.getItemComposition(data.getItemKey());
			}
		}
		else if (data.getItemId() != null)
		{
			comp = ddItemManager.getItemComposition(data.getItemId());
		}
		else
		{
			throw new IllegalArgumentException("Illegal GlamourData with no item key or id");
		}
		return Glamour.load(getPrimedItem(comp.getId()), comp.getIds(), data);
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
	GlamourVisibility getStagedVisibility(@Nonnull Glamour glam)
	{
		for (int key : glam.getItemIds())
		{
			Glamour staged = stagedGlamourMap.get(key);
			if (glam.isEquivalent(staged))
			{
				return GlamourVisibility.VISIBLE;
			}
		}
		for (int key : glam.getItemIds())
		{
			Glamour staged = stagedDefaultEquipMap.get(key);
			if (glam.isEquivalent(staged))
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
				for (int itemId : glam.getItemIds())
				{
					var primedItem = primedItemMap.get(itemId);
					if (primedItem != null)
					{
						primedItem.reprime();
					}
				}
				itemsChanged = true;
			}
		}
		for (Glamour glam : shouldBeApplied)
		{
			if (!currentlyApplied.contains(glam) || glam.clearDirty())
			{
				for (int itemId : glam.getItemIds())
				{
					var primedItem = getPrimedItem(itemId);
					if (primedItem != null)
					{
						glam.apply(primedItem.getItemComposition());
					}
				}
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
				reconcilePlayer(entry.getKey());
			}
		}
	}

	/**
	 * Revert all applied glamours for shutdown.
	 * Main thread only.
	 */
	public void revertAll()
	{
		localEquipOverride = null;
		clientThread.invokeLater(() -> {
			for (var entry : activePlayerOverrides.entrySet())
			{
				var player = entry.getKey();
				clearPlayerOverrides(player);
			}
			activePlayerOverrides.clear();
			playerOverrides.clear();
			clearAllStaged();
			appliedGlamourMap.clear();
			appliedDefaultEquipMap.clear();
			primedItemMap.values().forEach(PrimedItem::revert);
			resetItemCaches();
		});
	}

	void setLocalEquipmentOverride(Glamour override)
	{
		localEquipOverride = override;
		refreshLocalEquipment();
	}

	private void refreshLocalEquipment()
	{
		clientThread.invokeLater(() -> {
			var player = client.getLocalPlayer();
			if (player == null)
			{
				return;
			}
			reconcilePlayer(player);
		});
	}

	/* ==================== Sync related operations ==================== */

	/**
	 * Snapshot equippable glamours for the local player.
	 * Client thread only.
	 */
	public List<GlamourData> getEquippableGlamourSnapshot()
	{
		var wornItemIds = client.getLocalPlayerEquippableItemIds(ddItemManager);
		return getGlamourSnapshotForItems(wornItemIds);
	}

	/**
	 * Snapshot glamours for specific item IDs only.
	 */
	public List<GlamourData> getGlamourSnapshotForItems(Set<Integer> itemIds)
	{
		Map<Integer, Glamour> staged;
		synchronized (stageLock)
		{
			staged = new HashMap<>(stagedGlamourMap);
		}
		List<GlamourData> snapshot = new ArrayList<>();
		for (int itemId : itemIds)
		{
			var glamour = staged.get(itemId);
			if (glamour != null)
			{
				snapshot.add(glamour.getData(false, true));
			}
		}
		return snapshot;
	}

	/**
	 * Apply glamours to a player.
	 * Client thread only.
	 */
	public void updatePlayerGlamour(@Nonnull String playerName, Map<Integer, GlamourData> glamours)
	{
		Map<Integer, Glamour> overrideMap = new HashMap<>();
		for (var data : glamours.entrySet())
		{
			try
			{
				overrideMap.put(data.getKey(), loadGlamour(data.getValue()));
			}
			catch (Exception e)
			{
				log.warn("Failed to load glamour for {}: {}", playerName, data.getKey(), e);
			}
		}
		playerOverrides.put(playerName, overrideMap);
		reconcilePlayerByName(playerName);
	}

	/**
	 * Remove all player glamour data.
	 * Client thread only.
	 */
	public void clearPlayerGlamours()
	{
		var playerNames = new HashSet<>(playerOverrides.keySet());
		playerOverrides.clear();
		playerNames.forEach(this::reconcilePlayerByName);
	}

	/**
	 * Remove all glamour data for a player.
	 * Client thread only.
	 */
	public void removePlayerGlamour(@Nonnull String playerName)
	{
		playerOverrides.remove(playerName);
		reconcilePlayerByName(playerName);
	}

	/* ==================== Icon operations ==================== */

	/**
	 * Returns an AsyncBufferedImage that will populate with the icon at the next available opportunity.
	 */
	@Nonnull
	AsyncBufferedImage getIcon(int itemId, GlamState glamState)
	{
		AsyncBufferedImage img = new AsyncBufferedImage(
			clientThread, Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		IconPending IconPending = new IconPending(img, glamState);
		executor.execute(() -> queueIconCreation(itemId, IconPending));
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
		if (createIconBatchFuture == null && !pendingIconBatch.isEmpty())
		{
			createIconBatchFuture = executor.schedule(
				() -> clientThread.invokeLater(this::executeIconBatch),
				IMAGE_BATCH_DELAY_MS, TimeUnit.MILLISECONDS);
		}
	}

	private void executeIconBatch()
	{
		// Snapshot current batch items and start a new empty batch.
		var batch = pendingIconBatch;
		pendingIconBatch = new ConcurrentHashMap<>();

		resetItemCaches();
		for (var entry : batch.entrySet())
		{
			final var itemId = entry.getKey();
			final var iconState = entry.getValue().state;
			final var image = entry.getValue().image;
			final var primedItem = getPrimedItem(itemId);
			primedItem.runOnMutableItemComp(itemComp -> {
				iconState.applyTo(itemComp);
				if (!createSprite(itemId, image))
				{
					// Retry failures. ItemManager AsyncBufferedImage retries infinitely so this should be safe.
					pendingIconBatch.putIfAbsent(entry.getKey(), entry.getValue());
				}
			});

			image.loaded();
		}
		resetItemCaches();
		createIconBatchFuture = null;
		scheduleIconBatch();
	}

	private boolean createSprite(int itemId, @Nonnull BufferedImage target)
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
			return true;
		}
		return false;
	}
}
