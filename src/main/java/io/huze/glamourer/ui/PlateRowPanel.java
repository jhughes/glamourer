package io.huze.glamourer.ui;

import io.huze.glamourer.Config;
import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.glam.GlamourVisibility;
import io.huze.glamourer.glam.IconScale;
import io.huze.glamourer.glam.IconService;
import io.huze.glamourer.plate.DisplayStyle;
import io.huze.glamourer.plate.IconStyle;
import io.huze.glamourer.plate.Plate;
import io.huze.glamourer.texture.TextureImages;
import java.util.function.BiFunction;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.AbstractButton;
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
	@Nonnull
	private final Plate plate;
	@Nonnull
	private final IconService iconService;
	@Nullable
	private final BiFunction<Plate, Boolean, String> exportFunction;
	@Nonnull
	private final Config config;
	@Nullable
	private final ItemGlamourWindows itemWindows;
	@Nonnull
	private final TextureImages textureImages;
	@Nonnull
	private final Consumer<Plate> onAddItemRequest;
	@Nonnull
	private final BiConsumer<Plate, Integer> onGlamourRemoved;
	@Nonnull
	private final Runnable onExpandToggle;
	@Nullable
	private final ItemDragDropHandler.ItemMoveCallback onItemMoved;
	private final boolean preview;

	@Getter
	private boolean expanded;

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

	public PlateRowPanel(Plate plate, IconService iconService, Config config,
						 BiFunction<Plate, Boolean, String> exportFunction,
						 Consumer<Plate> onAddItemRequest,
						 Consumer<Plate> onDeleteRequest, Runnable onExpandToggle,
						 ItemDragDropHandler.ItemMoveCallback onItemMoved,
						 BiConsumer<Plate, Boolean> onEnableChanged,
						 BiConsumer<Plate, DisplayStyle> onDisplayStyleChanged,
						 BiConsumer<Plate, Integer> onGlamourRemoved,
						 BiConsumer<Plate, IconStyle> onIconStyleChanged,
						 ItemGlamourWindows itemWindows,
						 TextureImages textureImages)
	{
		this(plate, iconService, config, exportFunction, onAddItemRequest,
			onDeleteRequest, onExpandToggle, onItemMoved, onEnableChanged,
			onDisplayStyleChanged, onGlamourRemoved, onIconStyleChanged, itemWindows, textureImages, false);
	}

	public PlateRowPanel(Plate plate, IconService iconService, Config config,
						 TextureImages textureImages, Runnable onExpandToggle)
	{
		this(plate, iconService, config, null, nop -> {
			}, nop -> {
			}, onExpandToggle,
			null, (nop1, nop2) -> {
			}, (nop1, nop2) -> {
			}, (nop1, nop2) -> {
			}, (nop1, nop2) -> {
			}, null, textureImages, true);
	}

	private PlateRowPanel(@Nonnull Plate plate,
						  @Nonnull IconService iconService,
						  @Nonnull Config config,
						  @Nullable BiFunction<Plate, Boolean, String> exportFunction,
						  @Nonnull Consumer<Plate> onAddItemRequest,
						  @Nonnull Consumer<Plate> onDeleteRequest,
						  @Nonnull Runnable onExpandToggle,
						  @Nullable ItemDragDropHandler.ItemMoveCallback onItemMoved,
						  @Nonnull BiConsumer<Plate, Boolean> onEnableChanged,
						  @Nonnull BiConsumer<Plate, DisplayStyle> onDisplayStyleChanged,
						  @Nonnull BiConsumer<Plate, Integer> onGlamourRemoved,
						  @Nonnull BiConsumer<Plate, IconStyle> onIconStyleChanged,
						  @Nullable ItemGlamourWindows itemWindows,
						  @Nonnull TextureImages textureImages,
						  boolean preview)
	{
		this.plate = plate;
		this.iconService = iconService;
		this.config = config;
		this.exportFunction = exportFunction;
		this.itemWindows = itemWindows;
		this.textureImages = textureImages;
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
		expanded = false;
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
			enabledToggle.addActionListener(e -> onEnableChanged.accept(plate, enabledToggle.isSelected()));
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

			if (config.expandedRightClick())
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
			displayStyleItem.addActionListener(e -> onDisplayStyleChanged.accept(plate, oppositeStyle));
			popupMenu.add(displayStyleItem);

			IconStyle oppositeIconStyle = plate.getIconStyle() == IconStyle.NORMAL
				? IconStyle.WORN_ONLY
				: IconStyle.NORMAL;
			JMenuItem iconStyleItem = new JMenuItem(oppositeIconStyle.getAction(),
				ImageIcons.getIconStyleIcon(oppositeIconStyle));
			iconStyleItem.setIconTextGap(8);
			iconStyleItem.addActionListener(e -> onIconStyleChanged.accept(plate, oppositeIconStyle));
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

		rebuildDetailsPanel();
	}

	private void exportToClipboard(Plate plate, boolean verbose)
	{
		if (exportFunction != null)
		{
			String json = exportFunction.apply(plate, verbose);
			StringSelection selection = new StringSelection(json);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
		}
	}

	private void toggleExpanded()
	{
		expanded = !expanded;
		ImageIcons.setExpandIcon(expandButton, expanded);
		plateIconLabel.setVisible(!expanded);
		if (expanded)
		{
			rebuildDetailsPanel();
		}
		detailsPanel.setVisible(expanded);
		revalidate();
		onExpandToggle.run();
	}

	private void updatePlateIcon()
	{
		List<Glamour> glamours = plate.getGlamours();
		if (!glamours.isEmpty())
		{
			// Small whatever the configured scale, so a collapsed plate stays one slim row.
			ImageIcons.setIcon(plateIconLabel, iconService.getIcon(glamours.get(0), IconScale.PERCENT_75));
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
		if (expanded != other.expanded)
		{
			toggleExpanded();
		}
		else
		{
			rebuildDetailsPanel();
		}
	}

	public void setExpanded(boolean expanded)
	{
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
		updatePlateIcon();
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
			searchItemButton.addActionListener(e -> onAddItemRequest.accept(plate));

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
		panel.add(createItemHeaderRow(itemNameLabel, glam, glamourIndex, visibility), BorderLayout.NORTH);

		if (preview)
		{
			JPanel bodyPanel = new JPanel(new BorderLayout(5, 0));
			bodyPanel.setOpaque(false);
			bodyPanel.add(createItemIconLabel(glam), BorderLayout.WEST);
			bodyPanel.add(new GlamourEditorPanel(plate, glam.getPrimaryItemId(), textureImages, config, true,
				() -> {
				}, highlight -> {
				}), BorderLayout.CENTER);
			panel.add(bodyPanel, BorderLayout.CENTER);
		}
		else
		{
			makeRowClickable(panel, glam);
		}
		return panel;
	}

	/// The name and icon must stay listener-less so their clicks fall through to the row.
	private void makeRowClickable(JPanel panel, Glamour glam)
	{
		panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		panel.addMouseListener(rowPointerListener(panel, glam));
		followRowPointerIntoChildren(panel, panel, glam);
	}

	/// A child with listeners takes the pointer, so it must keep the row lit and open the window itself.
	private void followRowPointerIntoChildren(JPanel row, Container parent, Glamour glam)
	{
		for (Component child : parent.getComponents())
		{
			if (child.getMouseListeners().length > 0)
			{
				child.addMouseListener(rowPointerListener(row, child instanceof AbstractButton ? null : glam));
			}
			if (child instanceof Container)
			{
				followRowPointerIntoChildren(row, (Container) child, glam);
			}
		}
	}

	@Nonnull
	private MouseAdapter rowPointerListener(JPanel row, @Nullable Glamour opens)
	{
		return new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				if (opens != null && SwingUtilities.isLeftMouseButton(event) && itemWindows != null)
				{
					itemWindows.open(plate, opens);
				}
			}

			@Override
			public void mouseEntered(MouseEvent event)
			{
				setRowHovered(row, true);
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				setRowHovered(row, row.getMousePosition(true) != null);
			}
		};
	}

	private static void setRowHovered(JPanel row, boolean hovered)
	{
		row.setBackground(hovered ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARK_GRAY_COLOR);
	}

	private JLabel createItemIconLabel(Glamour glam)
	{
		JLabel iconLabel = new JLabel();
		ImageIcons.setIcon(iconLabel, iconService.getIcon(glam));
		return iconLabel;
	}

	private JPanel createItemHeaderRow(JLabel itemNameLabel, Glamour glam, int glamourIndex, GlamourVisibility visibility)
	{
		JPanel headerRow = new JPanel(new BorderLayout(2, 0));
		headerRow.setOpaque(false);

		JPanel centerPanel = new JPanel(new BorderLayout(0, 0));
		centerPanel.setOpaque(false);
		centerPanel.add(itemNameLabel, BorderLayout.CENTER);

		if (visibility != GlamourVisibility.UNSPECIFIED && visibility != GlamourVisibility.VISIBLE)
		{
			JLabel visibilityLabel = new JLabel(ImageIcons.getVisibilityIcon(visibility));
			visibilityLabel.setToolTipText(visibility.getTooltip());
			visibilityLabel.setBorder(new EmptyBorder(5, 4, 3, 0));
			centerPanel.add(visibilityLabel, BorderLayout.EAST);
		}

		if (!preview)
		{
			centerPanel.add(createItemIconLabel(glam), BorderLayout.WEST);
		}

		headerRow.add(centerPanel, BorderLayout.CENTER);

		if (!preview)
		{
			JButton removeButton = new JButton();
			ImageIcons.setBinIcon(removeButton);
			removeButton.setToolTipText("Remove");
			removeButton.addActionListener(e -> onGlamourRemoved.accept(plate, glamourIndex));
			JPanel removeWrapper = new JPanel(new GridBagLayout());
			removeWrapper.setOpaque(false);
			removeWrapper.add(removeButton);
			headerRow.add(removeWrapper, BorderLayout.EAST);
		}

		return headerRow;
	}

	private static boolean isTextTruncated(JLabel label)
	{
		FontMetrics fm = label.getFontMetrics(label.getFont());
		Insets insets = label.getInsets();
		int availableWidth = label.getWidth() - insets.left - insets.right;
		return fm.stringWidth(label.getText()) > availableWidth;
	}
}
