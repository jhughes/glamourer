package io.huze.glamourer.item;

import io.huze.glamourer.CsvLoader;
import io.huze.glamourer.Sheet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.Getter;

@Singleton
public class PetSheet extends Sheet<PetSheet.Row>
{
	@Getter
	private volatile Map<Integer, List<Integer>> npcsByItemId = Collections.emptyMap();
	private volatile Map<Integer, Integer> itemByNpcId = Collections.emptyMap();
	private volatile Map<Integer, List<Integer>> npcModelsByItemId = Collections.emptyMap();

	public PetSheet()
	{
		super("pet_sheet.csv", new String[]{"item_id", "npc_ids", "npc_models"}, PetSheet::parseRow);
	}

	public List<Integer> getNpcIds(int itemId)
	{
		return npcsByItemId.getOrDefault(itemId, Collections.emptyList());
	}

	public int getItemId(int npcId)
	{
		return itemByNpcId.getOrDefault(npcId, -1);
	}

	public List<Integer> getNpcModelIds(int itemId)
	{
		return npcModelsByItemId.getOrDefault(itemId, Collections.emptyList());
	}

	@Override
	protected void load(List<Row> rows)
	{
		Map<Integer, List<Integer>> npcs = new HashMap<>();
		Map<Integer, Integer> item = new HashMap<>();
		Map<Integer, List<Integer>> models = new HashMap<>();
		for (Row row : rows)
		{
			npcs.put(row.itemId, row.npcIds);
			for (int npcId : row.npcIds)
			{
				item.put(npcId, row.itemId);
			}
			if (!row.npcModels.isEmpty())
			{
				models.put(row.itemId, row.npcModels);
			}
		}
		this.npcsByItemId = Collections.unmodifiableMap(npcs);
		this.itemByNpcId = Collections.unmodifiableMap(item);
		this.npcModelsByItemId = Collections.unmodifiableMap(models);
	}

	private static Row parseRow(String[] cols)
	{
		return new Row(Integer.parseInt(cols[0]),
			CsvLoader.parseList(cols[1], Integer::parseInt),
			CsvLoader.parseList(cols[2], Integer::parseInt));
	}

	static final class Row
	{
		private final int itemId;
		private final List<Integer> npcIds;
		private final List<Integer> npcModels;

		Row(int itemId, List<Integer> npcIds, List<Integer> npcModels)
		{
			this.itemId = itemId;
			this.npcIds = npcIds;
			this.npcModels = npcModels;
		}
	}
}
