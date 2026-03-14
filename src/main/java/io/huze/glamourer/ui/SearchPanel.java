package io.huze.glamourer.ui;

import com.google.common.base.Strings;
import io.huze.glamourer.Config;
import io.huze.glamourer.item.SearchService;
import io.huze.glamourer.item.SearchResult;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BoxLayout;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.SwingUtil;

@Slf4j
class SearchPanel extends JPanel
{
	private static final String CARD_INFO = "INFO";
	private static final String CARD_RESULTS = "RESULTS";
	private static final int DEBOUNCE_MS = 200;

	private final ClientThread clientThread;
	private final SearchService searchService;
	private final ScheduledExecutorService executor;
	private final Consumer<Integer> onItemSelected;
	private final Config config;

	private final IconTextField searchField;
	private final VerticalScrollPane scrollPane;
	private final VerticalScrollPane.ScrollableContainer resultsContainer;
	private final CardLayout cards;
	private final JPanel cardPanel;
	private final PluginErrorPanel infoPanel;
	private final AtomicBoolean searching;
	private final Runnable onCancel;

	private Future<?> pendingSearch;
	private Ordering ordering;
	private boolean includeQuest;
	private boolean includeUncommon;
	private Set<Integer> alreadyAddedIds;

	@Inject
	public SearchPanel(ClientThread clientThread, SearchService searchService, ScheduledExecutorService executor, Config config, Consumer<Integer> onItemSelected, Runnable onCancel)
	{
		this.clientThread = clientThread;
		this.searchService = searchService;
		this.executor = executor;
		this.config = config;
		this.onItemSelected = onItemSelected;
		this.onCancel = onCancel;

		this.ordering = Ordering.ALPHABETICAL;
		this.includeQuest = false;
		this.includeUncommon = false;
		this.alreadyAddedIds = Collections.emptySet();
		this.searching = new AtomicBoolean(false);

		this.searchField = new IconTextField();
		this.infoPanel = new PluginErrorPanel();
		this.cards = new CardLayout();
		this.cardPanel = new JPanel(cards);

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		configureSearchField();
		scrollPane = new VerticalScrollPane();
		resultsContainer = scrollPane.getContainer();
		buildCardPanel();
		buildHeader();

		// Handle escape key to close the panel
		searchField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE && onCancel != null)
				{
					onCancel.run();
				}
			}
		});

		SwingUtilities.invokeLater(searchField::requestFocusInWindow);
	}

	public void focusSearchBar()
	{
		SwingUtilities.invokeLater(searchField::requestFocusInWindow);
	}

	public void setExistingItemIds(Set<Integer> ids)
	{
		alreadyAddedIds = ids != null ? ids : Collections.emptySet();
	}

	public void clearSearch()
	{
		if (!Strings.isNullOrEmpty(searchField.getText()))
		{
			searchField.setText("");
			// Document listener will call triggerSearch()
		}
		else
		{
			// Text is already empty, manually trigger to show player items
			triggerSearch();
		}
	}

	public void clearResults()
	{
		SwingUtil.fastRemoveAll(resultsContainer);
	}

	private void configureSearchField()
	{
		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setPreferredSize(new Dimension(100, 30));
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchField.addActionListener(e -> triggerSearch());
		searchField.addClearListener(this::clearSearch);
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				triggerSearch();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				triggerSearch();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				triggerSearch();
			}
		});
	}

	private void buildCardPanel()
	{
		JPanel errorWrapper = new JPanel(new BorderLayout());
		errorWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		infoPanel.setContent("Item Search", "Search for items to add to your plate.");
		errorWrapper.add(infoPanel, BorderLayout.NORTH);

		cardPanel.add(scrollPane, CARD_RESULTS);
		cardPanel.add(errorWrapper, CARD_INFO);
		cards.show(cardPanel, CARD_INFO);

		add(cardPanel, BorderLayout.CENTER);
	}

	private void buildHeader()
	{
		JPanel headerWrapper = new JPanel(new BorderLayout());
		headerWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Title bar at the top
		JPanel titlePanel = new JPanel(new BorderLayout());
		titlePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		titlePanel.setBorder(new EmptyBorder(8, 6, 4, 4));

		JLabel titleLabel = new JLabel("Glamourer Search");
		titleLabel.setForeground(Color.WHITE);
		titlePanel.add(titleLabel, BorderLayout.WEST);

		JButton closeButton = new JButton();
		ImageIcons.setCloseIcon(closeButton);
		closeButton.setToolTipText("Close search");
		closeButton.addActionListener(e -> {
			if (onCancel != null)
			{
				onCancel.run();
			}
		});
		titlePanel.add(closeButton, BorderLayout.EAST);
		headerWrapper.add(titlePanel, BorderLayout.NORTH);

		// Search controls
		JPanel controlsPanel = new JPanel();
		controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
		controlsPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		controlsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		controlsPanel.add(createSortRow());
		controlsPanel.add(createQuestFilterRow());
		controlsPanel.add(createUncommonFilterRow());
		controlsPanel.add(createSearchRow());

		headerWrapper.add(controlsPanel, BorderLayout.CENTER);

		add(headerWrapper, BorderLayout.NORTH);
	}

	private JPanel createSearchRow()
	{
		JPanel row = new JPanel(new BorderLayout(5, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.add(searchField, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private JPanel createSortRow()
	{
		JPanel row = new JPanel(new GridLayout(1, 2));
		row.setBorder(new EmptyBorder(5, 0, 0, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel label = new JLabel("Ordering");
		label.setForeground(Color.WHITE);
		label.setMaximumSize(new Dimension(0, 0));
		label.setPreferredSize(new Dimension(0, 0));
		row.add(label);

		JComboBox<Ordering> combo = new JComboBox<>(Ordering.values());
		combo.setSelectedItem(ordering);
		combo.setPreferredSize(new Dimension(combo.getPreferredSize().width, 25));
		combo.setForeground(Color.WHITE);
		combo.setFocusable(false);
		combo.addItemListener(e -> {
			if (e.getStateChange() == ItemEvent.SELECTED)
			{
				ordering = (Ordering) combo.getSelectedItem();
				triggerSearch();
			}
		});
		row.add(combo);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private JPanel createQuestFilterRow()
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBorder(new EmptyBorder(5, 0, 0, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel label = new JLabel("Quest items");
		label.setForeground(Color.WHITE);
		row.add(label, BorderLayout.WEST);

		row.setToolTipText("Include quest items in search results");

		JCheckBox checkbox = new JCheckBox();
		checkbox.setSelected(includeQuest);
		checkbox.setToolTipText(row.getToolTipText());
		checkbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
		checkbox.addActionListener(e -> {
			includeQuest = checkbox.isSelected();
			triggerSearch();
		});
		row.add(checkbox, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private JPanel createUncommonFilterRow()
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBorder(new EmptyBorder(5, 0, 0, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel label = new JLabel("Uncommon items");
		label.setForeground(Color.WHITE);
		row.add(label, BorderLayout.WEST);

		row.setToolTipText("Include uncommon and removed items in search results");

		JCheckBox checkbox = new JCheckBox();
		checkbox.setSelected(includeUncommon);
		checkbox.setToolTipText(row.getToolTipText());
		checkbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
		checkbox.addActionListener(e -> {
			includeUncommon = checkbox.isSelected();
			triggerSearch();
		});
		row.add(checkbox, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private void triggerSearch()
	{
		if (pendingSearch != null)
		{
			pendingSearch.cancel(false);
		}
		pendingSearch = executor.schedule(() -> clientThread.invokeLater(() -> {
			if (!executeSearch())
			{
				triggerSearch();
			}
		}), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
	}

	private void showInfoCard(String title, String description)
	{
		SwingUtilities.invokeLater(() -> {
			SwingUtil.fastRemoveAll(resultsContainer);
			resultsContainer.updateUI();
			infoPanel.setContent(title, description);
			cards.show(cardPanel, CARD_INFO);
			searching.set(false);
		});
	}

	private boolean executeSearch()
	{
		if (!searching.compareAndSet(false, true))
		{
			return false;
		}

		String query = searchField.getText().trim();
		if (query.length() == 1)
		{
			showInfoCard("Too short", "Type a longer search for results");
			return true;
		}

		List<SearchResult> results;
		if (query.isEmpty())
		{
			results = searchService.getPlayerItems(ordering, includeQuest, includeUncommon);
			if (results.isEmpty())
			{
				showInfoCard("Item Search", "Search for items to add to your plate.");
				return true;
			}
		}
		else
		{
			results = searchService.search(query, ordering, includeQuest, includeUncommon, null);
		}
		buildResults(results);
		return true;
	}

	private void buildResults(List<SearchResult> results)
	{
		if (results.isEmpty())
		{
			showInfoCard("No results", "No matching items found");
			return;
		}

		SwingUtilities.invokeLater(() -> {
			SwingUtil.fastRemoveAll(resultsContainer);

			for (SearchResult result : results)
			{
				int itemId = result.getId();
				boolean duplicate = alreadyAddedIds.contains(itemId);
				SearchResultPanel panel = new SearchResultPanel(result, onItemSelected, duplicate, config.iconScale() / 100f);

				JPanel margin = new JPanel(new BorderLayout());
				margin.setBackground(ColorScheme.DARK_GRAY_COLOR);
				margin.setBorder(new EmptyBorder(2, 10, 2, 10));
				margin.add(panel, BorderLayout.CENTER);
				margin.setMaximumSize(new Dimension(Integer.MAX_VALUE, margin.getPreferredSize().height));

				resultsContainer.add(margin);
			}

			cards.show(cardPanel, CARD_RESULTS);
			resultsContainer.revalidate();
			scrollPane.scrollToTop();
			searching.set(false);
		});
	}
}
