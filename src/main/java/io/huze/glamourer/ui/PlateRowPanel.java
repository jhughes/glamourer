package io.huze.glamourer.ui;

import com.google.gson.Gson;
import io.huze.glamourer.Config;
import io.huze.glamourer.color.ColorGroup;
import io.huze.glamourer.color.ColorGroupSettings;
import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.glam.Glamourer;
import io.huze.glamourer.glam.GlamourVisibility;
import io.huze.glamourer.glam.EquipHighlightGlamour;
import io.huze.glamourer.glam.HighlightMask;
import io.huze.glamourer.glam.IconService;
import io.huze.glamourer.plate.DisplayStyle;
import io.huze.glamourer.plate.IconStyle;
import io.huze.glamourer.plate.Plate;
import io.huze.glamourer.texture.TextureReplacement;
import io.huze.glamourer.ui.colorpicker.GroupColorLabel;
import io.huze.glamourer.ui.colorpicker.SingleColorLabel;
import io.huze.glamourer.ui.texturepicker.TextureLabel;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.swing.Timer;
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
	private static final float HIGHLIGHT_PERIOD_MS = 2000f;
	@Getter
	private final Plate plate;
	private final IconService iconService;
	private final Glamourer glamourer;
	private final Gson gson;
	private final float iconScale;
	@Nonnull private final Config config;
	private final Consumer<Plate> onAddItemRequest;
	private final BiConsumer<Plate, Integer> onGlamourRemoved;
	private final Runnable onExpandToggle;
	private final ItemDragDropHandler.ItemMoveCallback onItemMoved;
	private final boolean preview;

	private boolean expanded;
	private final Set<String> expandedGroups = new HashSet<>();
	private final Set<Integer> collapsedItemIds = new HashSet<>();
	private final Map<Integer, JLabel> glamourIconLabels = new HashMap<>();

	// Highlight animation state
	private static final int HIGHLIGHT_TICK_MS = 33;
	private Timer highlightTimer;
	private long highlightStartTime;
	private EquipHighlightGlamour highlightGlamour;
	private int highlightGlamourIndex;
	private BufferedImage highlightBaseIcon;
	private BufferedImage highlightPeakIcon;

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
	private boolean editingCancelled;

	public PlateRowPanel(Plate plate, IconService iconService, Glamourer glamourer, Config config,
						 Gson gson, float iconScale, Consumer<Plate> onAddItemRequest,
						 Consumer<Plate> onDeleteRequest, Runnable onExpandToggle,
						 ItemDragDropHandler.ItemMoveCallback onItemMoved,
						 BiConsumer<Plate, Boolean> onEnableChanged,
						 BiConsumer<Plate, DisplayStyle> onDisplayStyleChanged,
						 BiConsumer<Plate, Integer> onGlamourRemoved,
						 BiConsumer<Plate, IconStyle> onIconStyleChanged)
	{
		this(plate, iconService, glamourer, config, gson, iconScale, onAddItemRequest,
			onDeleteRequest, onExpandToggle, onItemMoved, onEnableChanged,
			onDisplayStyleChanged, onGlamourRemoved, onIconStyleChanged, false);
	}

	public PlateRowPanel(Plate plate, IconService iconService, Glamourer glamourer, float iconScale, Runnable onExpandToggle)
	{
		this(plate, iconService, glamourer, null, null, iconScale, null, null, onExpandToggle, null, null, null, null, null, true);
	}

	private PlateRowPanel(Plate plate, IconService iconService, Glamourer glamourer, Config config,
						  Gson gson, float iconScale, Consumer<Plate> onAddItemRequest,
						  Consumer<Plate> onDeleteRequest, Runnable onExpandToggle,
						  ItemDragDropHandler.ItemMoveCallback onItemMoved,
						  BiConsumer<Plate, Boolean> onEnableChanged,
						  BiConsumer<Plate, DisplayStyle> onDisplayStyleChanged,
						  BiConsumer<Plate, Integer> onGlamourRemoved,
						  BiConsumer<Plate, IconStyle> onIconStyleChanged, boolean preview)
	{
		this.plate = plate;
		this.iconService = iconService;
		this.glamourer = glamourer;
		this.config = config;
		this.gson = gson;
		this.iconScale = iconScale;
		this.onAddItemRequest = onAddItemRequest;
		this.onGlamourRemoved = onGlamourRemoved;
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

			// Right side: display style icon + toggle
			JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
			rightPanel.setOpaque(false);

			enabledToggle = new ToggleSwitch(plate.isEnabled());
			enabledToggle.addActionListener(e -> {
				boolean enabled = enabledToggle.isSelected();
				if (onEnableChanged != null)
				{
					onEnableChanged.accept(plate, enabled);
				}
			});
			if (plate.getIconStyle() == IconStyle.WORN_ONLY)
			{
				JLabel iconStyleLabel = new JLabel(ImageIcons.getWornOnlyIcon());
				iconStyleLabel.setToolTipText(IconStyle.WORN_ONLY.getTooltip());
				iconStyleLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
				rightPanel.add(iconStyleLabel);
			}
			if (plate.getDisplayStyle() == DisplayStyle.GLOBAL)
			{
				JLabel displayStyleLabel = new JLabel(ImageIcons.getDisplayStyleIcon(DisplayStyle.GLOBAL));
				displayStyleLabel.setToolTipText(DisplayStyle.GLOBAL.getTooltip());
				displayStyleLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
				rightPanel.add(displayStyleLabel);
			}

			rightPanel.add(enabledToggle);

			headerPanel.add(rightPanel, BorderLayout.EAST);

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

			DisplayStyle oppositeStyle = plate.getDisplayStyle() == DisplayStyle.LOCAL
				? DisplayStyle.GLOBAL
				: DisplayStyle.LOCAL;
			JMenuItem displayStyleItem = new JMenuItem(oppositeStyle.getAction(),
				ImageIcons.getDisplayStyleIcon(oppositeStyle));
			displayStyleItem.setIconTextGap(8);
			displayStyleItem.addActionListener(e -> {
				if (onDisplayStyleChanged != null)
				{
					onDisplayStyleChanged.accept(plate, oppositeStyle);
				}
			});
			popupMenu.add(displayStyleItem);

			IconStyle oppositeIconStyle = plate.getIconStyle() == IconStyle.NORMAL
				? IconStyle.WORN_ONLY
				: IconStyle.NORMAL;
			JMenuItem iconStyleItem = new JMenuItem(oppositeIconStyle.getAction(),
				ImageIcons.getIconStyleIcon(oppositeIconStyle));
			iconStyleItem.setIconTextGap(8);
			iconStyleItem.addActionListener(e -> {
				if (onIconStyleChanged != null)
				{
					onIconStyleChanged.accept(plate, oppositeIconStyle);
				}
			});
			popupMenu.add(iconStyleItem);

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

		add(headerPanel, BorderLayout.NORTH);

		// Details panel (visibility based on expanded state)
		detailsPanel = new JPanel();
		detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
		detailsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		detailsPanel.setBorder(new EmptyBorder(5, 3, 3, 3));
		detailsPanel.setVisible(expanded);

		add(detailsPanel, BorderLayout.CENTER);

		// Collapse all items by default on first load
		collapsedItemIds.addAll(plate.getGlamours().stream().map(Glamour::getPrimaryItemId).collect(Collectors.toSet()));

		rebuildDetailsPanel();
	}

	private void exportToClipboard(Plate plate, boolean verbose)
	{
		var data = plate.getData(verbose);
		data.setEnabled(null);
		data.setExpanded(null);
		data.setDisplayStyle(null);
		data.setIconStyle(null);
		String json = gson.toJson(data);
		StringSelection selection = new StringSelection(json);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
	}

	private void toggleExpanded()
	{
		expanded = !expanded;
		ImageIcons.setExpandIcon(expandButton, expanded);
		plateIconLabel.setVisible(!expanded);
		plate.setExpanded(expanded);
		if (expanded)
		{
			rebuildDetailsPanel();
		}
		detailsPanel.setVisible(expanded);
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
		collapsedItemIds.clear();
		collapsedItemIds.addAll(other.collapsedItemIds);
		expandedGroups.clear();
		expandedGroups.addAll(other.expandedGroups);
		rebuildDetailsPanel();
	}

	public void setExpanded(boolean expanded)
	{
		if (expanded)
		{
			collapsedItemIds.clear();
		}
		else
		{
			collapsedItemIds.addAll(plate.getGlamours().stream().map(Glamour::getPrimaryItemId).collect(Collectors.toSet()));
		}
		rebuildDetailsPanel();
		if (this.expanded != expanded)
		{
			toggleExpanded();
		}
	}

	public void ensureExpanded()
	{
		if (!expanded)
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
		stopHighlight();
		updatePlateIcon();
		glamourIconLabels.clear();
		detailsPanel.removeAll();

		if (!expanded)
		{
			detailsPanel.revalidate();
			detailsPanel.repaint();
			return;
		}

		int i = 0;
		for (Glamour glam : plate.getGlamours())
		{
			GlamourVisibility visibility = preview ? GlamourVisibility.UNSPECIFIED : plate.getVisibility(glam);
			JPanel itemPanel = createGlamourItemPanel(glam, i, visibility);
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

	private JPanel createGlamourItemPanel(Glamour glam, int glamourIndex, GlamourVisibility visibility)
	{
		JLabel itemNameLabel = new JLabel(glam.getItemName(), SwingConstants.LEFT);
		itemNameLabel.setForeground(Color.WHITE);
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
		boolean itemExpanded = !collapsedItemIds.contains(glam.getPrimaryItemId());

		panel.add(createItemHeaderRow(itemNameLabel, glam, glamourIndex, itemExpanded, visibility), BorderLayout.NORTH);

		if (itemExpanded)
		{
			JPanel bodyPanel = new JPanel(new BorderLayout(5, 0));
			bodyPanel.setOpaque(false);
			bodyPanel.add(createItemIconLabel(glam, glamourIndex), BorderLayout.WEST);
			bodyPanel.add(createColorsPanel(glam, glamourIndex), BorderLayout.CENTER);
			panel.add(bodyPanel, BorderLayout.CENTER);
		}
		return panel;
	}

	private JLabel createItemIconLabel(Glamour glam, int glamourIndex)
	{
		JLabel iconLabel = new JLabel();
		ImageIcons.setScaledIcon(iconLabel, iconService.getIcon(glam), iconScale);
		glamourIconLabels.put(glamourIndex, iconLabel);
		return iconLabel;
	}

	private JPanel createItemHeaderRow(JLabel itemNameLabel, Glamour glam, int glamourIndex, boolean itemExpanded, GlamourVisibility visibility)
	{
		final var itemId = glam.getPrimaryItemId();
		JPanel headerRow = new JPanel(new BorderLayout(2, 0));
		headerRow.setOpaque(false);

		JButton itemExpandButton = new JButton();
		ImageIcons.setExpandIcon(itemExpandButton, itemExpanded);
		itemExpandButton.addActionListener(e -> {
			if (collapsedItemIds.contains(itemId))
			{
				collapsedItemIds.remove(itemId);
			}
			else
			{
				collapsedItemIds.add(itemId);
			}
			rebuildDetailsPanel();
		});
		headerRow.add(itemExpandButton, BorderLayout.WEST);

		JPanel centerPanel = new JPanel(new BorderLayout(0, 0));
		centerPanel.setOpaque(false);

		centerPanel.add(itemNameLabel, BorderLayout.CENTER);

		if (visibility != GlamourVisibility.UNSPECIFIED)
		{
			JLabel visibilityLabel = new JLabel(ImageIcons.getVisibilityIcon(visibility));
			visibilityLabel.setToolTipText(visibility.getTooltip());
			visibilityLabel.setBorder(new EmptyBorder(5, 4, 3, 0));
			centerPanel.add(visibilityLabel, BorderLayout.EAST);
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
				if (onGlamourRemoved != null)
				{
					onGlamourRemoved.accept(plate, glamourIndex);
				}
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
		var settings = new ColorGroupSettings(config.colorGroupHueDist(), config.colorGroupSatDist(), config.colorGroupLumDist(), config.colorGroupSeparateGrayscale());
		List<ColorGroup> groups = ColorGroup.groupColors(pairs, settings);
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

		List<TextureReplacement> textures = glam.getTextureReplacements();
		if (!textures.isEmpty())
		{
			colorsPanel.add(Box.createVerticalStrut(2));
			int textureNum = 1;
			for (int textureIdx = 0; textureIdx < textures.size(); textureIdx++)
			{
				TextureReplacement tr = textures.get(textureIdx);
				if (textureNum > 1)
				{
					colorsPanel.add(Box.createVerticalStrut(2));
				}

				TextureLabel textureLabel = new TextureLabel(glam.getItemName() + " Texture " + textureNum, tr);
				textureLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
				textureLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, textureLabel.getPreferredSize().height));
				if (preview)
				{
					textureLabel.setViewOnly();
				}
				else
				{
					final int idx = textureIdx;
					textureLabel.setOnTextureChange(newTexture -> updateSingleTexture(glamourIndex, idx, newTexture));
					textureLabel.setOnTexturePreview(newTexture -> previewSingleTexture(glamourIndex, idx, newTexture));
					setHoverCallbacks(glam, glamourIndex, HighlightMask.forColorIndices(Set.of()), textureLabel);
				}
				colorsPanel.add(textureLabel);
				textureNum++;
			}
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
				colorLabel.setOnColorPreview(newColor -> previewSingleColor(glamourIndex, colorIdx, newColor));
				setHoverCallbacks(glam, glamourIndex, HighlightMask.forColorIndices(Set.of(colorIdx)), colorLabel);
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
			colorLabel.setOnColorPreview(newColor -> previewGroupColors(glamourIndex, group, newColor));
			colorLabel.setOnRevert(() -> revertGroupColors(glamourIndex, group, groupReplacements));
			setHoverCallbacks(glam, glamourIndex, HighlightMask.forColorIndices(new HashSet<>(group.getColorIndices())), colorLabel);
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
				colorLabel.setOnColorPreview(newColor -> previewSingleColor(glamourIndex, colorIdx, newColor));
				setHoverCallbacks(glam, glamourIndex, HighlightMask.forColorIndices(Set.of(colorIdx)), colorLabel);
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

	private void updateSingleTexture(int glamourIndex, int textureIdx, short newTexture)
	{
		plate.updateGlamourTexture(glamourIndex, textureIdx, newTexture);
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

	private void previewSingleColor(int glamourIndex, int colorIdx, short newColor)
	{
		Glamour glam = plate.getGlamours().get(glamourIndex);
		glam.replaceColorIndex(colorIdx, newColor);
		applyAndUpdateIcon(glamourIndex, glam);
	}

	private void previewGroupColors(int glamourIndex, ColorGroup group, short newColor)
	{
		Glamour glam = plate.getGlamours().get(glamourIndex);
		for (int i = 0; i < group.getColorIndices().size(); i++)
		{
			int colorIdx = group.getColorIndices().get(i);
			short adjustedColor = group.calculateNewColor(newColor, i);
			glam.replaceColorIndex(colorIdx, adjustedColor);
		}
		applyAndUpdateIcon(glamourIndex, glam);
	}

	private void previewSingleTexture(int glamourIndex, int textureIdx, short newTexture)
	{
		Glamour glam = plate.getGlamours().get(glamourIndex);
		glam.replaceTextureIndex(textureIdx, newTexture);
		applyAndUpdateIcon(glamourIndex, glam);
	}

	private void applyAndUpdateIcon(int glamourIndex, Glamour glam)
	{
		if (plate.isEnabled())
		{
			glamourer.apply(glam, plate.getDisplayStyle());
		}
		JLabel iconLabel = glamourIconLabels.get(glamourIndex);
		if (iconLabel != null)
		{
			ImageIcons.setScaledIcon(iconLabel, iconService.getIcon(glam), iconScale);
		}
	}

	private void setHoverCallbacks(Glamour glam, int glamourIndex, HighlightMask mask, Hoverable hoverable)
	{
		hoverable.setOnHoverStart(() -> startHighlight(glam, glamourIndex, mask));
		hoverable.setOnHoverEnd(this::stopHighlight);
	}

	private void startHighlight(Glamour glam, int glamourIndex, HighlightMask mask)
	{
		stopHighlight();
		var highlight = new EquipHighlightGlamour(glam, mask);
		highlightGlamour = highlight;
		highlightGlamourIndex = glamourIndex;
		highlight.setLerp(0f);
		highlightBaseIcon = iconService.getIcon(highlight);
		highlight.setLerp(1f);
		highlightPeakIcon = iconService.getIcon(highlight);
		highlightStartTime = System.currentTimeMillis();

		if (plate.isEnabled())
		{
			glamourer.setLocalEquipmentOverride(highlight);
		}

		if (highlightTimer == null)
		{
			highlightTimer = new Timer(HIGHLIGHT_TICK_MS, e -> onHighlightTick());
			highlightTimer.setRepeats(true);
		}
		highlightTimer.start();
		onHighlightTick();
	}

	private void stopHighlight()
	{
		if (highlightTimer != null)
		{
			highlightTimer.stop();
		}
		if (highlightGlamour != null)
		{
			JLabel iconLabel = glamourIconLabels.get(highlightGlamourIndex);
			if (iconLabel != null)
			{
				var originalGlam = plate.getGlamours().get(highlightGlamourIndex);
				ImageIcons.setScaledIcon(iconLabel, iconService.getIcon(originalGlam), iconScale);
			}
			glamourer.clearLocalEquipmentOverride();
			highlightGlamour = null;
		}
	}

	private void onHighlightTick()
	{
		if (highlightGlamour == null)
		{
			return;
		}
		float elapsed = (System.currentTimeMillis() - highlightStartTime) / HIGHLIGHT_PERIOD_MS;
		float t = 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * elapsed);

		JLabel iconLabel = glamourIconLabels.get(highlightGlamourIndex);
		if (iconLabel != null)
		{
			BufferedImage blended = ImageIcons.blendImages(highlightBaseIcon, highlightPeakIcon, t);
			ImageIcons.setScaledIcon(iconLabel, blended, iconScale);
		}

		if (plate.isEnabled())
		{
			highlightGlamour.setLerp(t);
			glamourer.setLocalEquipmentOverride(highlightGlamour);
		}
	}

	private static boolean isTextTruncated(JLabel label)
	{
		FontMetrics fm = label.getFontMetrics(label.getFont());
		Insets insets = label.getInsets();
		int availableWidth = label.getWidth() - insets.left - insets.right;
		return fm.stringWidth(label.getText()) > availableWidth;
	}
}
