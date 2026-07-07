package io.huze.glamourer.ui;

import io.huze.glamourer.Config;
import io.huze.glamourer.plate.ChangeLog;
import io.huze.glamourer.plate.Plate;
import io.huze.glamourer.plate.PlateManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.KeyStroke;
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
	private final JPanel subHeaderPanel;
	private final ThresholdSlidersPanel thresholdSlidersPanel;
	private final String undoShortcutText;
	private final String redoShortcutText;
	private boolean pendingScrollToBottom;
	private int iconScale = -1;

	public PlateManagerPanel(PlateManager plateManager, Config config, Consumer<Plate> onAddItemRequest)
	{
		this.plateManager = plateManager;
		this.config = config;
		this.changeLog = plateManager.getChangeLog();
		this.onAddItemRequest = onAddItemRequest;

		setLayout(new BorderLayout());

		// Title bar toolbar (right side of title)
		toolbarButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		toolbarButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);

		ToggleButton slidersToggleButton = new ToggleButton();
		ImageIcons.setSlidersIcon(slidersToggleButton);
		slidersToggleButton.setToolTipText("Color group settings");
		toolbarButtons.add(slidersToggleButton);

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
		undoButton.addActionListener(e -> {
			changeLog.undo();
			rebuildPlatesSection();
		});
		secondaryToolbar.add(undoButton);

		redoButton = new JButton();
		ImageIcons.setRedoIcon(redoButton, false);
		redoButton.setToolTipText("Redo");
		redoButton.setEnabled(false);
		redoButton.addActionListener(e -> {
			changeLog.redo();
			rebuildPlatesSection();
		});
		secondaryToolbar.add(redoButton);

		changeLog.setOnUndoRedoAvailabilityChanged(() -> SwingUtilities.invokeLater(this::updateUndoRedoButtons));

		thresholdSlidersPanel = new ThresholdSlidersPanel(config, this::rebuildPlatesSection);
		thresholdSlidersPanel.setVisible(false);

		slidersToggleButton.addActionListener(e -> {
			boolean visible = !thresholdSlidersPanel.isVisible();
			thresholdSlidersPanel.setVisible(visible);
			slidersToggleButton.setActive(visible);
		});

		// Sub-header: secondary toolbar + threshold sliders
		subHeaderPanel = new JPanel();
		subHeaderPanel.setLayout(new BoxLayout(subHeaderPanel, BoxLayout.Y_AXIS));
		subHeaderPanel.add(secondaryToolbar);
		subHeaderPanel.add(thresholdSlidersPanel);

		infoPanel = new PluginErrorPanel();
		infoPanel.setContent("Getting Started",
			"Create a new plate with the + button, then search for items to glamour.");

		scrollPane = new VerticalScrollPane();
		add(scrollPane, BorderLayout.CENTER);

		// Undo/Redo keybindings (active when panel is showing)
		int menuShortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		String modifierName = InputEvent.getModifiersExText(menuShortcutMask);
		undoShortcutText = modifierName + " + Z";
		redoShortcutText = modifierName + " + Shift + Z  /  " + modifierName + " + Y";

		var inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuShortcutMask), "glamourerUndo");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuShortcutMask | InputEvent.SHIFT_DOWN_MASK), "glamourerRedo");
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuShortcutMask), "glamourerRedo");
		getActionMap().put("glamourerUndo", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				SwingUtilities.invokeLater(() -> {
					changeLog.undo();
					rebuildPlatesSection();
				});
			}
		});
		getActionMap().put("glamourerRedo", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				SwingUtilities.invokeLater(() -> {
					changeLog.redo();
					rebuildPlatesSection();
				});
			}
		});

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
		return subHeaderPanel;
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
				plate, plateManager.getIconService(), plateManager.getGlamourer(), config,
				plateManager::exportPlateJson,
				iconScale / 100f, onAddItemRequest,
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
		undoButton.setToolTipText("<html>" + undoDesc + "<br>" + undoShortcutText + "</html>");
		String redoDesc = changeLog.redoDescription();
		redoButton.setToolTipText("<html>" + redoDesc + "<br>" + redoShortcutText + "</html>");
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
