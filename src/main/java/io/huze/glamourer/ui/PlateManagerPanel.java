package io.huze.glamourer.ui;

import io.huze.glamourer.Config;
import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.glam.Glamourer;
import io.huze.glamourer.plate.Plate;
import io.huze.glamourer.plate.PlateManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.net.URI;
import javax.swing.border.EmptyBorder;
import javax.swing.JLabel;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;

@Slf4j
public class PlateManagerPanel extends JPanel
{
	private final ClientThread clientThread;
	private final PlateManager plateManager;
	private final Glamourer glamourer;
	private final Consumer<Plate> onAddItemRequest;
	private final Config config;

	private final VerticalScrollPane scrollPane;
	private final JButton expandCollapseAllButton;
	private boolean pendingScrollToBottom;

	public PlateManagerPanel(ClientThread clientThread,
							 PlateManager plateManager, Glamourer glamourer, Config config,
							 Consumer<Plate> onAddItemRequest)
	{
		this.clientThread = clientThread;
		this.plateManager = plateManager;
		this.glamourer = glamourer;
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

		add(titlePanel, BorderLayout.NORTH);

		scrollPane = new VerticalScrollPane();
		add(scrollPane, BorderLayout.CENTER);

		importPlateButton.addActionListener(e -> {
			ImportPlateDialog dialog = new ImportPlateDialog(
				SwingUtilities.windowForComponent(this),
				plateManager,
				clientThread,
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
		platesContainer.removeAll();

		List<Plate> plates = plateManager.getPlates();
		for (int i = 0; i < plates.size(); i++)
		{
			Plate plate = plates.get(i);
			PlateRowPanel rowPanel = new PlateRowPanel(
				plate, glamourer, plateManager.getIconService(),
				clientThread, plateManager.getGson(),
				config.iconScale() / 100f, onAddItemRequest,
				p -> clientThread.invokeLater(() -> plateManager.deletePlate(p.getId())),
				() -> {
					updateExpandCollapseButton();
					revalidate();
					repaint();
				},
				this::handleItemMove
			);

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
		clientThread.invokeLater(() -> {
			if (sourcePlate == targetPlate)
			{
				// Same plate - just reorder
				sourcePlate.moveGlamour(sourceIndex, targetIndex);
			}
			else
			{
				// Cross-plate transfer - check for duplicate before extracting
				Glamour glam = sourcePlate.getGlamours().get(sourceIndex);
				if (targetPlate.containsItem(glam.getPrimaryItemId()))
				{
					return;
				}
				glam = sourcePlate.extractGlamour(glamourer, sourceIndex);
				if (glam != null)
				{
					targetPlate.insertGlamour(glamourer, targetIndex, glam);
				}
			}

			// Rebuild affected row panels on EDT
			SwingUtilities.invokeLater(() -> {
				PlateRowPanel sourceRow = findRowPanelForPlate(sourcePlate);
				if (sourceRow != null)
				{
					sourceRow.rebuildDetailsPanel();
				}
				if (sourcePlate != targetPlate)
				{
					PlateRowPanel targetRow = findRowPanelForPlate(targetPlate);
					if (targetRow != null)
					{
						targetRow.rebuildDetailsPanel();
					}
				}
			});
		});
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
