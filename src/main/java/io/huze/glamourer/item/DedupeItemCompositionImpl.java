package io.huze.glamourer.item;

import io.huze.glamourer.Extensions;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;

@Slf4j
@ExtensionMethod({Extensions.class})
public class DedupeItemCompositionImpl implements DedupeItemComposition
{
	private final ItemComposition delegate;
	private final int[] duplicates;

	DedupeItemCompositionImpl(ItemManager manager, int bestId, Set<Integer> duplicateItems)
	{
		if (duplicateItems == null || duplicateItems.isEmpty())
		{
			duplicateItems = Set.of(bestId);
		}
		duplicates = duplicateItems.toIntArray();
		try
		{
			this.delegate = manager.getItemComposition(bestId);
		}
		catch (Exception e)
		{
			log.error("Error loading delegate: {} {} {}", bestId, duplicateItems, e.getMessage());
			throw e;
		}
	}

	@Override
	public String getMembersName()
	{
		return delegate.getMembersName();
	}

	public Collection<Integer> getIds()
	{
		return Arrays.stream(duplicates)
			.boxed()
			.collect(Collectors.toList());
	}

	@Override
	public int getId()
	{
		return delegate.getId();
	}
}
