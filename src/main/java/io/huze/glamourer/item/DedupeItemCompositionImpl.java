package io.huze.glamourer.item;

import io.huze.glamourer.Extensions;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;

@Slf4j
@ExtensionMethod({Extensions.class})
public class DedupeItemCompositionImpl implements DedupeItemComposition
{
	private final ItemManager manager;
	private final ItemComposition delegate;
	private final int[] duplicates;

	DedupeItemCompositionImpl(ItemManager manager, int bestId, Set<Integer> duplicateItems)
	{
		this.manager = manager;
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

	private void forEachDuplicate(Consumer<ItemComposition> action)
	{
		for (int id : duplicates)
		{
			action.accept(manager.getItemComposition(id));
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

	@Override
	public int getInventoryModel()
	{
		return delegate.getInventoryModel();
	}

	@Nullable
	@Override
	public short[] getColorToReplace()
	{
		return delegate.getColorToReplace();
	}

	@Override
	public void setColorToReplace(final short[] colorsToReplace)
	{
		forEachDuplicate(ic -> ic.setColorToReplace(colorsToReplace));
	}

	@Nullable
	@Override
	public short[] getColorToReplaceWith()
	{
		return delegate.getColorToReplaceWith();
	}

	@Override
	public void setColorToReplaceWith(short[] colorToReplaceWith)
	{
		forEachDuplicate(ic -> ic.setColorToReplaceWith(colorToReplaceWith));
	}

	@Nullable
	@Override
	public short[] getTextureToReplace()
	{
		return delegate.getTextureToReplace();
	}

	@Override
	public void setTextureToReplace(short[] textureToFind)
	{
		forEachDuplicate(ic -> ic.setTextureToReplace(textureToFind));
	}

	@Nullable
	@Override
	public short[] getTextureToReplaceWith()
	{
		return delegate.getTextureToReplaceWith();
	}

	@Override
	public void setTextureToReplaceWith(short[] textureToReplaceWith)
	{
		forEachDuplicate(ic -> ic.setTextureToReplaceWith(textureToReplaceWith));
	}
}
