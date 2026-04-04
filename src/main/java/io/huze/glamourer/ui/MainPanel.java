package io.huze.glamourer.ui;

import io.huze.glamourer.Config;
import io.huze.glamourer.item.SearchService;
import io.huze.glamourer.party.PartyInterface;
import io.huze.glamourer.plate.Plate;
import io.huze.glamourer.plate.PlateManager;
import java.awt.CardLayout;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class MainPanel extends PluginPanel
{
	private static final String CARD_MAIN = "MAIN";
	private static final String CARD_SEARCH = "SEARCH";

	private final GlamourerPanel glamourerPanel;
	private final SearchPanel searchPanel;
	private final PlateManager plateManager;
	private final CardLayout cardLayout;

	private Plate currentSearchPlate;
	private int savedScrollPosition;

	@Inject
	public MainPanel(ClientThread clientThread, SearchService searchService,
					 ScheduledExecutorService executor, Config config,
					 PlateManager plateManager, PartyInterface partyInterface)
	{
		super(false);
		this.plateManager = plateManager;

		cardLayout = new CardLayout();
		setLayout(cardLayout);

		glamourerPanel = new GlamourerPanel(plateManager, partyInterface, config,
			this::showSearchPanelForPlate);

		searchPanel = new SearchPanel(clientThread, searchService, executor, config,
			this::onItemSelectedFromSearch, this::hideSearchPanel);

		add(glamourerPanel, CARD_MAIN);
		add(searchPanel, CARD_SEARCH);

		cardLayout.show(this, CARD_MAIN);
	}

	public void showError(Exception ex)
	{
		glamourerPanel.showError(ex);
		cardLayout.show(this, CARD_MAIN);
	}

	@Override
	public void onActivate()
	{
		glamourerPanel.onActivate();
	}

	public void showSearchPanelForPlate(Plate plate)
	{
		currentSearchPlate = plate;

		savedScrollPosition = glamourerPanel.getPlateManagerPanel().getScrollPosition();

		PlateRowPanel rowPanel = glamourerPanel.getPlateManagerPanel().findRowPanelForPlate(plate);
		if (rowPanel != null)
		{
			rowPanel.ensureExpanded();
		}

		searchPanel.setExistingItemIds(plate.getItemIds());
		searchPanel.clearSearch();
		cardLayout.show(this, CARD_SEARCH);
		searchPanel.focusSearchBar();
	}

	public void hideSearchPanel()
	{
		searchPanel.clearResults();
		cardLayout.show(this, CARD_MAIN);
		glamourerPanel.getPlateManagerPanel().setScrollPosition(savedScrollPosition);
		revalidate();
		repaint();
		currentSearchPlate = null;
	}

	private void onItemSelectedFromSearch(int itemId)
	{
		if (currentSearchPlate == null)
		{
			log.warn("No plate being edited when item selected");
			return;
		}

		final Plate searchPlate = currentSearchPlate;
		plateManager.addGlamour(searchPlate, itemId).thenRun(() -> {
			SwingUtilities.invokeLater(() -> {
				PlateRowPanel rowPanel = glamourerPanel.getPlateManagerPanel().findRowPanelForPlate(searchPlate);
				if (rowPanel != null)
				{
					rowPanel.rebuildDetailsPanel();
					rowPanel.ensureExpanded();
				}
				hideSearchPanel();
			});
		});
	}
}
