package io.huze.glamourer;

import com.google.inject.Provides;
import io.huze.glamourer.glam.GlamourEngine;
import io.huze.glamourer.item.DedupeItemManager;
import io.huze.glamourer.item.ItemSheet;
import io.huze.glamourer.item.StackVariantSheet;
import io.huze.glamourer.plate.ChangeLog;
import io.huze.glamourer.plate.PlateManager;
import io.huze.glamourer.ui.MainPanel;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

@Slf4j
@ExtensionMethod({Extensions.class})
@PluginDescriptor(
	name = "Glamourer"
)
public class Plugin extends net.runelite.client.plugins.Plugin
{
	@Inject
	Client client;
	@Inject
	ClientThread clientThread;
	@Inject
	Config config;
	@Inject
	ClientToolbar clientToolbar;
	@Inject
	ItemSheet itemSheet;
	@Inject
	StackVariantSheet stackVariantSheet;
	@Inject
	DedupeItemManager ddItemManager;
	@Inject
	PlateManager plateManager;
	@Inject
	CsvLoader csvLoader;
	@Inject
	EventBus eventBus;
	@Inject
	GlamourEngine glamourEngine;
	@Inject
	ChangeLog changeLog;

	NavigationButton navButton;
	PluginPanel panel;
	private boolean needsStarterPlate;

	@Override
	protected void startUp()
	{
		final int startUpState = client.getGameState().getState();
		eventBus.register(glamourEngine);
		panel = injector.getInstance(MainPanel.class);
		setUpNavBar();
		clientThread.invokeLater(() -> {
			if (client.getGameState().getState() < GameState.LOGIN_SCREEN.getState())
			{
				return false;
			}
			try
			{
				if (!itemSheet.isLoadedOrRethrow() || !stackVariantSheet.isLoadedOrRethrow())
				{
					return false;
				}
				csvLoader = null;
				ddItemManager.initializeOnClientThread();
				plateManager.loadPlates().thenRun(() -> {
					plateManager.reapplyAllPlates();
					if (startUpState >= GameState.LOADING.getState())
					{
						glamourEngine.backfillPlayerState();
					}
					if (plateManager.getPlates().isEmpty())
					{
						needsStarterPlate = true;
						tryCreateStarterPlate();
					}
				});
			}
			catch (Exception ex)
			{
				SwingUtilities.invokeLater(() -> ((MainPanel) panel).showError(ex));
			}
			return true;
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			tryCreateStarterPlate();
		}
	}

	private void tryCreateStarterPlate()
	{
		if (!needsStarterPlate || client.getGameState() != GameState.LOGGED_IN)
		{
			// Try again later
			return;
		}
		clientThread.invokeLater(() -> {
			if (client.getLocalPlayer() == null)
			{
				return false;
			}
			if (!plateManager.getPlates().isEmpty())
			{
				// User has created a plate, don't make a starter plate.
				needsStarterPlate = false;
				return true;
			}
			var wornItemIds = client.getWornItemIds();
			if (wornItemIds.isEmpty())
			{
				return false;
			}
			needsStarterPlate = false;
			plateManager.createStarterPlate(wornItemIds);
			return true;
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(Config.GROUP))
		{
			var key = event.getKey();
			if (key.equals(Config.KEY_NAV_PRIORITY))
			{
				setUpNavBar();
			}
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		changeLog.clear();
		try
		{
			plateManager.loadPlates().thenRun(() ->
			{
				plateManager.reapplyAllPlates();
				if (plateManager.getPlates().isEmpty())
				{
					needsStarterPlate = true;
					tryCreateStarterPlate();
				}
			});
		}
		catch (Exception ex)
		{
			SwingUtilities.invokeLater(() -> ((MainPanel) panel).showError(ex));
		}
	}

	private void setUpNavBar()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
		navButton = NavigationButton.builder()
			.tooltip("Glamourer")
			.icon(ImageUtil.loadImageResource(getClass(), "nav_icon.png"))
			.priority(config.navPriority())
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(glamourEngine);
		glamourEngine.revertAll();
		clientToolbar.removeNavigation(navButton);
	}

	@Provides
	Config provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(Config.class);
	}
}
