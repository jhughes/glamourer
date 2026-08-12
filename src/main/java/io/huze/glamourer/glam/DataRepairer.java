package io.huze.glamourer.glam;

import io.huze.glamourer.item.DedupeItemManager;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataRepairer
{
	public static GlamourData tryRepairOrThrow(GlamourEngine engine, DedupeItemManager itemManager, @Nonnull GlamourData data)
	{
		assert data.getItemKey() != null;

		// Fix items whose names have been re-cased by game update.
		var recasedKey = itemManager.findKeyIgnoreCase(data.getItemKey());
		if (recasedKey != null)
		{
			log.info("Repaired corrupt key '{}' as re-cased '{}'", data.getItemKey(), recasedKey);
			return data.withNewKey(recasedKey);
		}

		// Fix items whose keys were corrupted by old glamourer's race condition.
		var itemIds = itemManager.getMatchingItemIdsForCorruptKey(data.getItemKey());
		log.info("Repairing corrupt key '{}' with {} potential items. Scores:", data.getItemKey(), itemIds.size());
		var bestScore = -1;
		PrimedItem bestItem = null;
		for (var itemId : itemIds)
		{
			var primedItem = engine.getPrimedItem(itemId);
			var score = computeColorMatchScore(data, primedItem);
			log.info("\t{} : {}", primedItem.getItemComposition().getMembersName(), score);
			if (score > bestScore)
			{
				bestItem = primedItem;
				bestScore = score;
			}
		}

		///  TODO In the future, we could give the user an option to choose what item to use.
		if (bestScore > 0)
		{
			log.info("Repaired corrupt key '{}' with '{}'", data.getItemKey(), bestItem.getDedupeKey());
			return data.withNewKey(bestItem.getDedupeKey());
		}
		throw new IllegalArgumentException("Unable to repair " + data.getItemKey());
	}

	static int computeColorMatchScore(GlamourData data, PrimedItem item)
	{
		int matchScore = 0;
		for (var replacement : data.getColorReplacements())
		{
			for (var primedReplacement : item.getColorReplacements())
			{
				if (replacement.getModel() != null && replacement.getModel() == primedReplacement.getOriginal())
				{
					matchScore++;
				}
				if (replacement.getOriginal() == primedReplacement.getReplacement())
				{
					matchScore++;
				}
			}
		}
		return matchScore;
	}
}
