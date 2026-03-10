package io.huze.glamourer.item;

import io.huze.glamourer.ui.Ordering;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class SearchService
{
	private static final Comparator<SearchResult> ALPHA_COMPARATOR = Comparator.comparing(SearchResult::getName);
	private static final Comparator<SearchResult> REVERSE_ALPHA_COMPARATOR = ALPHA_COMPARATOR.reversed();

	private final Client client;
	private final DedupeItemManager itemManager;
	private final ItemSheet itemSheet;

	public List<SearchResult> search(String query, Ordering sortOrder, boolean includeQuest, boolean includeUncommon,
									 @Nullable Function<ItemComposition, Boolean> filter)
	{
		List<SearchResult> results = new ArrayList<>();
		String lowerQuery = query.trim().toLowerCase();
		Set<Integer> seenIds = new HashSet<>();
		Set<Integer> skippedIds = buildSkipSet(includeQuest, includeUncommon);

		for (int i = 0; i < client.getItemCount(); i++)
		{
			ItemComposition comp = getItemCompositionSafe(i, skippedIds);
			if (comp == null)
			{
				continue;
			}

			if (isValidResult(comp, lowerQuery, filter, seenIds))
			{
				seenIds.add(comp.getId());
				addSearchResult(results, comp);
			}
		}

		if (sortOrder == Ordering.ALPHABETICAL)
		{
			results.sort(ALPHA_COMPARATOR);
		}
		else if (sortOrder == Ordering.REVERSE_ALPHABETICAL)
		{
			results.sort(REVERSE_ALPHA_COMPARATOR);
		}

		return results;
	}

	private Set<Integer> buildSkipSet(boolean includeQuest, boolean includeUncommon)
	{
		Set<Integer> skips = new HashSet<>();
		if (!includeQuest)
		{
			skips.addAll(itemSheet.getQuestItemIds());
		}
		if (!includeUncommon)
		{
			skips.addAll(itemSheet.getUncommonItemIds());
			skips.addAll(itemSheet.getRemovedItemIds());
		}
		return skips;
	}

	private ItemComposition getItemCompositionSafe(int itemIndex, Set<Integer> skippedIds)
	{
		try
		{
			int canonical = itemManager.canonicalize(itemIndex);
			if (skippedIds.contains(canonical))
			{
				return null;
			}
			return itemManager.getItemComposition(canonical);
		}
		catch (Exception e)
		{
			log.debug("Failed to get item composition for item {}", itemIndex, e);
			return null;
		}
	}

	private boolean isValidResult(ItemComposition comp, String query,
								  @Nullable Function<ItemComposition, Boolean> filter, Set<Integer> seenIds)
	{
		return !comp.getMembersName().isEmpty()
			&& !comp.getMembersName().equals("null")
			&& !seenIds.contains(comp.getId())
			&& matchesQuery(comp, query, filter);
	}

	private void addSearchResult(List<SearchResult> results, ItemComposition comp)
	{
		try
		{
			AsyncBufferedImage image = itemManager.getImage(comp.getId());
			results.add(new SearchResult(comp, image));
		}
		catch (Exception e)
		{
			log.debug("Failed to get image for item {}", comp.getId(), e);
		}
	}

	private boolean matchesQuery(ItemComposition itemComposition, String query,
								 @Nullable Function<ItemComposition, Boolean> filter)
	{
		if (filter != null && !filter.apply(itemComposition))
		{
			return false;
		}

		String fullName = itemComposition.getMembersName().toLowerCase();
		String[] nameWords = fullName.split("\\s+");
		String[] queryWords = query.split("\\s+");

		// Each query word must match the start of at least one name word
		for (String queryWord : queryWords)
		{
			if (queryWord.isEmpty())
			{
				continue;
			}
			boolean found = false;
			for (String nameWord : nameWords)
			{
				if (nameWord.startsWith(queryWord))
				{
					found = true;
					break;
				}
			}
			if (!found)
			{
				return false;
			}
		}
		return true;
	}
}
