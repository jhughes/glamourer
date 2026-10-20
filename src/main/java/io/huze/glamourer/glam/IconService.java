package io.huze.glamourer.glam;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.huze.glamourer.Config;
import io.huze.glamourer.item.DedupeItemManager;
import io.huze.glamourer.model.ItemIconRenderer;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.AsyncBufferedImage;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class IconService
{
	private final Cache<IconKey, BufferedImage> iconCache = CacheBuilder.newBuilder()
		.maximumSize(256)
		.expireAfterAccess(60, TimeUnit.SECONDS)
		.build();
	private final ClientThread clientThread;
	private final DedupeItemManager ddItemManager;
	private final ItemIconRenderer iconRenderer;
	private final GlamourEngine glamourEngine;
	private final Config config;

	@Nonnull
	public BufferedImage getIcon(Glamour glamour)
	{
		return getIcon(glamour, config.iconScale());
	}

	@Nonnull
	public BufferedImage getIcon(Glamour glamour, IconScale scale)
	{
		var state = glamour.snapshotState();
		final int inventoryModel = state.getModel();
		final var edits = state.getModelEdits();
		final int itemId = glamour.getPrimaryItemId();
		return getIcon(new IconKey(itemId, edits, scale), (target, onUnavailable) ->
			iconRenderer.drawIcon(itemId, inventoryModel, edits, scale.getScale(), target, onUnavailable));
	}

	@Nonnull
	public BufferedImage getItemIcon(int itemId)
	{
		var scale = config.iconScale();
		return getIcon(IconKey.unglamoured(itemId, scale), (target, onUnavailable) ->
			clientThread.invokeLater(() -> {
				var primedItem = glamourEngine.findPrimedItem(ddItemManager.getItemDefinition(itemId).getId());
				if (primedItem == null)
				{
					iconRenderer.drawIcon(itemId, scale.getScale(), target, onUnavailable);
				}
				else
				{
					iconRenderer.drawIcon(itemId, primedItem.getModel(), primedItem.getOriginalEdits(), scale.getScale(),
						target, onUnavailable);
				}
			}));
	}

	@Nonnull
	public Dimension getIconSize()
	{
		return ItemIconRenderer.getIconSize(config.iconScale().getScale());
	}

	public void clearCache()
	{
		iconCache.invalidateAll();
	}

	private interface IconDraw
	{
		void draw(@Nonnull AsyncBufferedImage target, @Nonnull Runnable onUnavailable);
	}

	@Nonnull
	private BufferedImage getIcon(@Nonnull IconKey key, @Nonnull IconDraw draw)
	{
		BufferedImage cached = iconCache.getIfPresent(key);
		if (cached != null)
		{
			return cached;
		}
		var size = ItemIconRenderer.getIconSize(key.getScale().getScale());
		var image = new AsyncBufferedImage(clientThread, size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		iconCache.put(key, image);
		image.onLoaded(() -> {
			BufferedImage copy = new BufferedImage(
				image.getColorModel(), image.getRaster(), image.isAlphaPremultiplied(), null);
			iconCache.put(key, copy);
		});
		draw.draw(image, () -> iconCache.invalidate(key));
		return image;
	}
}
