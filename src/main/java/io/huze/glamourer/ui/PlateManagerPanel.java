package io.huze.glamourer.ui;

import io.huze.glamourer.Config;
import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.plate.Plate;
import io.huze.glamourer.plate.PlateManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Insets;
import java.net.URI;
import javax.swing.border.EmptyBorder;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

@Slf4j
public class PlateManagerPanel extends JPanel
{
	private final PlateManager plateManager;
	private final Consumer<Plate> onAddItemRequest;
	private final Config config;

	private final VerticalScrollPane scrollPane;
	private final JButton expandCollapseAllButton;
	private final ThresholdSlidersPanel thresholdSlidersPanel;
	private boolean pendingScrollToBottom;

	public PlateManagerPanel(PlateManager plateManager, Config config, Consumer<Plate> onAddItemRequest)
	{
		this.plateManager = plateManager;
		this.config = config;
		this.onAddItemRequest = onAddItemRequest;

		setLayout(new BorderLayout());

		// Title bar at the top
		JPanel titlePanel = new JPanel(new BorderLayout());
		titlePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		titlePanel.setBorder(new EmptyBorder(4, 6, 4, 4));

		JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		leftPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel titleLabel = new JLabel("Glamourer");
		titleLabel.setForeground(Color.WHITE);
		leftPanel.add(titleLabel);

		JButton discordButton = new JButton();
		ImageIcons.setDiscordIcon(discordButton);
		discordButton.setToolTipText("Join Discord");
		discordButton.addActionListener(e -> {
			try
			{
				Desktop.getDesktop().browse(new URI("https://discord.gg/B6dD9R5U36"));
			}
			catch (Exception ex)
			{
				log.warn("Failed to open Discord link", ex);
			}
		});
		leftPanel.add(discordButton);

		titlePanel.add(leftPanel, BorderLayout.WEST);

		JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		rightPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton slidersToggleButton = new JButton()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				if (Boolean.TRUE.equals(getClientProperty("active")))
				{
					g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
					g.fillRect(0, 0, getWidth(), getHeight());
				}
				Icon icon = getModel().isRollover() && getRolloverIcon() != null ? getRolloverIcon() : getIcon();
				if (icon != null)
				{
					Insets insets = getInsets();
					icon.paintIcon(this, g,
						insets.left + (getWidth() - insets.left - insets.right - icon.getIconWidth()) / 2,
						insets.top + (getHeight() - insets.top - insets.bottom - icon.getIconHeight()) / 2);
				}
			}
		};
		ImageIcons.setSlidersIcon(slidersToggleButton);
		slidersToggleButton.setToolTipText("Color group settings");
		rightPanel.add(slidersToggleButton);

		JButton importPlateButton = new JButton();
		ImageIcons.setImportIcon(importPlateButton);
		importPlateButton.setToolTipText("Import plate JSON");
		rightPanel.add(importPlateButton);

		JButton createPlateButton = new JButton();
		ImageIcons.setCreateIcon(createPlateButton);
		createPlateButton.setToolTipText("Create empty plate");
		rightPanel.add(createPlateButton);

		expandCollapseAllButton = new JButton();
		expandCollapseAllButton.addActionListener(e -> toggleExpandCollapseAll());
		rightPanel.add(expandCollapseAllButton);

		titlePanel.add(rightPanel, BorderLayout.EAST);

		// Threshold sliders panel (initially hidden)
		thresholdSlidersPanel = new ThresholdSlidersPanel(config, this::rebuildPlatesSection);
		thresholdSlidersPanel.setVisible(false);

		slidersToggleButton.addActionListener(e -> {
			boolean visible = !thresholdSlidersPanel.isVisible();
			thresholdSlidersPanel.setVisible(visible);
			slidersToggleButton.putClientProperty("active", visible);
			slidersToggleButton.repaint();
		});

		// Wrap title bar and sliders in a vertical box
		JPanel northPanel = new JPanel();
		northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
		northPanel.add(titlePanel);
		northPanel.add(thresholdSlidersPanel);

		add(northPanel, BorderLayout.NORTH);

		scrollPane = new VerticalScrollPane();
		add(scrollPane, BorderLayout.CENTER);

		importPlateButton.addActionListener(e -> {
			ImportPlateDialog dialog = new ImportPlateDialog(
				SwingUtilities.windowForComponent(this),
				config,
				plateManager,
				config.iconScale() / 100f
			);
			pendingScrollToBottom = true;
			dialog.setVisible(true);
			if (!dialog.isImported() || dialog.isOverwritten())
			{
				pendingScrollToBottom = false;
			}
		});

		createPlateButton.addActionListener(e -> {
			pendingScrollToBottom = true;
			plateManager.createPlate();
			rebuildPlatesSection();
		});

		// Listen for plate changes
		plateManager.setOnPlatesChanged(v -> SwingUtilities.invokeLater(this::rebuildPlatesSection));

		// Initial build
		rebuildPlatesSection();
	}

	public void rebuildPlatesSection()
	{
		var platesContainer = scrollPane.getContainer();

		// Save existing collapse state before removing panels
		Map<String, PlateRowPanel> oldPanels = new HashMap<>();
		for (Component comp : platesContainer.getComponents())
		{
			if (comp instanceof PlateRowPanel)
			{
				PlateRowPanel row = (PlateRowPanel) comp;
				oldPanels.put(row.getPlate().getId(), row);
			}
		}

		platesContainer.removeAll();

		List<Plate> plates = plateManager.getPlates();
		for (int i = 0; i < plates.size(); i++)
		{
			Plate plate = plates.get(i);
			PlateRowPanel rowPanel = new PlateRowPanel(
				plate, plateManager.getIconService(), plateManager.getGlamourer(), config,
				plateManager.getGson(),
				config.iconScale() / 100f, onAddItemRequest,
				p -> plateManager.deletePlate(p.getId()),
				() -> {
					updateExpandCollapseButton();
					revalidate();
					repaint();
				},
				this::handleItemMove,
				plateManager::setPlateEnabled,
				plateManager::setPlateDisplayStyle,
				plateManager::removeGlamour,
				plateManager::setPlateIconStyle
			);

			PlateRowPanel oldPanel = oldPanels.get(plate.getId());
			if (oldPanel != null)
			{
				rowPanel.restoreCollapseState(oldPanel);
			}

			PlateDragDropHandler.setupDragAndDrop(
				rowPanel.getHeaderPanel(),
				rowPanel,
				i,
				plateManager::movePlate,
				this::rebuildPlatesSection
			);

			platesContainer.add(rowPanel);
		}

		updateExpandCollapseButton();

		platesContainer.revalidate();
		platesContainer.repaint();

		if (pendingScrollToBottom)
		{
			pendingScrollToBottom = false;
			SwingUtilities.invokeLater(scrollPane::scrollToBottom);
		}
	}

	public PlateRowPanel findRowPanelForPlate(Plate plate)
	{
		for (Component comp : scrollPane.getContainer().getComponents())
		{
			if (comp instanceof PlateRowPanel)
			{
				PlateRowPanel row = (PlateRowPanel) comp;
				if (row.getPlate() == plate)
				{
					return row;
				}
			}
		}
		return null;
	}

	private boolean isAnyPlateExpanded()
	{
		for (Component comp : scrollPane.getContainer().getComponents())
		{
			if (comp instanceof PlateRowPanel)
			{
				if (((PlateRowPanel) comp).getPlate().isExpanded())
				{
					return true;
				}
			}
		}
		return false;
	}

	private void updateExpandCollapseButton()
	{
		boolean hasExpanded = isAnyPlateExpanded();
		ImageIcons.setExpandCollapseAllIcon(expandCollapseAllButton, hasExpanded);
		expandCollapseAllButton.setToolTipText(hasExpanded ? "Collapse all" : "Expand all");
	}

	private void toggleExpandCollapseAll()
	{
		boolean shouldExpand = !isAnyPlateExpanded();
		plateManager.runBatched(() -> {
			for (Component comp : scrollPane.getContainer().getComponents())
			{
				if (comp instanceof PlateRowPanel)
				{
					((PlateRowPanel) comp).setExpanded(shouldExpand);
				}
			}
		});
		updateExpandCollapseButton();
	}

	private void handleItemMove(Plate sourcePlate, int sourceIndex, Plate targetPlate, int targetIndex)
	{
		if (sourcePlate == targetPlate)
		{
			// Same plate - just reorder
			sourcePlate.moveGlamour(sourceIndex, targetIndex);
			PlateRowPanel sourceRow = findRowPanelForPlate(sourcePlate);
			if (sourceRow != null)
			{
				sourceRow.rebuildDetailsPanel();
			}
		}
		else
		{
			// Cross-plate transfer - check for duplicate before extracting
			Glamour glam = sourcePlate.getGlamours().get(sourceIndex);
			if (targetPlate.containsItem(glam.getPrimaryItemId()))
			{
				return;
			}
			glam = sourcePlate.removeGlamour(sourceIndex);
			if (glam != null)
			{
				targetPlate.insertGlamour(targetIndex, glam);
			}
			plateManager.reapplyAllPlates();

			// Rebuild all panels since hidden state may have changed
			rebuildAllRowPanels();
		}
	}

	private void rebuildAllRowPanels()
	{
		for (Component comp : scrollPane.getContainer().getComponents())
		{
			if (comp instanceof PlateRowPanel)
			{
				((PlateRowPanel) comp).rebuildDetailsPanel();
			}
		}
	}

	public int getScrollPosition()
	{
		return scrollPane.getVerticalScrollBar().getValue();
	}

	public void setScrollPosition(int position)
	{
		SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(position));
	}
}
