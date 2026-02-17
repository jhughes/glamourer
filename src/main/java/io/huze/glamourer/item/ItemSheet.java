package io.huze.glamourer.item;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ModelData;
import net.runelite.client.game.ItemManager;

@Slf4j
@Singleton
public class ItemSheet
{
	public static final String[] CSV_HEADERS = {"id", "release_date", "removal_date", "quest", "category", "male_model0", "male_model1", "male_model2", "female_model0", "female_model1", "female_model2"};

	private final Client client;
	private final ItemManager itemManager;

	private final CompletableFuture<Void> futureItems;

	private volatile Map<Integer, ItemRow> itemsById;
	@Getter
	private volatile Set<Integer> removedItemIds;
	@Getter
	private volatile Set<Integer> questItemIds;
	@Getter
	private volatile Set<Integer> uncommonItemIds;

	@Inject
	public ItemSheet(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
		futureItems = loadItemsAsync();
	}

	public boolean isLoadedOrRethrow()
	{
		if (futureItems.isCompletedExceptionally())
		{
			futureItems.join();
		}
		return futureItems.isDone();
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
		return CompletableFuture.runAsync(() -> {
			long startTime = System.nanoTime();
			List<ItemRow> items = new ArrayList<>();

			var is = getClass().getResourceAsStream("item_sheet.csv");
			if (is == null)
			{
				throw new RuntimeException("Failed to find item sheet");
			}
			try (BufferedReader br = new BufferedReader(new InputStreamReader(is)))
			{
				String line;
				boolean isFirstLine = true;
				while ((line = br.readLine()) != null)
				{
					if (line.startsWith("#"))
					{
						continue;
					}
					if (isFirstLine)
					{
						if (!line.equals(String.join(",", CSV_HEADERS)))
						{
							throw new IllegalArgumentException();
						}
						isFirstLine = false;
						continue;
					}
					items.add(ItemRow.fromCsvString(line));
				}
			}
			catch (IOException | NumberFormatException e)
			{
				throw new RuntimeException("Failed to parse CSV", e);
			}
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
			log.debug("ItemSheet load took {}ms", (System.nanoTime() - startTime) / 1_000_000);
		});
	}
}
