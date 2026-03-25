package io.huze.glamourer.item;

import io.huze.glamourer.Extensions;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.IterableHashTable;
import net.runelite.api.Node;
import net.runelite.client.game.ItemManager;

@Slf4j
@ExtensionMethod({Extensions.class})
public class DedupeItemComposition implements ItemComposition
{
	private final ItemManager manager;
	private final ItemComposition delegate;
	private final int[] duplicates;

	DedupeItemComposition(ItemManager manager, int bestId, Set<Integer> duplicateItems)
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
	public String getName()
	{
		return delegate.getName();
	}

	@Override
	public String getMembersName()
	{
		return delegate.getMembersName();
	}

	@Override
	public void setName(String name)
	{
		throw new UnsupportedOperationException();
	}

	public Collection<Integer> getIds()
	{
		return java.util.Arrays.stream(duplicates)
			.boxed()
			.collect(Collectors.toList());
	}

	@Deprecated(since = "Don't serialize this because the dedupe item comp has many potential IDs.\nNot actually deprecated; this just helps see usages in the IDE.")
	@Override
	public int getId()
	{
		return delegate.getId();
	}

	@Override
	public int getNote()
	{
		return delegate.getNote();
	}

	@Override
	public int getLinkedNoteId()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public int getPlaceholderId()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public int getPlaceholderTemplateId()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public int getPrice()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public int getHaPrice()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isMembers()
	{
		return delegate.isMembers();
	}

	@Override
	public boolean isStackable()
	{
		return delegate.isStackable();
	}

	@Override
	public boolean isTradeable()
	{
		return delegate.isTradeable();
	}

	@Override
	public String[] getInventoryActions()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public String[][] getSubops()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public int getShiftClickActionIndex()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void setShiftClickActionIndex(int shiftClickActionIndex)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public int getInventoryModel()
	{
		return delegate.getInventoryModel();
	}

	@Override
	public void setInventoryModel(int model)
	{
		forEachDuplicate(ic -> ic.setInventoryModel(model));
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

	@Override
	public int getXan2d()
	{
		return delegate.getXan2d();
	}

	@Override
	public int getYan2d()
	{
		return delegate.getYan2d();
	}

	@Override
	public int getZan2d()
	{
		return delegate.getZan2d();
	}

	@Override
	public void setXan2d(int angle)
	{
		forEachDuplicate(ic -> ic.setXan2d(angle));
	}

	@Override
	public void setYan2d(int angle)
	{
		forEachDuplicate(ic -> ic.setYan2d(angle));
	}

	@Override
	public void setZan2d(int angle)
	{
		forEachDuplicate(ic -> ic.setZan2d(angle));
	}

	@Override
	public int getAmbient()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public int getContrast()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public IterableHashTable<Node> getParams()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public int getIntValue(int paramID)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void setValue(int paramID, int value)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public String getStringValue(int paramID)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void setValue(int paramID, String value)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public long getLongValue(int paramID)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void setValue(int paramID, long value)
	{
		throw new UnsupportedOperationException();
	}
}
