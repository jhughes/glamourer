package io.huze.glamourer.texture;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class TextureImages
{
	private final ClientThread clientThread;
	private final SamplableTextureProvider textureProvider;

	/// Client thread only.
	@Nullable
	private Map<Short, TextureImage> images;
	private int imagesGeneration;

	@Nonnull
	public CompletableFuture<Map<Short, TextureImage>> loadAsync()
	{
		CompletableFuture<Map<Short, TextureImage>> future = new CompletableFuture<>();
		clientThread.invokeLater(() -> {
			try
			{
				future.complete(getImages());
			}
			catch (Exception e)
			{
				log.warn("Failed to load texture images", e);
				future.complete(Map.of());
			}
		});
		return future;
	}

	@Nonnull
	private Map<Short, TextureImage> getImages()
	{
		textureProvider.loadIfStale();
		int textureCount = textureProvider.getTextureCount();
		if (images != null && imagesGeneration == textureProvider.getGeneration())
		{
			return images;
		}

		var built = new LinkedHashMap<Short, TextureImage>();
		boolean complete = true;
		for (short textureId = 0; textureId < textureCount; textureId++)
		{
			SamplableTexture texture = textureProvider.get(textureId);
			if (texture.isUsable())
			{
				built.put(textureId, new TextureImage(texture));
			}
			else if (textureProvider.hasTexture(textureId))
			{
				complete = false;
			}
		}
		var result = Collections.unmodifiableMap(built);
		if (complete)
		{
			images = result;
			imagesGeneration = textureProvider.getGeneration();
		}
		return result;
	}
}
