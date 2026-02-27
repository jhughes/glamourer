package io.huze.glamourer.item;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.kit.KitType;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class DedupeItemManager
{
	private final ItemManager itemManager;
	private final Client client;
	private final StackVariantSheet stackVariantSheet;
	private final Map<String, Integer> dedupeKeyToBestItemMap = new HashMap<>();
	private final Map<Integer, DedupeItemComposition> dedupeMap = new HashMap<>();

	@Nonnull
	public DedupeItemComposition getItemComposition(int itemId)
	{
		return dedupeMap.getOrDefault(itemId, new DedupeItemComposition(itemManager, itemId, null));
	}

	public DedupeItemComposition getItemComposition(String dedupeKey)
	{
		return getItemComposition(dedupeKeyToBestItemMap.get(dedupeKey));
	}

	public ItemComposition getItemDefinition(int itemId)
	{
		return client.getItemDefinition(itemId);
	}

	private boolean filterItem(ItemComposition itemComposition)
	{
		var name = itemComposition.getMembersName();
		return name == null ||
			name.isBlank() ||
			name.equalsIgnoreCase("null");
	}

	public int canonicalize(int id)
	{
		return itemManager.canonicalize(id);
	}

	public AsyncBufferedImage getImage(int id)
	{
		return itemManager.getImage(id);
	}

	private static class DupeItem
	{
		ItemComposition best;
		Set<Integer> dupeIds;

		DupeItem(ItemComposition composition)
		{
			best = composition;
			dupeIds = new HashSet<>();
		}

		void add(ItemComposition item)
		{
			dupeIds.add(item.getId());
			var name = item.getMembersName();
			var bestName = best.getMembersName();
			var nameIsShorter = name.length() < bestName.length();
			var nameIsGreater = name.length() == bestName.length() && name.compareTo(bestName) > 0;
			if (nameIsShorter || nameIsGreater)
			{
				best = item;
			}
		}
	}

	public void initializeOnClientThread()
	{
		long startTime = System.nanoTime();
		Map<String, DupeItem> dupeItemMap = new HashMap<>();
		for (int i = 0; i < client.getItemCount(); i++)
		{
			var itemComposition = itemManager.getItemComposition(i);
			if (filterItem(itemComposition))
			{
				continue;
			}
			var key = DedupeKey.of(itemComposition);
			dupeItemMap.computeIfAbsent(key, k -> new DupeItem(itemComposition)).add(itemComposition);
		}

		dedupeMap.clear();
		for (var dupeEntry : dupeItemMap.values())
		{
			var bestId = dupeEntry.best.getId();
			dedupeKeyToBestItemMap.put(DedupeKey.of(dupeEntry.best), bestId);
			var dupeIds = dupeEntry.dupeIds;

			Set<Integer> variantIds = new HashSet<>();
			for (int dupeId : dupeIds)
			{
				variantIds.addAll(stackVariantSheet.getVariants(dupeId));
			}
			dupeIds.addAll(variantIds);

			if (dupeIds.size() > 1)
			{
				for (var itemId : dupeIds)
				{
					dedupeMap.put(itemId, new DedupeItemComposition(itemManager, bestId, dupeIds));
				}
			}
		}
		log.debug("DedupeItemManager initialization took {}ms", (System.nanoTime() - startTime) / 1_000_000);
	}
}
