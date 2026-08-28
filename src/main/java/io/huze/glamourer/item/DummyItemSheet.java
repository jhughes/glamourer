package io.huze.glamourer.item;

import io.huze.glamourer.Sheet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;

/// Maps dummy items to the real item they visually match.
@Singleton
public class DummyItemSheet extends Sheet<int[]>
{
	private volatile Map<Integer, Integer> dummyToRealId = Collections.emptyMap();

	public DummyItemSheet()
	{
		super("dummy_item_sheet.csv", new String[]{"dummyItemId", "itemId"},
			cols -> new int[]{Integer.parseInt(cols[0]), Integer.parseInt(cols[1])});
	}

	public int getItemIdForVisibleItemId(int visibleItemId)
	{
		return dummyToRealId.getOrDefault(visibleItemId, visibleItemId);
	}

	@Override
	protected void load(List<int[]> rows)
	{
		Map<Integer, Integer> map = new HashMap<>();
		for (int[] row : rows)
		{
			map.put(row[0], row[1]);
		}
		this.dummyToRealId = map;
	}
}
