package io.huze.glamourer.glam;

import io.huze.glamourer.Extensions;
import io.huze.glamourer.color.Colors;
import io.huze.glamourer.item.ItemEquipSheet;
import io.huze.glamourer.item.ItemSpotAnimSheet;
import io.huze.glamourer.item.PetSheet;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.experimental.ExtensionMethod;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
@ExtensionMethod({Extensions.class})
public class PaletteFactory
{
	private final Client client;
	private final PetSheet petSheet;
	private final ItemEquipSheet itemEquipSheet;
	private final ItemSpotAnimSheet itemSpotAnimSheet;

	@Nonnull
	public Palette of(@Nonnull Collection<ItemComposition> itemCompositions)
	{
		var inventoryModelIds = new HashSet<Integer>();
		var otherModelIds = new HashSet<Integer>();
		for (var itemComposition : itemCompositions)
		{
			inventoryModelIds.add(itemComposition.getInventoryModel());
			itemEquipSheet.addModels(itemComposition.getId(), otherModelIds);
			otherModelIds.addAll(petSheet.getNpcModelIds(itemComposition.getId()));
		}
		otherModelIds.removeAll(inventoryModelIds);

		var colors = new HashSet<Short>();
		var textures = new HashSet<Short>();
		for (int modelId : inventoryModelIds)
		{
			if (!addModel(modelId, colors, textures))
			{
				throw new IllegalStateException("Failed to load inventory model " + modelId);
			}
		}
		for (int modelId : otherModelIds)
		{
			addModel(modelId, colors, textures);
		}
		for (var itemComposition : itemCompositions)
		{
			for (var spotAnim : itemSpotAnimSheet.getRowsForItem(itemComposition.getId()))
			{
				addSpotAnimModel(spotAnim, colors, textures);
			}
		}
		return new Palette(sortedArray(colors), sortedArray(textures));
	}

	private void addSpotAnimModel(@Nonnull ItemSpotAnimSheet.Row spotAnim, @Nonnull Set<Short> colors, @Nonnull Set<Short> textures)
	{
		var modelData = client.loadModelData(spotAnim.getModelId());
		if (modelData == null)
		{
			return;
		}
		for (short color : modelData.getFaceColors())
		{
			colors.add(Colors.applyReplacements(color, spotAnim.getRecolorFind(), spotAnim.getRecolorReplace()));
		}
		var faceTextures = modelData.getFaceTextures();
		if (faceTextures != null)
		{
			for (short texture : faceTextures)
			{
				if (texture != -1)
				{
					textures.add(Colors.applyReplacements(texture, spotAnim.getRetextureFind(), spotAnim.getRetextureReplace()));
				}
			}
		}
	}

	private boolean addModel(int modelId, @Nonnull Set<Short> colors, @Nonnull Set<Short> textures)
	{
		var modelData = client.loadModelData(modelId);
		if (modelData == null)
		{
			return false;
		}
		for (short color : modelData.getFaceColors())
		{
			colors.add(color);
		}
		var faceTextures = modelData.getFaceTextures();
		if (faceTextures != null)
		{
			for (short texture : faceTextures)
			{
				if (texture != -1)
				{
					textures.add(texture);
				}
			}
		}
		return true;
	}

	@Nonnull
	private static short[] sortedArray(@Nonnull Set<Short> values)
	{
		short[] array = values.toShortArray();
		Arrays.sort(array);
		return array;
	}
}
