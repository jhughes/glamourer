package io.huze.glamourer;

import com.google.inject.Provides;
import io.huze.glamourer.glam.Glamourer;
import io.huze.glamourer.item.DedupeItemManager;
import io.huze.glamourer.item.ItemSheet;
import io.huze.glamourer.item.StackVariantSheet;
import io.huze.glamourer.plate.PlateManager;
import io.huze.glamourer.ui.ExceptionPanel;
import io.huze.glamourer.ui.MainPanel;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

@Slf4j
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
	Glamourer glamourer;
	@Inject
	PlateManager plateManager;
	@Inject
	CsvLoader csvLoader;

	NavigationButton navButton;
	PluginPanel panel;

	@Override
	protected void startUp()
	{
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
			}
			catch (Exception ex)
			{
				panel = new ExceptionPanel(ex);
				setUpNavBar();
				return true;
			}
			plateManager.loadPlates().thenRun(() -> {
				plateManager.applyAllPlates();
				try
				{
					panel = injector.getInstance(MainPanel.class);
				}
				catch (Exception ex)
				{
					panel = new ExceptionPanel(ex);
				}
				setUpNavBar();
			});
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
			else if (key.equals(Config.KEY_ICON_SCALE))
			{
				if (panel instanceof MainPanel)
				{
					SwingUtilities.invokeLater(() -> ((MainPanel) panel).onIconScaleChanged());
				}
			}
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		glamourer.revertAll();
		plateManager.loadPlates().thenRun(() -> plateManager.applyAllPlates());
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
		glamourer.revertAll();
		clientToolbar.removeNavigation(navButton);
	}

	@Provides
	Config provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(Config.class);
	}
}
