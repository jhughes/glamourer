package io.huze.glamourer.item;

import io.huze.glamourer.Sheet;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Data;

@Singleton
public class ItemEquipSheet extends Sheet<ItemEquipSheet.Row>
{
	private static final int[] EMPTY = {};
	private static final String[] CSV_HEADERS = {
		"item_id", "male_model0", "male_model1", "male_model2", "female_model0", "female_model1", "female_model2"};

	private volatile Map<Integer, Row> rowsByItemId = Map.of();

	@Inject
	public ItemEquipSheet()
	{
		super("item_equip_sheet.csv", CSV_HEADERS, Row::fromCsvColumns);
	}

	public void addModels(int itemId, Collection<Integer> modelIds)
	{
		for (int modelId : getMaleModelIds(itemId))
		{
			modelIds.add(modelId);
		}
		for (int modelId : getFemaleModelIds(itemId))
		{
			modelIds.add(modelId);
		}
	}

	@Nonnull
	public int[] getMaleModelIds(int itemId)
	{
		return getModelIds(itemId, Row::getMaleModelIds);
	}

	@Nonnull
	public int[] getFemaleModelIds(int itemId)
	{
		return getModelIds(itemId, Row::getFemaleModelIds);
	}

	@Nonnull
	private int[] getModelIds(int itemId, Function<Row, int[]> modelIds)
	{
		var row = rowsByItemId.get(itemId);
		return row == null ? EMPTY : modelIds.apply(row);
	}

	@Override
	protected void load(List<Row> rows)
	{
		this.rowsByItemId = rows.stream()
			.collect(Collectors.toMap(Row::getItemId, Function.identity(), (first, second) -> first));
	}

	@Data
	@AllArgsConstructor
	public static class Row
	{
		private static final int MODELS_PER_KIND = 3;

		final int itemId;
		final int[] maleModelIds;
		final int[] femaleModelIds;

		public static Row fromCsvColumns(String[] cols)
		{
			return new Row(Integer.parseInt(cols[0]), parseModels(cols, 1), parseModels(cols, 1 + MODELS_PER_KIND));
		}

		private static int[] parseModels(String[] cols, int startIndex)
		{
			int[] parsed = new int[MODELS_PER_KIND];
			int count = 0;
			for (int i = 0; i < MODELS_PER_KIND; i++)
			{
				var col = cols[startIndex + i];
				int modelId = col.isEmpty() ? -1 : Integer.parseInt(col);
				if (modelId >= 0)
				{
					parsed[count++] = modelId;
				}
			}
			return Arrays.copyOf(parsed, count);
		}
	}
}
