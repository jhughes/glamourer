package io.huze.glamourer.model;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.runelite.api.Client;
import net.runelite.api.ModelData;

/// Loads models out of the client's cache. Client thread only.
@UtilityClass
final class ModelLoader
{
	/// Null while the model is still loading.
	@Nullable
	static ModelData load(@Nonnull Client client, int modelId)
	{
		var loaded = client.loadModelData(modelId);
		return loaded == null ? null : loaded.shallowCopy();
	}

	/// Null if any part is still loading.
	@Nullable
	static ModelData merge(@Nonnull Client client, @Nonnull int[] modelIds)
	{
		if (modelIds.length == 0)
		{
			return null;
		}
		var parts = new ModelData[modelIds.length];
		for (int i = 0; i < modelIds.length; i++)
		{
			parts[i] = client.loadModelData(modelIds[i]);
			if (parts[i] == null)
			{
				return null;
			}
		}
		// Don't merge a solo part because it re-derives normals which causes minor lighting differences.
		return parts.length == 1 ? parts[0].shallowCopy() : client.mergeModels(parts);
	}
}
