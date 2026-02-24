package io.huze.glamourer.item;

import io.huze.glamourer.CsvLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ModelData;
import net.runelite.client.game.ItemManager;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ItemSheet
{
	public static final String[] CSV_HEADERS = {"id", "release_date", "removal_date", "quest", "category", "male_model0", "male_model1", "male_model2", "female_model0", "female_model1", "female_model2"};

	private final Client client;
	private final ItemManager itemManager;
	private final CsvLoader csvLoader;
	private final CompletableFuture<Void> future = loadItemsAsync();

	private volatile Map<Integer, ItemRow> itemsById;
	@Getter
	private volatile Set<Integer> removedItemIds;
	@Getter
	private volatile Set<Integer> questItemIds;
	@Getter
	private volatile Set<Integer> uncommonItemIds;

	public boolean isLoadedOrRethrow()
	{
		if (future.isCompletedExceptionally())
		{
			future.join();
		}
		return future.isDone();
	}

	public Collection<ModelData> getModels(int itemId)
	{
		var inventoryModelData = client.loadModelData(itemManager.getItemComposition(itemId).getInventoryModel());
		if (inventoryModelData == null)
		{
			throw new IllegalStateException("Failed to load model data for item: " + itemId);
		}
		var row = getItemById(itemId);
		if (row == null)
		{
			return List.of(inventoryModelData);
		}
		var modelList = new ArrayList<ModelData>();
		modelList.add(inventoryModelData);

		var modelIds = new int[]{
			row.getMaleModel0(), row.getMaleModel1(), row.getMaleModel2(),
			row.getFemaleModel0(), row.getFemaleModel1(), row.getFemaleModel2()
		};
		for (int modelId : modelIds)
		{
			if (modelId > 0)
			{
				modelList.add(client.loadModelData(modelId));
			}
		}
		return modelList;
	}

	public ItemRow getItemById(int itemId)
	{
		return itemsById.get(itemId);
	}

	public CompletableFuture<Void> loadItemsAsync()
	{
		final var startTime = System.nanoTime();
		return CompletableFuture.runAsync(() -> {
			List<ItemRow> items = csvLoader.load(ItemSheet.class, "item_sheet.csv", CSV_HEADERS, ItemRow::fromCsvColumns);
			this.itemsById = items.stream()
				.collect(Collectors.toMap(ItemRow::getId, Function.identity()));
			this.removedItemIds = items.stream()
				.filter(i -> i.removalDate < Integer.MAX_VALUE)
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
			log.debug("ItemSheet loaded in {}ms", (System.nanoTime() - startTime) / 1_000_000);
		});
	}
}
