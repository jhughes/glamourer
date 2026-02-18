package io.huze.glamourer.glam;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.SpritePixels;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
@Singleton
public class IconService
{
	private static final int MAX_ICON_CACHE_SIZE = 256;
	private static final int IMAGE_BATCH_DELAY_MS = 1;

	private final ItemManager itemManager;
	private final Client client;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private final Cache<String, BufferedImage> iconCache = CacheBuilder.newBuilder()
		.maximumSize(MAX_ICON_CACHE_SIZE)
		.build();
	/// Stores the ItemID -> Glamours for all pending icon creations in the batch.
	/// Only one icon for an ItemID can be created at a time because creation uses a shared ItemComposition.
	private final ConcurrentHashMap<Integer, Glamour> pendingCreateIconBatch = new ConcurrentHashMap<>();
	private volatile Future<?> createIconBatchFuture;

	@Inject
	public IconService(ItemManager itemManager, Client client, ClientThread clientThread, ScheduledExecutorService executor)
	{
		this.itemManager = itemManager;
		this.client = client;
		this.clientThread = clientThread;
		this.executor = executor;
	}

	/**
	 * Returns an icon for the glamour's current staged state.
	 * On cache hit, returns the image immediately.
	 * On cache miss, returns an AsyncBufferedImage that should populate on the next frame.
	 */
	public BufferedImage getIcon(Glamour glamour)
	{
		BufferedImage cached = iconCache.getIfPresent(IconKey.of(glamour));
		if (cached != null)
		{
			return cached;
		}

		AsyncBufferedImage img = new AsyncBufferedImage(
			clientThread, Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		iconCache.put(IconKey.of(glamour), img);

		executor.execute(() -> queueIconCreation(glamour));
		return img;
	}

	private void queueIconCreation(Glamour glamour)
	{
		// Retry with a small delay while another icon is being created for the same item ID
		var entry = pendingCreateIconBatch.putIfAbsent(glamour.getPrimaryItemId(), glamour);
		if (entry != null)
		{
			executor.schedule(() -> queueIconCreation(glamour), IMAGE_BATCH_DELAY_MS, TimeUnit.MILLISECONDS);
			return;
		}

		scheduleIconBatch();
	}

	private void scheduleIconBatch()
	{
		if (createIconBatchFuture == null)
		{
			createIconBatchFuture = executor.schedule(
				() -> clientThread.invokeLater(this::createPendingImages),
				IMAGE_BATCH_DELAY_MS, TimeUnit.MILLISECONDS);
		}
	}

	/// Apply the glamour ephemerally to run a function and then revert it.
	/// This enables icon creation for non-applied glamours.
	private void runOnGlam(Glamour glam, Runnable r)
	{
		var composition = itemManager.getItemComposition(glam.getPrimaryItemId());
		var originalState = GlamState.backup(composition);
		glam.apply(composition);
		r.run();
		originalState.applyTo(composition);
	}

	private void createPendingImages()
	{
		immediateCacheReset();
		for (var entry : pendingCreateIconBatch.entrySet())
		{
			Glamour glamour = entry.getValue();
			BufferedImage target = iconCache.getIfPresent(IconKey.of(glamour));
			runOnGlam(glamour, () -> {
				var spritePixels = client.createItemSprite(
					glamour.getPrimaryItemId(),
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
			});
			if (target instanceof AsyncBufferedImage)
			{
				((AsyncBufferedImage) target).loaded();
			}
		}
		immediateCacheReset();
		pendingCreateIconBatch.clear();
		createIconBatchFuture = null;
	}

	private void immediateCacheReset()
	{
		client.getItemModelCache().reset();
		client.getItemSpriteCache().reset();
	}
}
