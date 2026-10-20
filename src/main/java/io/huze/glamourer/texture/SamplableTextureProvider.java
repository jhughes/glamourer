package io.huze.glamourer.texture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Texture;
import net.runelite.api.TextureProvider;

/// Caches every client texture at the client's brightness. Loaded on the client thread; read anywhere.
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class SamplableTextureProvider
{
	private static final SamplableTexture[] NONE_LOADED = {};

	private final Client client;

	/// Null where the client has no texture.
	@Nonnull
	private volatile SamplableTexture[] texturesById = NONE_LOADED;
	private double loadedBrightness = Double.NaN;
	private boolean complete;
	@Getter
	private volatile int generation;

	/// Client thread only. Reloads everything when client brightness changes.
	public void loadIfStale()
	{
		var clientTextureProvider = client.getTextureProvider();
		var textures = clientTextureProvider == null ? null : clientTextureProvider.getTextures();
		if (textures == null)
		{
			return;
		}
		double brightness = clientTextureProvider.getBrightness();
		boolean reloadAll = brightness != loadedBrightness || textures.length != texturesById.length;
		if (!reloadAll && complete)
		{
			return;
		}

		var previous = texturesById;
		var loaded = new SamplableTexture[textures.length];
		boolean allUsable = true;
		boolean changed = reloadAll;
		for (int textureId = 0; textureId < textures.length; textureId++)
		{
			if (textures[textureId] == null)
			{
				continue;
			}
			var kept = reloadAll ? null : previous[textureId];
			if (kept != null && kept.isUsable())
			{
				loaded[textureId] = kept;
				continue;
			}
			loaded[textureId] = load(clientTextureProvider, textures[textureId], (short) textureId);
			changed |= loaded[textureId].isUsable();
			allUsable &= loaded[textureId].isUsable();
		}
		texturesById = loaded;
		loadedBrightness = brightness;
		complete = allUsable;
		if (changed)
		{
			generation++;
		}
	}

	/// Any thread. Unusable while the client has not loaded it.
	@Nonnull
	public SamplableTexture get(short textureId)
	{
		var texture = find(textureId);
		return texture == null ? SamplableTexture.NONE : texture;
	}

	/// Loadable or not.
	public boolean hasTexture(short textureId)
	{
		return find(textureId) != null;
	}

	public int getTextureCount()
	{
		return texturesById.length;
	}

	@Nullable
	private SamplableTexture find(short textureId)
	{
		var textures = texturesById;
		return textureId >= 0 && textureId < textures.length ? textures[textureId] : null;
	}

	@Nonnull
	private static SamplableTexture load(@Nonnull TextureProvider clientTextureProvider, @Nonnull Texture texture,
										 short textureId)
	{
		short averageColor = getAverageColor(clientTextureProvider, textureId);
		try
		{
			int[] pixels = clientTextureProvider.load(textureId);
			if (pixels == null)
			{
				return SamplableTexture.unavailable(averageColor);
			}
			int size = (int) Math.round(Math.sqrt(pixels.length));
			if (size == 0 || size * size != pixels.length)
			{
				return SamplableTexture.unavailable(averageColor);
			}
			return new SamplableTexture(size, pixels.clone(), TextureAnimation.of(texture), averageColor);
		}
		catch (Exception e)
		{
			log.debug("No pixels for texture {}", textureId, e);
			return SamplableTexture.unavailable(averageColor);
		}
	}

	private static short getAverageColor(@Nonnull TextureProvider clientTextureProvider, short textureId)
	{
		try
		{
			return (short) clientTextureProvider.getDefaultColor(textureId);
		}
		catch (Exception e)
		{
			log.debug("No color for texture {}", textureId, e);
			return SamplableTexture.NONE.getAverageColor();
		}
	}
}
