package io.huze.glamourer.item;

import io.huze.glamourer.Sheet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ModelData;

@Singleton
public class ItemSheet extends Sheet<ItemRow>
{
	private static final String[] CSV_HEADERS = {"id", "release_date", "removal_date", "quest", "category", "male_model0", "male_model1", "male_model2", "female_model0", "female_model1", "female_model2"};

	private final Client client;
	private final PetSheet petSheet;

	private volatile Map<Integer, ItemRow> itemsById = Map.of();
	@Getter
	private volatile Set<Integer> removedItemIds = Set.of();
	@Getter
	private volatile Set<Integer> questItemIds = Set.of();
	@Getter
	private volatile Set<Integer> uncommonItemIds = Set.of();

	@Inject
	public ItemSheet(Client client, PetSheet petSheet)
	{
		super("item_sheet.csv", CSV_HEADERS, ItemRow::fromCsvColumns);
		this.client = client;
		this.petSheet = petSheet;
	}

	@Nonnull
	public Collection<ModelData> getModels(@Nonnull ItemComposition itemComposition)
	{
		final var itemId = itemComposition.getId();
		var inventoryModelData = client.loadModelData(itemComposition.getInventoryModel());
		if (inventoryModelData == null)
		{
			throw new IllegalStateException("Failed to load model data for item: " + itemId);
		}
		var modelList = new ArrayList<ModelData>();
		modelList.add(inventoryModelData);

		for (int npcModelId : petSheet.getNpcModelIds(itemId))
		{
			var npcModelData = client.loadModelData(npcModelId);
			if (npcModelData != null)
			{
				modelList.add(npcModelData);
			}
		}

		var row = getItemById(itemId);
		if (row == null)
		{
			return modelList;
		}

		var modelIds = new int[]{
			row.getMaleModel0(), row.getMaleModel1(), row.getMaleModel2(),
			row.getFemaleModel0(), row.getFemaleModel1(), row.getFemaleModel2()
		};
		for (int modelId : modelIds)
		{
			if (modelId > 0)
			{
				var modelData = client.loadModelData(modelId);
				if (modelData != null)
				{
					modelList.add(modelData);
				}
			}
		}
		return modelList;
	}

	@Nullable
	public ItemRow getItemById(int itemId)
	{
		return itemsById.get(itemId);
	}

	@Override
	protected void load(List<ItemRow> items)
	{
		this.itemsById = items.stream()
			.collect(Collectors.toMap(ItemRow::getId, Function.identity()));
		this.removedItemIds = items.stream()
			.filter(ItemRow::isRemoved)
			.map(ItemRow::getId)
			.collect(Collectors.toSet());
		this.questItemIds = items.stream()
			.filter(ItemRow::isQuest)
			.map(ItemRow::getId)
			.collect(Collectors.toSet());
		this.uncommonItemIds = items.stream()
			.filter(ItemRow::isUncommon)
			.map(ItemRow::getId)
			.collect(Collectors.toSet());
	}
}
