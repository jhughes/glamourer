package io.huze.glamourer.npc;

import java.util.Objects;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/// Tracks the owner of the house the player is currently in.
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class HouseTracker
{
	private final Client client;

	/// Standardized name if visiting or null if local player's house / not in a house.
	@Getter
	@Nullable
	private volatile String currentOwnerName;
	/// Standardized name of who might be visited based on the name dialog.
	@Nullable
	private String pendingOwnerName;
	private boolean enteringFromBoard;
	private boolean enteringHouse;

	/// The last house entered from the board, which is what its "Visit-Last" returns to.
	@Nullable
	private String lastBoardOwner;

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOADING:
			case LOGGED_IN:
				final String entered = consumePendingOwner();
				if (!client.getTopLevelWorldView().isInstance())
				{
					setOwner(null);
				}
				else if (entered != null && enteringHouse)
				{
					setOwner(entered);
				}
				if (event.getGameState() == GameState.LOGGED_IN)
				{
					enteringHouse = false;
				}
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				pendingOwnerName = null;
				enteringFromBoard = false;
				enteringHouse = false;
				lastBoardOwner = null;
				setOwner(null);
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onVarClientStrChanged(VarClientStrChanged event)
	{
		if (event.getIndex() == VarClientID.LAST_NAMEDIALOG)
		{
			String name = client.getVarcStrValue(VarClientID.LAST_NAMEDIALOG);
			pendingOwnerName = name == null || name.isBlank() ? null : Text.standardize(name);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getParam1() == InterfaceID.PohBoard.BUTTON)
		{
			enteringFromBoard = true;
		}
		else if ("Visit-Last".equalsIgnoreCase(event.getMenuOption()))
		{
			enteringFromBoard = true;
			pendingOwnerName = lastBoardOwner;
		}
	}

	@Nullable
	private String consumePendingOwner()
	{
		String owner = pendingOwnerName;
		if (owner != null && enteringFromBoard)
		{
			lastBoardOwner = owner;
		}
		pendingOwnerName = null;
		enteringFromBoard = false;
		return owner;
	}

	private void setOwner(@Nullable String owner)
	{
		if (!Objects.equals(owner, currentOwnerName))
		{
			currentOwnerName = owner;
			log.debug("house: {}", owner == null ? "none" : owner + "'s");
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.POH_LOADING)
		{
			enteringHouse = true;
		}
	}
}
