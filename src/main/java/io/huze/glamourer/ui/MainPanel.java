package io.huze.glamourer.ui;

import io.huze.glamourer.Config;
import io.huze.glamourer.glam.GlamourConflictException;
import io.huze.glamourer.glam.Glamourer;
import io.huze.glamourer.item.SearchService;
import io.huze.glamourer.plate.Plate;
import io.huze.glamourer.plate.PlateManager;
import java.awt.CardLayout;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class MainPanel extends PluginPanel
{
	private static final String CARD_PLATES = "PLATES";
	private static final String CARD_SEARCH = "SEARCH";

	private final PlateManagerPanel plateManagerPanel;
	private final SearchPanel searchPanel;
	private final Glamourer glamourer;
	private final ClientThread clientThread;
	private final PlateManager plateManager;
	private final CardLayout cardLayout;

	private Plate currentSearchPlate;
	private int savedScrollPosition;

	@Inject
	public MainPanel(Glamourer glamourer, ClientThread clientThread,
					 SearchService searchService, ScheduledExecutorService executor,
					 Config config, PlateManager plateManager)
	{
		super(false);
		this.glamourer = glamourer;
		this.clientThread = clientThread;
		this.plateManager = plateManager;

		// Use CardLayout to switch between plates and search
		cardLayout = new CardLayout();
		setLayout(cardLayout);

		// Create plate manager panel with add item request callback
		plateManagerPanel = new PlateManagerPanel(clientThread, plateManager, glamourer, config, this::showSearchPanelForPlate);

		// Create search panel with item selection callback
		searchPanel = new SearchPanel(clientThread, searchService, executor, config,
			this::onItemSelectedFromSearch, this::hideSearchPanel);

		add(plateManagerPanel, CARD_PLATES);
		add(searchPanel, CARD_SEARCH);

		// Show plates by default
		cardLayout.show(this, CARD_PLATES);
	}

	public void onIconScaleChanged()
	{
		plateManagerPanel.rebuildPlatesSection();
	}

	public void showSearchPanelForPlate(Plate plate)
	{
		currentSearchPlate = plate;

		// Save scroll position before switching
		savedScrollPosition = plateManagerPanel.getScrollPosition();

		// Expand the plate row before switching
		PlateRowPanel rowPanel = plateManagerPanel.findRowPanelForPlate(plate);
		if (rowPanel != null)
		{
			rowPanel.setExpanded(true);
		}

		searchPanel.setExistingItemIds(plate.getItemIds());
		searchPanel.clearSearch();
		cardLayout.show(this, CARD_SEARCH);
		searchPanel.focusSearchBar();
	}

	public void hideSearchPanel()
	{
		searchPanel.clearResults();
		cardLayout.show(this, CARD_PLATES);
		plateManagerPanel.setScrollPosition(savedScrollPosition);
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

		clientThread.invokeLater(() -> {
			try
			{
				currentSearchPlate.addGlamour(glamourer, itemId);
			}
			catch (GlamourConflictException ex)
			{
				DialogUtil.showErrorDialogNearCursor(SwingUtilities.windowForComponent(this), "This item conflicts with another plate.\nThis plate will be disabled.", "Glamour Conflict");
			}

			SwingUtilities.invokeLater(() -> {
				// Rebuild the row panel and hide search
				PlateRowPanel rowPanel = plateManagerPanel.findRowPanelForPlate(currentSearchPlate);
				if (rowPanel != null)
				{
					rowPanel.rebuildDetailsPanel();
					rowPanel.setExpanded(true);
				}
				hideSearchPanel();
			});
		});
	}
}
