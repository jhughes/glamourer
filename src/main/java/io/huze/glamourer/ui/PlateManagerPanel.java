package io.huze.glamourer.ui;

import io.huze.glamourer.Config;
import io.huze.glamourer.glam.IconScale;
import io.huze.glamourer.plate.ChangeLog;
import io.huze.glamourer.plate.Plate;
import io.huze.glamourer.plate.PlateManager;
import io.huze.glamourer.texture.TextureImages;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import javax.swing.border.EmptyBorder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.components.PluginErrorPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

@Slf4j
public class PlateManagerPanel extends JPanel implements GlamourerSubPanel
{
	private final PlateManager plateManager;
	private final ItemGlamourWindows itemWindows;
	private final TextureImages textureImages;
	private final Consumer<Plate> onAddItemRequest;
	private final Config config;
	private final ChangeLog changeLog;

	private final PluginErrorPanel infoPanel;
	private final VerticalScrollPane scrollPane;
	private final JButton expandCollapseAllButton;
	private final JButton undoButton;
	private final JButton redoButton;
	private final JPanel toolbarButtons;
	private final JPanel secondaryToolbar;
	private boolean pendingScrollToBottom;
	private IconScale iconScale;

	public PlateManagerPanel(PlateManager plateManager, Config config, Consumer<Plate> onAddItemRequest,
							 ItemGlamourWindows itemWindows, TextureImages textureImages)
	{
		this.plateManager = plateManager;
		this.itemWindows = itemWindows;
		this.textureImages = textureImages;
		this.config = config;
		this.changeLog = plateManager.getChangeLog();
		this.onAddItemRequest = onAddItemRequest;
		this.iconScale = config.iconScale();

		setLayout(new BorderLayout());

		// Title bar toolbar (right side of title)
		toolbarButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		toolbarButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JButton importPlateButton = new JButton();
		ImageIcons.setImportIcon(importPlateButton);
		importPlateButton.setToolTipText("Import plate JSON");
		toolbarButtons.add(importPlateButton);

		JButton createPlateButton = new JButton();
		ImageIcons.setCreateIcon(createPlateButton);
		createPlateButton.setToolTipText("Create empty plate");
		toolbarButtons.add(createPlateButton);

		expandCollapseAllButton = new JButton();
		expandCollapseAllButton.addActionListener(e -> toggleExpandCollapseAll());
		toolbarButtons.add(expandCollapseAllButton);

		// Secondary toolbar row (below title)
		secondaryToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
		secondaryToolbar.setBorder(new EmptyBorder(0, 5, 0, 0));
		secondaryToolbar.setBackground(ColorScheme.DARK_GRAY_COLOR);

		undoButton = new JButton();
		ImageIcons.setUndoIcon(undoButton, false);
		undoButton.setToolTipText("Undo");
		undoButton.setEnabled(false);
		undoButton.addActionListener(e -> plateManager.undo());
		secondaryToolbar.add(undoButton);

		redoButton = new JButton();
		ImageIcons.setRedoIcon(redoButton, false);
		redoButton.setToolTipText("Redo");
		redoButton.setEnabled(false);
		redoButton.addActionListener(e -> plateManager.redo());
		secondaryToolbar.add(redoButton);

		changeLog.setOnUndoRedoAvailabilityChanged(() -> SwingUtilities.invokeLater(this::updateUndoRedoButtons));

		infoPanel = new PluginErrorPanel();
		infoPanel.setContent("Getting Started",
			"Create a new plate with the + button, then search for items to glamour.");

		scrollPane = new VerticalScrollPane();
		add(scrollPane, BorderLayout.CENTER);

		UndoRedoKeys.install(this, plateManager::undo, plateManager::redo);

		importPlateButton.addActionListener(e -> {
			ImportPlateDialog dialog = new ImportPlateDialog(
				SwingUtilities.windowForComponent(this),
				config,
				plateManager,
				textureImages
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
		plateManager.setOnPlatesChanged(v -> SwingUtilities.invokeLater(this::refreshPlates));
	}

	private void refreshPlates()
	{
		rebuildPlatesSection();
		itemWindows.refreshAll();
	}

	@Override
	public String getCardKey()
	{
		return "PLATES";
	}

	@Override
	public JPanel getToolbarButtons()
	{
		return toolbarButtons;
	}

	@Nullable
	@Override
	public JPanel getSubHeaderPanel()
	{
		return secondaryToolbar;
	}

	@Override
	public void onActivate()
	{
		if (config.iconScale() != iconScale)
		{
			iconScale = config.iconScale();
			rebuildPlatesSection();
		}
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
		if (plates.isEmpty())
		{
			platesContainer.add(infoPanel);
		}
		for (int i = 0; i < plates.size(); i++)
		{
			Plate plate = plates.get(i);
			PlateRowPanel rowPanel = new PlateRowPanel(
				plate, plateManager.getIconService(), config,
				plateManager::exportPlateJson,
				onAddItemRequest,
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
				plateManager::setPlateIconStyle,
				itemWindows,
				textureImages
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
				if (((PlateRowPanel) comp).isExpanded())
				{
					return true;
				}
			}
		}
		return false;
	}

	private void updateUndoRedoButtons()
	{
		boolean canUndo = changeLog.canUndo();
		boolean canRedo = changeLog.canRedo();
		undoButton.setEnabled(canUndo);
		redoButton.setEnabled(canRedo);
		ImageIcons.setUndoIcon(undoButton, canUndo);
		ImageIcons.setRedoIcon(redoButton, canRedo);

		String undoDesc = changeLog.undoDescription();
		undoButton.setToolTipText("<html>" + undoDesc + "<br>" + UndoRedoKeys.UNDO_SHORTCUT_TEXT + "</html>");
		String redoDesc = changeLog.redoDescription();
		redoButton.setToolTipText("<html>" + redoDesc + "<br>" + UndoRedoKeys.REDO_SHORTCUT_TEXT + "</html>");
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
		for (Component comp : scrollPane.getContainer().getComponents())
		{
			if (comp instanceof PlateRowPanel)
			{
				((PlateRowPanel) comp).setExpanded(shouldExpand);
			}
		}
		updateExpandCollapseButton();
	}

	private void handleItemMove(Plate sourcePlate, int sourceIndex, Plate targetPlate, int targetIndex)
	{
		if (sourcePlate == targetPlate)
		{
			plateManager.moveGlamour(sourcePlate, sourceIndex, targetIndex);
			PlateRowPanel sourceRow = findRowPanelForPlate(sourcePlate);
			if (sourceRow != null)
			{
				sourceRow.rebuildDetailsPanel();
			}
		}
		else
		{
			plateManager.transferGlamour(sourcePlate, sourceIndex, targetPlate, targetIndex);
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
