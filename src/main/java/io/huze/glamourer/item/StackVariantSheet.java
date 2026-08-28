package io.huze.glamourer.item;

import io.huze.glamourer.Sheet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;

@Singleton
public class StackVariantSheet extends Sheet<int[]>
{
	private volatile Map<Integer, Set<Integer>> variantsByItemId = Collections.emptyMap();

	public StackVariantSheet()
	{
		super("stack_variant_sheet.csv", new String[]{"id", "variantId"},
			cols -> new int[]{Integer.parseInt(cols[0]), Integer.parseInt(cols[1])});
	}

	public Set<Integer> getVariants(int itemId)
	{
		return variantsByItemId.getOrDefault(itemId, Collections.emptySet());
	}

	@Override
	protected void load(List<int[]> rows)
	{
		Map<Integer, Set<Integer>> map = new HashMap<>();
		for (int[] row : rows)
		{
			map.computeIfAbsent(row[0], k -> new HashSet<>()).add(row[1]);
		}
		this.variantsByItemId = map;
	}
}
