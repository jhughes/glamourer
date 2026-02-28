package io.huze.glamourer.ui;

import com.google.gson.Gson;
import io.huze.glamourer.Config;
import io.huze.glamourer.color.ColorGroup;
import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.glam.IconService;
import io.huze.glamourer.plate.Plate;
import io.huze.glamourer.ui.colorpicker.GroupColorLabel;
import io.huze.glamourer.ui.colorpicker.SingleColorLabel;
import java.awt.BorderLayout;
import java.awt.FontMetrics;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

@Slf4j
public class PlateRowPanel extends JPanel
{
	@Getter
	private final Plate plate;
	private final IconService iconService;
	private final Gson gson;
	private final float iconScale;
	private final Consumer<Plate> onAddItemRequest;
	private final Runnable onExpandToggle;
	private final ItemDragDropHandler.ItemMoveCallback onItemMoved;
	private final boolean preview;

	private boolean expanded;
	private final Set<String> expandedGroups = new HashSet<>();
	private final Set<Integer> collapsedItems = new HashSet<>();
	private final JPanel detailsPanel;
	@Getter
	private final JPanel headerPanel;
	private final JButton expandButton;
	private final JLabel plateIconLabel;
	private ToggleSwitch enabledToggle;
	private final JLabel nameLabel;
	private JTextField nameField;
	private CardLayout nameCardLayout;
	private JPanel nameContainer;
	private JPanel toolbarPanel;
	private boolean editingCancelled;

	public PlateRowPanel(Plate plate, IconService iconService, Config config,
						 Gson gson, float iconScale, Consumer<Plate> onAddItemRequest,
						 Consumer<Plate> onDeleteRequest, Runnable onExpandToggle,
						 ItemDragDropHandler.ItemMoveCallback onItemMoved,
						 BiConsumer<Plate, Boolean> onEnableChanged)
	{
		this(plate, iconService, config, gson, iconScale, onAddItemRequest,
			onDeleteRequest, onExpandToggle, onItemMoved, onEnableChanged, false);
	}

	public PlateRowPanel(Plate plate, IconService iconService, float iconScale, Runnable onExpandToggle)
	{
		this(plate, iconService, null, null, iconScale, null, null, onExpandToggle, null, null, true);
	}

	private PlateRowPanel(Plate plate, IconService iconService, Config config,
						  Gson gson, float iconScale, Consumer<Plate> onAddItemRequest,
						  Consumer<Plate> onDeleteRequest, Runnable onExpandToggle,
						  ItemDragDropHandler.ItemMoveCallback onItemMoved,
						  BiConsumer<Plate, Boolean> onEnableChanged, boolean preview)
	{
		this.plate = plate;
		this.iconService = iconService;
		this.gson = gson;
		this.iconScale = iconScale;
		this.onAddItemRequest = onAddItemRequest;
		this.onExpandToggle = onExpandToggle;
		this.onItemMoved = onItemMoved;
		this.preview = preview;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
			BorderFactory.createEmptyBorder(3, 3, 2, 3)
		));

		// Header panel using BorderLayout for compact fit
		headerPanel = new JPanel(new BorderLayout(2, 0))
		{
			@Override
			public String getToolTipText(MouseEvent event)
			{
				return isTextTruncated(nameLabel) ? plate.getName() : null;
			}
		};
		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		ToolTipManager.sharedInstance().registerComponent(headerPanel);

		// Left side: expand button + collapsed icon
		expanded = plate.isExpanded();
		expandButton = new JButton();
		ImageIcons.setExpandIcon(expandButton, expanded);
		expandButton.addActionListener(e -> toggleExpanded());

		plateIconLabel = new JLabel();
		plateIconLabel.setVisible(!expanded);

		JPanel leftPanel = new JPanel(new BorderLayout(0, 0));
		leftPanel.setOpaque(false);
		leftPanel.add(expandButton, BorderLayout.WEST);
		leftPanel.add(plateIconLabel, BorderLayout.EAST);
		headerPanel.add(leftPanel, BorderLayout.WEST);

		// Center: name label
		nameLabel = new JLabel(plate.getName(), SwingConstants.LEFT);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setBorder(new EmptyBorder(2, 0, 0, 4));
		nameLabel.setPreferredSize(new Dimension(0, nameLabel.getPreferredSize().height));
		nameLabel.setMinimumSize(new Dimension(0, nameLabel.getPreferredSize().height));

		if (!preview)
		{
			// Name container with CardLayout for inline editing
			nameCardLayout = new CardLayout();
			nameContainer = new JPanel(nameCardLayout);
			nameContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			nameContainer.setMinimumSize(new Dimension(0, 0));
			nameContainer.add(nameLabel, "label");

			// Name text field (edit view)
			nameField = new JTextField(plate.getName());
			nameField.setHorizontalAlignment(JTextField.LEFT);
			nameField.setBorder(new EmptyBorder(0, 4, 0, 4));
			nameField.setPreferredSize(new Dimension(0, nameField.getPreferredSize().height));
			nameField.setMinimumSize(new Dimension(0, nameField.getPreferredSize().height));
			nameField.addActionListener(e -> finishEditing());
			nameField.addFocusListener(new java.awt.event.FocusAdapter()
			{
				@Override
				public void focusLost(java.awt.event.FocusEvent e)
				{
					finishEditing();
				}
			});
			nameField.addKeyListener(new java.awt.event.KeyAdapter()
			{
				@Override
				public void keyPressed(java.awt.event.KeyEvent e)
				{
					if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE)
					{
						cancelEditing();
					}
				}
			});
			nameContainer.add(nameField, "edit");

			nameCardLayout.show(nameContainer, "label");
			headerPanel.add(nameContainer, BorderLayout.CENTER);

			// Right side: toggle
			enabledToggle = new ToggleSwitch(plate.isEnabled());
			enabledToggle.addActionListener(e -> {
				boolean enabled = enabledToggle.isSelected();
				if (onEnableChanged != null)
				{
					onEnableChanged.accept(plate, enabled);
				}
			});
			headerPanel.add(enabledToggle, BorderLayout.EAST);

			// Toolbar row (visible when expanded)
			toolbarPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
			toolbarPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			toolbarPanel.setVisible(expanded);

			JButton renameButton = new JButton();
			ImageIcons.setEditIcon(renameButton);
			renameButton.setToolTipText("Rename");
			renameButton.addActionListener(e -> startEditing());
			toolbarPanel.add(renameButton);

			JButton exportButton = new JButton();
			ImageIcons.setExportIcon(exportButton);
			exportButton.setToolTipText("Export JSON to clipboard");
			exportButton.addActionListener(e -> exportToClipboard(plate, false));
			toolbarPanel.add(exportButton);

			// Right-click menu
			JPopupMenu popupMenu = new JPopupMenu();

			JMenuItem renameItem = new JMenuItem("Rename", ImageIcons.getEditIcon());
			renameItem.setIconTextGap(8);
			renameItem.addActionListener(e -> startEditing());
			popupMenu.add(renameItem);

			JMenuItem exportItem = new JMenuItem("Export JSON to clipboard", ImageIcons.getExportIcon());
			exportItem.setIconTextGap(8);
			exportItem.addActionListener(e -> exportToClipboard(plate, false));
			popupMenu.add(exportItem);

			if (config.advancedOptions())
			{
				JMenuItem exportVerboseItem = new JMenuItem("Export Verbose JSON to clipboard", ImageIcons.getExportIcon());
				exportVerboseItem.setIconTextGap(8);
				exportVerboseItem.addActionListener(e -> exportToClipboard(plate, true));
				popupMenu.add(exportVerboseItem);
			}

			JMenuItem deleteItem = new JMenuItem("Delete", ImageIcons.getBinIcon());
			deleteItem.setIconTextGap(8);
			deleteItem.addActionListener(e -> {
				if (plate.getGlamours().isEmpty())
				{
					onDeleteRequest.accept(plate);
				}
				else
				{
					int result = DialogUtil.showConfirmDialogNearCursor(
						SwingUtilities.windowForComponent(this),
						"Delete plate \"" + plate.getName() + "\"?",
						"Confirm Delete",
						JOptionPane.YES_NO_OPTION,
						JOptionPane.WARNING_MESSAGE
					);
					if (result == JOptionPane.YES_OPTION)
					{
						onDeleteRequest.accept(plate);
					}
				}
			});
			popupMenu.add(deleteItem);
			headerPanel.setComponentPopupMenu(popupMenu);
		}
		else
		{
			headerPanel.add(nameLabel, BorderLayout.CENTER);
		}

		// Wrap header + toolbar in a container
		JPanel headerWrapper = new JPanel();
		headerWrapper.setLayout(new BoxLayout(headerWrapper, BoxLayout.Y_AXIS));
		headerWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerWrapper.add(headerPanel);
		if (toolbarPanel != null)
		{
			headerWrapper.add(toolbarPanel);
		}
		add(headerWrapper, BorderLayout.NORTH);

		// Details panel (visibility based on expanded state)
		detailsPanel = new JPanel();
		detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
		detailsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		detailsPanel.setBorder(new EmptyBorder(5, 3, 3, 3));
		detailsPanel.setVisible(expanded);

		add(detailsPanel, BorderLayout.CENTER);

		rebuildDetailsPanel();
	}

	private void exportToClipboard(Plate plate, boolean verbose)
	{
		var data = plate.getData(verbose);
		data.setEnabled(null);
		data.setExpanded(null);
		String json = gson.toJson(data);
		StringSelection selection = new StringSelection(json);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
	}

	private void toggleExpanded()
	{
		expanded = !expanded;
		ImageIcons.setExpandIcon(expandButton, expanded);
		plateIconLabel.setVisible(!expanded);
		detailsPanel.setVisible(expanded);
		if (toolbarPanel != null)
		{
			toolbarPanel.setVisible(expanded);
		}
		plate.setExpanded(expanded);
		revalidate();
		if (onExpandToggle != null)
		{
			onExpandToggle.run();
		}
	}

	private void updatePlateIcon()
	{
		List<Glamour> glamours = plate.getGlamours();
		if (!glamours.isEmpty())
		{
			BufferedImage icon = iconService.getIcon(glamours.get(0));
			ImageIcons.setIconWithComponentHeight(plateIconLabel, icon, headerPanel);
			return;
		}
		plateIconLabel.setIcon(null);
		plateIconLabel.setPreferredSize(new Dimension(0, 0));
	}

	private void startEditing()
	{
		editingCancelled = false;
		nameField.setText(plate.getName());
		nameCardLayout.show(nameContainer, "edit");
		nameField.requestFocusInWindow();
		nameField.selectAll();
	}

	private void finishEditing()
	{
		if (editingCancelled)
		{
			return;
		}
		String newName = nameField.getText().trim();
		if (!newName.isEmpty() && !newName.equals(plate.getName()))
		{
			plate.setName(newName);
			nameLabel.setText(newName);
		}
		nameCardLayout.show(nameContainer, "label");
	}

	private void cancelEditing()
	{
		editingCancelled = true;
		nameField.setText(plate.getName());
		nameCardLayout.show(nameContainer, "label");
	}

	public void restoreCollapseState(PlateRowPanel other)
	{
		collapsedItems.clear();
		collapsedItems.addAll(other.collapsedItems);
		expandedGroups.clear();
		expandedGroups.addAll(other.expandedGroups);
		rebuildDetailsPanel();
	}

	public void setExpanded(boolean expanded)
	{
		if (expanded)
		{
			collapsedItems.clear();
		}
		else
		{
			for (int i = 0; i < plate.getGlamours().size(); i++)
			{
				collapsedItems.add(i);
			}
		}
		rebuildDetailsPanel();
		if (this.expanded != expanded)
		{
			toggleExpanded();
		}
	}

	public void rebuildDetailsPanel()
	{
		if (enabledToggle != null)
		{
			enabledToggle.setSelected(plate.isEnabled());
		}
		updatePlateIcon();
		detailsPanel.removeAll();

		Set<Glamour> hiddenGlamours = plate.getHiddenGlamours();
		int i = 0;
		for (Glamour glam : plate.getGlamours())
		{
			JPanel itemPanel = createGlamourItemPanel(glam, i, hiddenGlamours.contains(glam));
			if (!preview)
			{
				ItemDragDropHandler.setupItemDragAndDrop(
					itemPanel,
					plate,
					i,
					onItemMoved,
					this::rebuildDetailsPanel
				);
			}

			detailsPanel.add(itemPanel);
			detailsPanel.add(Box.createVerticalStrut(3));
			i++;
		}

		if (!preview)
		{
			JButton searchItemButton = new JButton("+ Search for Item");
			searchItemButton.setMargin(new Insets(2, 6, 2, 6));
			searchItemButton.addActionListener(e -> {
				if (onAddItemRequest != null)
				{
					onAddItemRequest.accept(plate);
				}
			});

			// Setup as drop target for items from OTHER plates
			ItemDragDropHandler.setupAddItemButtonDropTarget(searchItemButton, plate, onItemMoved, this::rebuildDetailsPanel);

			JPanel buttonWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
			buttonWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			buttonWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, searchItemButton.getPreferredSize().height + 4));
			buttonWrapper.add(searchItemButton);
			detailsPanel.add(buttonWrapper);
		}

		detailsPanel.revalidate();
		detailsPanel.repaint();
	}

	private JPanel createGlamourItemPanel(Glamour glam, int glamourIndex, boolean isHidden)
	{
		JLabel itemNameLabel = new JLabel(glam.getItemName(), SwingConstants.LEFT);
		itemNameLabel.setForeground(isHidden ? Color.GRAY : Color.WHITE);
		itemNameLabel.setBorder(new EmptyBorder(5, 0, 3, 0));

		JPanel panel = new JPanel(new BorderLayout())
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}

			@Override
			public String getToolTipText(MouseEvent event)
			{
				return isTextTruncated(itemNameLabel) ? glam.getItemName() : null;
			}
		};
		ToolTipManager.sharedInstance().registerComponent(panel);
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(3, 3, 3, 3));
		boolean itemExpanded = !collapsedItems.contains(glamourIndex);

		panel.add(createItemHeaderRow(itemNameLabel, glam, glamourIndex, itemExpanded, isHidden), BorderLayout.NORTH);

		JPanel bodyPanel = new JPanel(new BorderLayout(5, 0));
		bodyPanel.setOpaque(false);
		bodyPanel.add(createItemIconLabel(glam), BorderLayout.WEST);
		bodyPanel.add(createColorsPanel(glam, glamourIndex), BorderLayout.CENTER);
		bodyPanel.setVisible(itemExpanded);
		panel.add(bodyPanel, BorderLayout.CENTER);
		return panel;
	}

	private JLabel createItemIconLabel(Glamour glam)
	{
		JLabel iconLabel = new JLabel();
		ImageIcons.setScaledIcon(iconLabel, iconService.getIcon(glam), iconScale);
		return iconLabel;
	}

	private JPanel createItemHeaderRow(JLabel itemNameLabel, Glamour glam, int glamourIndex, boolean itemExpanded, boolean isHidden)
	{
		JPanel headerRow = new JPanel(new BorderLayout(2, 0));
		headerRow.setOpaque(false);

		JButton itemExpandButton = new JButton();
		ImageIcons.setExpandIcon(itemExpandButton, itemExpanded);
		itemExpandButton.addActionListener(e -> {
			if (collapsedItems.contains(glamourIndex))
			{
				collapsedItems.remove(glamourIndex);
			}
			else
			{
				collapsedItems.add(glamourIndex);
			}
			rebuildDetailsPanel();
		});
		headerRow.add(itemExpandButton, BorderLayout.WEST);

		JPanel centerPanel = new JPanel(new BorderLayout(0, 0));
		centerPanel.setOpaque(false);

		centerPanel.add(itemNameLabel, BorderLayout.CENTER);

		if (isHidden)
		{
			JLabel warningLabel = new JLabel(ImageIcons.getEyeStrikeIcon());
			warningLabel.setToolTipText("Hidden by a higher plate");
			warningLabel.setBorder(new EmptyBorder(5, 4, 3, 0));
			centerPanel.add(warningLabel, BorderLayout.EAST);
		}

		if (!itemExpanded)
		{
			BufferedImage icon = iconService.getIcon(glam);
			JLabel iconLabel = new JLabel();
			ImageIcons.setIconWithComponentHeight(iconLabel, icon, itemNameLabel);
			centerPanel.add(iconLabel, BorderLayout.WEST);
		}

		headerRow.add(centerPanel, BorderLayout.CENTER);

		if (!preview)
		{
			JButton removeButton = new JButton();
			ImageIcons.setBinIcon(removeButton);
			removeButton.setToolTipText("Remove");
			removeButton.addActionListener(e -> {
				plate.removeGlamour(glamourIndex);
				rebuildDetailsPanel();
			});
			headerRow.add(removeButton, BorderLayout.EAST);
		}

		return headerRow;
	}

	private JPanel createColorsPanel(Glamour glam, int glamourIndex)
	{
		JPanel colorsPanel = new JPanel();
		colorsPanel.setLayout(new BoxLayout(colorsPanel, BoxLayout.Y_AXIS));
		colorsPanel.setOpaque(false);

		List<ColorReplacement> pairs = glam.getColorReplacements();
		List<ColorGroup> groups = ColorGroup.groupColors(pairs);
		int groupNum = 0;
		int displayNum = 1;

		for (ColorGroup group : groups)
		{
			if (groupNum > 0)
			{
				colorsPanel.add(Box.createVerticalStrut(2));
			}

			boolean isMultiColor = group.getColorIndices().size() > 1;
			boolean isCoherent = group.areReplacementsCoherent(pairs);
			String groupKey = glamourIndex + "_" + groupNum;
			boolean isGroupExpanded = expandedGroups.contains(groupKey);

			if (isMultiColor && isCoherent)
			{
				if (isGroupExpanded)
				{
					displayNum = addExpandedGroupRows(colorsPanel, glam, glamourIndex, groupNum, group, pairs, displayNum);
				}
				else
				{
					addCollapsedGroupRow(colorsPanel, glam, glamourIndex, groupNum, group, pairs);
					displayNum += group.getColorIndices().size();
				}
			}
			else
			{
				displayNum = addSingleColorRows(colorsPanel, glam, glamourIndex, group, pairs, displayNum);
			}

			groupNum++;
		}

		return colorsPanel;
	}

	private int addExpandedGroupRows(JPanel colorsPanel, Glamour glam, int glamourIndex, int groupNum,
									 ColorGroup group, List<ColorReplacement> pairs, int displayNum)
	{
		boolean isFirst = true;
		for (int colorIdx : group.getColorIndices())
		{
			ColorReplacement pair = pairs.get(colorIdx);

			if (!isFirst)
			{
				colorsPanel.add(Box.createVerticalStrut(2));
			}

			JPanel rowPanel = new JPanel(new BorderLayout(3, 0));
			rowPanel.setOpaque(false);
			rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

			if (isFirst)
			{
				rowPanel.add(createCollapseButton(glamourIndex, groupNum), BorderLayout.WEST);
			}
			else
			{
				rowPanel.setBorder(new EmptyBorder(0, 20, 0, 0));
			}

			SingleColorLabel colorLabel = new SingleColorLabel(glam.getItemName() + " Color " + displayNum, pair);
			if (preview)
			{
				colorLabel.setViewOnly();
			}
			else
			{
				colorLabel.setOnColorChange(newColor -> updateSingleColor(glamourIndex, colorIdx, newColor));
			}
			rowPanel.add(colorLabel, BorderLayout.CENTER);

			colorsPanel.add(rowPanel);
			isFirst = false;
			displayNum++;
		}
		return displayNum;
	}

	private void addCollapsedGroupRow(JPanel colorsPanel, Glamour glam, int glamourIndex, int groupNum,
									  ColorGroup group, List<ColorReplacement> pairs)
	{
		JPanel groupHeaderPanel = new JPanel(new BorderLayout(3, 0));
		groupHeaderPanel.setOpaque(false);
		groupHeaderPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		groupHeaderPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		groupHeaderPanel.add(createExpandButton(glamourIndex, groupNum), BorderLayout.WEST);

		List<ColorReplacement> groupReplacements = new ArrayList<>();
		for (int idx : group.getColorIndices())
		{
			groupReplacements.add(pairs.get(idx));
		}

		String label = glam.getItemName() + " Group " + (groupNum + 1) + " (" + group.getColorIndices().size() + " colors)";
		GroupColorLabel colorLabel = new GroupColorLabel(label, groupReplacements);
		if (preview)
		{
			colorLabel.setViewOnly();
		}
		else
		{
			colorLabel.setOnColorChange(newColor -> updateGroupColors(glamourIndex, group, newColor));
			colorLabel.setOnRevert(() -> revertGroupColors(glamourIndex, group, groupReplacements));
		}
		groupHeaderPanel.add(colorLabel, BorderLayout.CENTER);

		colorsPanel.add(groupHeaderPanel);
	}

	private int addSingleColorRows(JPanel colorsPanel, Glamour glam, int glamourIndex,
								   ColorGroup group, List<ColorReplacement> pairs, int displayNum)
	{
		boolean first = true;
		for (int colorIdx : group.getColorIndices())
		{
			ColorReplacement pair = pairs.get(colorIdx);

			if (!first)
			{
				colorsPanel.add(Box.createVerticalStrut(2));
			}
			first = false;

			SingleColorLabel colorLabel = new SingleColorLabel(glam.getItemName() + " Color " + displayNum, pair);
			colorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			colorLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, colorLabel.getPreferredSize().height));
			if (preview)
			{
				colorLabel.setViewOnly();
			}
			else
			{
				colorLabel.setOnColorChange(newColor -> updateSingleColor(glamourIndex, colorIdx, newColor));
			}
			colorsPanel.add(colorLabel);
			displayNum++;
		}
		return displayNum;
	}

	private JButton createExpandButton(int glamourIndex, int groupNum)
	{
		JButton expandButton = new JButton();
		ImageIcons.setExpandIcon(expandButton, false);
		expandButton.addActionListener(e -> {
			expandedGroups.add(glamourIndex + "_" + groupNum);
			rebuildDetailsPanel();
		});
		return expandButton;
	}

	private JButton createCollapseButton(int glamourIndex, int groupNum)
	{
		JButton collapseButton = new JButton();
		ImageIcons.setExpandIcon(collapseButton, true);
		collapseButton.addActionListener(e -> {
			expandedGroups.remove(glamourIndex + "_" + groupNum);
			rebuildDetailsPanel();
		});
		return collapseButton;
	}

	private void updateSingleColor(int glamourIndex, int colorIdx, short newColor)
	{
		plate.updateGlamourColor(glamourIndex, colorIdx, newColor);
		rebuildDetailsPanel();
	}

	private void updateGroupColors(int glamourIndex, ColorGroup group, short newColor)
	{
		List<int[]> colorUpdates = new ArrayList<>();
		for (int i = 0; i < group.getColorIndices().size(); i++)
		{
			int colorIdx = group.getColorIndices().get(i);
			short adjustedColor = group.calculateNewColor(newColor, i);
			colorUpdates.add(new int[]{colorIdx, adjustedColor});
		}
		plate.updateGlamourColors(glamourIndex, colorUpdates);
		rebuildDetailsPanel();
	}

	private void revertGroupColors(int glamourIndex, ColorGroup group, List<ColorReplacement> groupReplacements)
	{
		List<int[]> colorUpdates = new ArrayList<>();
		List<Integer> indices = group.getColorIndices();
		for (int i = 0; i < indices.size(); i++)
		{
			ColorReplacement pair = groupReplacements.get(i);
			colorUpdates.add(new int[]{indices.get(i), pair.getOriginal()});
		}
		plate.updateGlamourColors(glamourIndex, colorUpdates);
		rebuildDetailsPanel();
	}

	private static boolean isTextTruncated(JLabel label)
	{
		FontMetrics fm = label.getFontMetrics(label.getFont());
		Insets insets = label.getInsets();
		int availableWidth = label.getWidth() - insets.left - insets.right;
		return fm.stringWidth(label.getText()) > availableWidth;
	}

}
