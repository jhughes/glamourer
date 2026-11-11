package io.huze.glamourer;

import io.huze.glamourer.texture.SamplableTexture;
import io.huze.glamourer.texture.TextureAnimation;
import io.huze.glamourer.texture.TextureProvider;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.TextureManager;
import net.runelite.cache.definitions.TextureDefinition;
import net.runelite.cache.item.RSTextureProvider;

/// Samples textures straight from a game cache, for rendering outside the client.
final class CacheTextureProvider implements TextureProvider
{
	private final TextureManager textures;
	private final RSTextureProvider pixels;
	private final Map<Short, SamplableTexture> loaded = new HashMap<>();

	CacheTextureProvider(@Nonnull TextureManager textures, @Nonnull SpriteManager sprites)
	{
		this.textures = textures;
		this.pixels = new RSTextureProvider(textures, sprites);
	}

	@Nonnull
	@Override
	public synchronized SamplableTexture get(short textureId)
	{
		return loaded.computeIfAbsent(textureId, this::load);
	}

	@Nonnull
	private SamplableTexture load(short textureId)
	{
		TextureDefinition texture = textureId < 0 ? null : textures.findTexture(textureId);
		if (texture == null)
		{
			return SamplableTexture.NONE;
		}
		// The cache loader stores the client's averageRGB field, a packed HSL, under this name.
		short averageColor = (short) texture.getMissingColor();
		int[] texels;
		try
		{
			texels = pixels.load(textureId);
		}
		catch (RuntimeException e)
		{
			return SamplableTexture.unavailable(averageColor);
		}
		int size = texels == null ? 0 : (int) Math.round(Math.sqrt(texels.length));
		if (size == 0 || size * size != texels.length)
		{
			return SamplableTexture.unavailable(averageColor);
		}
		return new SamplableTexture(size, texels.clone(),
			TextureAnimation.of(texture.getAnimationDirection(), texture.getAnimationSpeed()), averageColor);
	}
}
