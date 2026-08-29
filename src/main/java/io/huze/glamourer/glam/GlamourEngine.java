package io.huze.glamourer.glam;

import io.huze.glamourer.Extensions;
import io.huze.glamourer.item.DummyItemSheet;
import io.huze.glamourer.item.DedupeItemComposition;
import io.huze.glamourer.item.DedupeItemManager;
import io.huze.glamourer.item.ItemSheet;
import io.huze.glamourer.plate.DisplayStyle;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.SpritePixels;
import net.runelite.api.events.AnimationChanged;
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
import net.runelite.client.util.Text;

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
	private final DummyItemSheet dummyItemSheet;
	private final ScheduledExecutorService executor;

	@Setter
	@Nonnull
	private Runnable onGlamoursChanged = () -> {};

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
	/// Per-player overrides (keys are standardized names)
	private final Map<String, Map<Integer, Glamour>> playerOverrides = new HashMap<>();
	private final Map<Player, Set<KitType>> activePlayerOverrides = new HashMap<>();
	@Nullable
	private volatile Glamour highlightOverride;

	@Nullable
	private volatile Future<?> reconcileFuture;
	private volatile boolean batchMode;

	// --- Icon state ---
	/// Guards both icon batch fields below.
	private final Object iconLock = new Object();
	/// Stores ItemID -> Queue of all icon generating to perform in the next batch.
	private final Map<Integer, Queue<IconPending>> pendingIconBatch = new HashMap<>();
	@Nullable
	private Future<?> createIconBatchFuture;

	private void resetItemCaches()
	{
		client.getItemModelCache().reset();
		client.getItemSpriteCache().reset();
	}

	/* ==================== Glamour operations ==================== */

	@Nonnull
	PrimedItem getPrimedItem(int itemId)
	{
		return getAndUpdatePrimedItem(itemId, null);
	}

	@Nonnull
	private PrimedItem getPrimedItem(int visibleItemId, int itemId)
	{
		if (visibleItemId == itemId)
		{
			return getPrimedItem(itemId);
		}
		// Visible item is a dummy; prime it using the real item.
		var primedItem = primedItemMap.get(visibleItemId);
		if (primedItem == null)
		{
			var visibleComp = ddItemManager.getItemDefinition(visibleItemId);
			primedItem = PrimedItem.ofDummy(visibleComp, getPrimedItem(itemId));
			primedItemMap.put(visibleItemId, primedItem);
		}
		return primedItem;
	}

	@Nonnull
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
		activePlayerOverrides.remove(event.getPlayer());
	}

	@Subscribe
	public void onWorldChanged(WorldChanged event)
	{
		activePlayerOverrides.clear();
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() instanceof Player && activePlayerOverrides.containsKey(event.getActor()))
		{
			reconcilePlayer((Player) event.getActor());
		}
	}

	/**
	 * Get glamours for player.
	 */
	@Nonnull
	private Map<Integer, Glamour> getPlayerGlamours(@Nonnull Player player)
	{
		if (player == client.getLocalPlayer())
		{
			var override = highlightOverride;
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
		var standardizedName = player.getName();
		if (standardizedName != null)
		{
			standardizedName = Text.standardize(standardizedName);
		}
		var basePlayerOverrides = playerOverrides.get(standardizedName);
		if (basePlayerOverrides != null)
		{
			var playerOverrides = new HashMap<>(appliedDefaultEquipMap);
			playerOverrides.putAll(basePlayerOverrides);
			return playerOverrides;
		}
		return appliedDefaultEquipMap;
	}

	private void reconcilePlayerByName(@Nonnull String standardizedName)
	{
		for (var entry : activePlayerOverrides.entrySet())
		{
			var player = entry.getKey();
			var name = player.getName();
			if (name != null && standardizedName.equals(Text.standardize(name)))
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

		// Extract item ID overrides from animation.
		int rightItemId = -1;
		int leftItemId = -1;
		int animationId = player.getAnimation();
		if (animationId != -1)
		{
			var animation = client.loadAnimation(animationId);
			if (animation != null)
			{
				rightItemId = animation.getRightHandItem();
				leftItemId = animation.getLeftHandItem();
			}
		}
		var activeKit = EnumSet.noneOf(KitType.class);
		var overrides = getPlayerGlamours(player);
		var comp = player.getPlayerComposition();
		var equipmentIds = comp.getEquipmentIds();
		for (int kitIdx = 0; kitIdx < equipmentIds.length; kitIdx++)
		{
			KitType kit = KitType.values()[kitIdx];
			int equipmentId = equipmentIds[kitIdx];
			int visibleItemId = equipmentId - PlayerComposition.ITEM_OFFSET;
			if (kit == KitType.WEAPON && rightItemId != -1)
			{
				visibleItemId = rightItemId;
			}
			else if (kit == KitType.SHIELD && leftItemId != -1)
			{
				visibleItemId = leftItemId;
			}
			else if (equipmentId < PlayerComposition.ITEM_OFFSET)
			{
				continue;
			}
			int itemId = dummyItemSheet.getItemIdForVisibleItemId(visibleItemId);

			// Apply override if one exists
			var override = overrides.get(itemId);
			if (override != null)
			{
				getPrimedItem(visibleItemId, itemId);
				override.applyReplacement(comp.createColorTextureOverride(kit, visibleItemId));
				activeKit.add(kit);
				continue;
			}

			// Apply original if item is primed
			var primedItem = primedItemMap.get(visibleItemId);
			if (primedItem != null)
			{
				primedItem.prime(comp.createColorTextureOverride(kit, visibleItemId));
				activeKit.add(kit);
				continue;
			}

			// Remove old override if kit is no longer glamoured
			if (currentKit.contains(kit))
			{
				comp.removeColorTextureOverride(kit);
			}
		}
		comp.setHash();
		currentKit.clear();
		currentKit.addAll(activeKit);
	}

	private void clearPlayerOverrides(@Nonnull Player player)
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

	/// Loads each datum, skipping and logging any that fail — the one policy for turning synced data
	/// back into glamours outside the engine.
	public List<Glamour> loadGlamours(Collection<GlamourData> data)
	{
		List<Glamour> loaded = new ArrayList<>(data.size());
		for (GlamourData datum : data)
		{
			try
			{
				loaded.add(loadGlamour(datum));
			}
			catch (Exception e)
			{
				log.debug("Failed to load a glamour", e);
			}
		}
		return loaded;
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
		synchronized (stageLock)
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
		}
		return GlamourVisibility.HIDDEN;
	}

	/**
	 * Callable from any thread.
	 */
	public boolean isGlobal(int itemId)
	{
		synchronized (stageLock)
		{
			return stagedDefaultEquipMap.containsKey(itemId);
		}
	}

	/**
	 * Clear all staged glamours, run the action to re-stage, then schedule a single reconcile.
	 */
	void batch(Runnable action)
	{
		batchMode = true;
		try
		{
			clearAllStaged();
			action.run();
		}
		finally
		{
			batchMode = false;
		}
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
					glam.apply(getPrimedItem(itemId).getItemComposition());
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
			onGlamoursChanged.run();
		}
	}

	/**
	 * Revert all applied glamours for shutdown.
	 * Main thread only.
	 */
	public void revertAll()
	{
		highlightOverride = null;
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

	void setHighlightOverride(@Nullable Glamour override)
	{
		highlightOverride = override;
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
	 * Snapshot glamours for specific item IDs only.
	 * Client thread only.
	 */
	@Nonnull
	public List<GlamourData> getGlamourSnapshotForItems(@Nonnull Collection<Integer> itemIds)
	{
		Map<Integer, Glamour> staged = new LinkedHashMap<>();
		synchronized (stageLock)
		{
			for (int itemId : itemIds)
			{
				var glamour = stagedGlamourMap.get(itemId);
				if (glamour != null)
				{
					staged.put(itemId, glamour);
				}
			}
		}
		List<GlamourData> snapshot = new ArrayList<>(staged.size());
		for (var entry : staged.entrySet())
		{
			snapshot.add(entry.getValue().getData(entry.getKey(), false));
		}
		return snapshot;
	}

	/**
	 * Apply glamours to a player.
	 * Client thread only.
	 */
	public void updatePlayerGlamour(@Nonnull String standardizedName, @Nonnull Map<Integer, GlamourData> glamours)
	{
		Map<Integer, Glamour> overrideMap = new LinkedHashMap<>();
		for (var data : glamours.entrySet())
		{
			try
			{
				overrideMap.put(data.getKey(), loadGlamour(data.getValue()));
			}
			catch (Exception e)
			{
				log.warn("Failed to load glamour for {}: {}", standardizedName, data.getKey(), e);
			}
		}
		playerOverrides.put(standardizedName, overrideMap);
		reconcilePlayerByName(standardizedName);
	}

	public Collection<Glamour> getPlayerGlamours(@Nonnull String standardizedName)
	{
		var glamours = playerOverrides.get(standardizedName);
		return glamours != null ? glamours.values() : Collections.emptySet();
	}

	/**
	 * Remove all player glamour data.
	 * Client thread only.
	 */
	public void clearPlayerGlamours()
	{
		var standardizedNames = new HashSet<>(playerOverrides.keySet());
		playerOverrides.clear();
		standardizedNames.forEach(this::reconcilePlayerByName);
	}

	/**
	 * Remove all glamour data for a player.
	 * Client thread only.
	 */
	public void removePlayerGlamour(@Nonnull String standardizedName)
	{
		playerOverrides.remove(standardizedName);
		reconcilePlayerByName(standardizedName);
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
		IconPending iconPending = new IconPending(img, glamState);
		executor.execute(() -> queueIconCreation(itemId, iconPending));
		return img;
	}

	private void queueIconCreation(final int itemId, @Nonnull final IconPending iconPending)
	{
		synchronized (iconLock)
		{
			pendingIconBatch.computeIfAbsent(itemId, k -> new ArrayDeque<>()).add(iconPending);
			scheduleIconBatch();
		}
	}

	/// Caller must hold iconLock.
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
		Map<Integer, Queue<IconPending>> batch;
		synchronized (iconLock)
		{
			batch = new HashMap<>(pendingIconBatch);
			pendingIconBatch.clear();
			createIconBatchFuture = null;
		}

		// Render once per item id per round until every queue is empty.
		while (!batch.isEmpty())
		{
			resetItemCaches();
			for (var it = batch.entrySet().iterator(); it.hasNext(); )
			{
				var entry = it.next();
				final var itemId = entry.getKey();
				if (entry.getValue().isEmpty())
				{
					it.remove();
					continue;
				}
				final var iconState = entry.getValue().poll();
				// Touch the item definition to ensure our reference is fresh.
				ddItemManager.getItemDefinition(itemId);
				getPrimedItem(itemId).runOnMutableItemComp(itemComp -> {
					iconState.state.applyTo(itemComp);
					if (createSprite(itemId, iconState.image))
					{
						iconState.image.loaded();
					}
					else
					{
						// Retry failures. ItemManager AsyncBufferedImage retries infinitely so this should be safe.
						queueIconCreation(itemId, iconState);
					}
				});
			}
		}
		resetItemCaches();
		synchronized (iconLock)
		{
			scheduleIconBatch();
		}
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
