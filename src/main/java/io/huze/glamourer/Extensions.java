package io.huze.glamourer;

import io.huze.glamourer.item.DedupeItemManager;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.gameval.InventoryID;

public class Extensions
{
	@Nullable
	public static short[] nullableClone(@Nullable short[] a)
	{
		return a == null ? null : a.clone();
	}

	public static <T extends Collection<Integer>> int[] toIntArray(T collection)
	{
		int[] array = new int[collection.size()];
		int i = 0;
		for (int x : collection)
		{
			array[i++] = x;
		}
		return array;
	}

	public static <T extends Collection<Short>> short[] toShortArray(T collection)
	{
		short[] array = new short[collection.size()];
		int i = 0;
		for (short s : collection)
		{
			array[i++] = s;
		}
		return array;
	}

	public static String toHex(short[] array)
	{
		if (array == null)
		{
			return "";
		}

		StringBuilder sb = new StringBuilder(array.length * 4);
		for (short s : array)
		{
			sb.append(String.format("%04x", s & 0xFFFF));
		}
		return sb.toString();
	}

	public static String toHex(Collection<Short> collection)
	{
		if (collection == null)
		{
			return "";
		}

		StringBuilder sb = new StringBuilder(collection.size() * 4);
		for (short s : collection)
		{
			sb.append(String.format("%04x", s & 0xFFFF));
		}
		return sb.toString();
	}

	public static Set<Integer> getWornItemIds(Client client)
	{
		Set<Integer> wornIds = new HashSet<>();

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null && localPlayer.getPlayerComposition() != null)
		{
			int[] equipmentIds = localPlayer.getPlayerComposition().getEquipmentIds();
			for (int equipmentId : equipmentIds)
			{
				if (equipmentId >= PlayerComposition.ITEM_OFFSET)
				{
					int itemId = equipmentId - PlayerComposition.ITEM_OFFSET;
					wornIds.add(itemId);
				}
			}
		}

		// This is redundant with the previous block unless the user is using an item replacement plugin.
		// If the user is replacing items, this will include both the worn item and its replacement.
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment != null)
		{
			for (Item item : equipment.getItems())
			{
				if (item.getId() >= 0)
				{
					wornIds.add(item.getId());
				}
			}
		}
		return wornIds;
	}

	public static Set<Integer> getLocalPlayerItemIds(Client client)
	{
		var playerItemIds = getWornItemIds(client);
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				if (item.getId() >= 0)
				{
					playerItemIds.add(item.getId());
				}
			}
		}
		return playerItemIds;
	}

	public static Set<Integer> getLocalPlayerEquippableItemIds(Client client, DedupeItemManager itemManager)
	{
		var playerItemIds = getWornItemIds(client);
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				if (item.getId() >= 0 && itemManager.isEquippable(item.getId()))
				{
					playerItemIds.add(item.getId());
				}
			}
		}
		return playerItemIds;
	}
}
