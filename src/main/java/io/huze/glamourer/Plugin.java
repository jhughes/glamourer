package io.huze.glamourer;

import com.google.inject.Provides;
import io.huze.glamourer.glam.GlamourEngine;
import io.huze.glamourer.glam.Glamourer;
import io.huze.glamourer.glam.IconService;
import io.huze.glamourer.party.PartyInterface;
import io.huze.glamourer.party.PlayerBlacklist;
import io.huze.glamourer.item.DummyItemSheet;
import io.huze.glamourer.item.DedupeItemManager;
import io.huze.glamourer.item.ItemEquipSheet;
import io.huze.glamourer.item.ItemRenderSheet;
import io.huze.glamourer.item.ItemSheet;
import io.huze.glamourer.item.PetSheet;
import io.huze.glamourer.item.StackVariantSheet;
import io.huze.glamourer.plate.ChangeLog;
import io.huze.glamourer.plate.PlateManager;
import io.huze.glamourer.npc.PetGlamourSync;
import io.huze.glamourer.npc.NpcGlamourer;
import io.huze.glamourer.npc.NpcSheet;
import io.huze.glamourer.texture.SamplableTextureProvider;
import io.huze.glamourer.ui.ItemGlamourWindows;
import io.huze.glamourer.ui.MainPanel;
import io.huze.glamourer.ui.colorpicker.ColorDisplay;
import javax.inject.Inject;
import javax.swing.JOptionPane;
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
	ItemRenderSheet itemRenderSheet;
	@Inject
	ItemEquipSheet itemEquipSheet;
	@Inject
	PetSheet petSheet;
	@Inject
	StackVariantSheet stackVariantSheet;
	@Inject
	DummyItemSheet dummyItemSheet;
	@Inject
	NpcSheet npcSheet;
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
	Glamourer glamourer;
	@Inject
	PartyInterface partyInterface;
	@Inject
	PlayerBlacklist playerBlacklist;
	@Inject
	ChangeLog changeLog;
	@Inject
	IconService iconService;
	@Inject
	NpcGlamourer npcGlamourer;
	@Inject
	PetGlamourSync petGlamourSync;
	@Inject
	ItemGlamourWindows itemGlamourWindows;
	@Inject
	SamplableTextureProvider textureProvider;
	NavigationButton navButton;
	MainPanel panel;
	volatile boolean isStarted;

	@Override
	protected void startUp()
	{
		isStarted = true;
		final int startUpState = client.getGameState().getState();
		eventBus.register(glamourEngine);
		panel = injector.getInstance(MainPanel.class);
		setUpNavBar();
		applyShadeIntensity();
		clientThread.invokeLater(() -> {
			if (!isStarted)
			{
				return true;
			}
			if (client.getGameState().getState() < GameState.LOGIN_SCREEN.getState())
			{
				return false;
			}
			textureProvider.loadIfStale();
			try
			{
				if (!itemSheet.isLoadedOrRethrow()
					|| !itemRenderSheet.isLoadedOrRethrow()
					|| !itemEquipSheet.isLoadedOrRethrow()
					|| !petSheet.isLoadedOrRethrow()
					|| !stackVariantSheet.isLoadedOrRethrow()
					|| !dummyItemSheet.isLoadedOrRethrow()
					|| !npcSheet.isLoadedOrRethrow())
				{
					return false;
				}
				csvLoader = null;
				ddItemManager.initializeOnClientThread();
				plateManager.loadPlates().thenRun(() -> {
					if (!isStarted)
					{
						return;
					}
					plateManager.reapplyAllPlates();
					changeLog.setOnGlamourChanged(itemIds -> {
						if (itemIds.isEmpty())
						{
							partyInterface.sendUpdate();
						}
						else
						{
							partyInterface.sendPatch(itemIds);
						}
					});
					partyInterface.startUp();
					eventBus.register(partyInterface);
					if (startUpState >= GameState.LOADING.getState())
					{
						glamourEngine.backfillPlayerState();
					}
					tryCreateStarterPlate();
					promptOrphanRecovery();
				}).exceptionally(ex -> {
					log.error("Plugin startup failed", ex);
					return null;
				});
			}
			catch (Exception ex)
			{
				SwingUtilities.invokeLater(() -> panel.showError(ex));
			}
			return true;
		});

		npcGlamourer.startUp();
		petGlamourSync.startUp();
		glamourEngine.setOnGlamoursChanged(petGlamourSync::sync);
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
		if (!plateManager.isStarterPlateNeeded() || client.getGameState() != GameState.LOGGED_IN)
		{
			// Try again later
			return;
		}
		clientThread.invokeLater(() -> {
			if (!plateManager.isStarterPlateNeeded())
			{
				return true;
			}
			if (client.getLocalPlayer() == null)
			{
				return false;
			}
			if (!plateManager.getPlates().isEmpty())
			{
				// User has created a plate, don't make a starter plate.
				return true;
			}
			var wornItemIds = client.getWornItemIds();
			if (wornItemIds.isEmpty())
			{
				return false;
			}
			plateManager.createStarterPlate(wornItemIds);
			return true;
		});
	}

	private void promptOrphanRecovery()
	{
		final int count = plateManager.getOrphanedPlateCount();
		if (count <= 0)
		{
			return;
		}
		var pluralPlates = count + " misplaced Glamour plate" + (count == 1 ? "" : "s");
		SwingUtilities.invokeLater(() -> {
			String[] options = {"Yes", "No", "Delete permanently"};
			int result = JOptionPane.showOptionDialog(
				null,
				"Glamourer has found " + pluralPlates + " in your configuration.\n" +
					"Would you like to recover " + pluralPlates + "?",
				"Glamourer",
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null,
				options,
				options[0]
			);
			if (result == 0)
			{
				clientThread.invokeLater(() -> {
					plateManager.recoverOrphanedPlates();
				});
			}
			else if (result == 2)
			{
				clientThread.invokeLater(() -> {
					plateManager.discardOrphanedPlates();
				});
			}
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(Config.GROUP))
		{
			var key = event.getKey();
			if (key.equals(Config.KEY_NAV_PRIORITY) || key.equals(Config.KEY_HIDE_SIDEBAR_ICON))
			{
				setUpNavBar();
			}
			if (key.equals(Config.KEY_ICON_SCALE))
			{
				iconService.clearCache();
			}
			if (key.equals(Config.KEY_SHADE_INTENSITY))
			{
				applyShadeIntensity();
			}
		}
	}

	private void applyShadeIntensity()
	{
		SwingUtilities.invokeLater(() -> ColorDisplay.setLightIntensity(config.shadeIntensity() / 100.0));
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		changeLog.clear();
		playerBlacklist.load();
		try
		{
			plateManager.loadPlates().thenRun(() ->
			{
				plateManager.reapplyAllPlates();
				tryCreateStarterPlate();
				promptOrphanRecovery();
			});
		}
		catch (Exception ex)
		{
			SwingUtilities.invokeLater(() -> panel.showError(ex));
		}
	}

	private void setUpNavBar()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (config.hideSidebarIcon())
		{
			return;
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
		isStarted = false;
		SwingUtilities.invokeLater(itemGlamourWindows::closeAll);
		changeLog.setOnGlamourChanged(itemIds -> {});
		changeLog.setOnUndoRedoAvailabilityChanged(() -> {});
		plateManager.setOnPlatesChanged(ignored -> {});
		partyInterface.setOnStateChanged(() -> {});
		changeLog.clear();
		glamourEngine.setOnGlamoursChanged(() -> {});
		petGlamourSync.shutDown();
		npcGlamourer.shutDown();
		eventBus.unregister(glamourEngine);
		eventBus.unregister(partyInterface);
		partyInterface.shutDown();
		glamourEngine.revertAll();
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}
	}

	@Provides
	Config provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(Config.class);
	}
}
