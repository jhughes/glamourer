package io.huze.glamourer.glam;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class IconService
{
	private final Cache<IconKey, BufferedImage> iconCache = CacheBuilder.newBuilder()
		.maximumSize(256)
		.expireAfterAccess(60, TimeUnit.SECONDS)
		.build();
	private final GlamourEngine engine;

	@Nonnull
	public BufferedImage getIcon(Glamour glamour)
	{
		IconKey key = IconKey.of(glamour);
		BufferedImage cached = iconCache.getIfPresent(key);
		if (cached != null)
		{
			return cached;
		}
		var img = engine.getIcon(glamour);
		iconCache.put(key, img);
		img.onLoaded(() -> {
			// Replace the AsyncBufferedImage with a plain copy so future cache hits
			// skip the async callback path in setScaledIcon entirely.
			BufferedImage copy = new BufferedImage(
				img.getColorModel(), img.getRaster(), img.isAlphaPremultiplied(), null);
			iconCache.put(key, copy);
		});
		return img;
	}
}
