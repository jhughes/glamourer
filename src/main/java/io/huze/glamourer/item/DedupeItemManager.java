package io.huze.glamourer.item;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
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
		var comp = dedupeMap.get(itemId);
		return comp != null ? comp : new DedupeItemCompositionImpl(itemManager, itemId, null);
	}

	@Nullable
	public DedupeItemComposition getItemComposition(@Nonnull String dedupeKey)
	{
		var itemId = dedupeKeyToBestItemMap.get(dedupeKey);
		if (itemId == null)
		{
			return null;
		}
		return getItemComposition(itemId);
	}

	public ItemComposition getItemDefinition(int itemId)
	{
		return client.getItemDefinition(itemId);
	}

	@Nullable
	public String findKeyIgnoreCase(@Nonnull String dedupeKey)
	{
		for (String key : dedupeKeyToBestItemMap.keySet())
		{
			if (key.equalsIgnoreCase(dedupeKey))
			{
				return key;
			}
		}
		return null;
	}

	public Set<Integer> getMatchingItemIdsForCorruptKey(String dedupeKey)
	{
		var split = dedupeKey.split(":");
		if (split.length < 2)
		{
			return Collections.emptySet();
		}
		var keyPrefix = split[0] + ":" + split[1] + ":";
		return dedupeKeyToBestItemMap.entrySet().stream()
			// Ignore case so a re-cased item name finds repair candidates.
			.filter(entry -> entry.getKey().regionMatches(true, 0, keyPrefix, 0, keyPrefix.length()))
			.map(Map.Entry::getValue)
			.collect(Collectors.toSet());
	}

	public boolean isEquippable(int itemId)
	{
		var stats = itemManager.getItemStats(itemId);
		return stats != null && stats.isEquipable();
	}

	private boolean filterItem(ItemComposition itemComposition)
	{
		var name = itemComposition.getMembersName();
		return itemComposition.getNote() != -1 ||
			itemComposition.getPlaceholderTemplateId() != -1 ||
			name == null ||
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

	private boolean initialized;

	public void initializeOnClientThread()
	{
		if (initialized)
		{
			return;
		}
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
		dedupeKeyToBestItemMap.clear();
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
					dedupeMap.put(itemId, new DedupeItemCompositionImpl(itemManager, bestId, dupeIds));
				}
			}
		}
		initialized = true;
		log.debug("DedupeItemManager initialization took {}ms", (System.nanoTime() - startTime) / 1_000_000);
	}
}
