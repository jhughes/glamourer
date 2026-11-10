package io.huze.glamourer.glam;

import io.huze.glamourer.item.DummyItemSheet;
import io.huze.glamourer.item.ItemSpotAnimSheet;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.events.GraphicChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class SpotAnimGlamourer
{
	private final Client client;
	private final EventBus eventBus;
	private final GlamourEngine glamourEngine;
	private final DummyItemSheet dummyItemSheet;
	private final ItemSpotAnimSheet itemSpotAnimSheet;

	public void startUp()
	{
		eventBus.register(this);
	}

	public void shutDown()
	{
		eventBus.unregister(this);
	}

	/// -1 priority so transmog plugins have swapped the player's kit before it is read.
	@Subscribe(priority = -1)
	public void onGraphicChanged(GraphicChanged event)
	{
		if (!(event.getActor() instanceof Player))
		{
			return;
		}
		var player = (Player) event.getActor();
		List<ActorSpotAnim> replaced = null;
		for (ActorSpotAnim spotAnim : player.getSpotAnims())
		{
			if (spawnStandIn(player, spotAnim))
			{
				if (replaced == null)
				{
					replaced = new ArrayList<>();
				}
				replaced.add(spotAnim);
			}
		}
		if (replaced != null)
		{
			for (ActorSpotAnim spotAnim : replaced)
			{
				player.removeSpotAnim((int) spotAnim.getHash());
			}
		}
	}

	private boolean spawnStandIn(@Nonnull Player player, @Nonnull ActorSpotAnim spotAnim)
	{
		var definition = findWornDefinition(player, itemSpotAnimSheet.getRows(spotAnim.getId()));
		if (definition == null)
		{
			return false;
		}
		var glamour = glamourEngine.findGlamourFor(player, definition.getItemId());
		if (glamour == null)
		{
			return false;
		}
		boolean spawned = GlamouredSpotAnim.spawn(client, player, spotAnim, definition, glamour);
		if (!spawned)
		{
			log.debug("No model or animation for spotanim {} of item {}", spotAnim.getId(), definition.getItemId());
		}
		return spawned;
	}

	@Nullable
	private ItemSpotAnimSheet.Row findWornDefinition(@Nonnull Player player, @Nonnull List<ItemSpotAnimSheet.Row> rows)
	{
		if (rows.isEmpty())
		{
			return null;
		}
		var composition = player.getPlayerComposition();
		if (composition == null)
		{
			return null;
		}
		for (var row : rows)
		{
			if (isWearing(composition, row.getItemId()))
			{
				return row;
			}
		}
		return null;
	}

	private boolean isWearing(@Nonnull PlayerComposition composition, int itemId)
	{
		for (int equipmentId : composition.getEquipmentIds())
		{
			if (equipmentId >= PlayerComposition.ITEM_OFFSET
				&& dummyItemSheet.getItemIdForVisibleItemId(equipmentId - PlayerComposition.ITEM_OFFSET) == itemId)
			{
				return true;
			}
		}
		return false;
	}
}
