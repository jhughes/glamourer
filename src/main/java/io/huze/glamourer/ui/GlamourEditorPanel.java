package io.huze.glamourer.ui;

import io.huze.glamourer.Config;
import io.huze.glamourer.color.ColorGroup;
import io.huze.glamourer.color.ColorGroupSettings;
import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.plate.Plate;
import io.huze.glamourer.render.RegionSelection;
import io.huze.glamourer.texture.TextureImages;
import io.huze.glamourer.texture.TextureReplacement;
import io.huze.glamourer.ui.colorpicker.ColorLabel;
import io.huze.glamourer.ui.colorpicker.GroupColorLabel;
import io.huze.glamourer.ui.colorpicker.SingleColorLabel;
import io.huze.glamourer.ui.texturepicker.TextureLabel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

public class GlamourEditorPanel extends JPanel
{
	private static final int GROUP_ROW_HEIGHT = ColorLabel.ROW_HEIGHT + 4;

	@Nonnull
	private final Plate plate;
	private final int itemId;
	@Nonnull
	private final TextureImages textureImages;
	@Nonnull
	private final Config config;
	private final boolean viewOnly;
	@Nonnull
	private final Runnable onPreview;
	@Nonnull
	private final Consumer<RegionSelection> onHighlightChanged;

	private final Set<Integer> expandedGroups = new HashSet<>();
	private boolean highlighting;
	private final List<Row> rows = new ArrayList<>();

	private static final class Row
	{
		final RegionSelection selection;
		final JComponent component;
		final Runnable showPicker;

		Row(RegionSelection selection, JComponent component, Runnable showPicker)
		{
			this.selection = selection;
			this.component = component;
			this.showPicker = showPicker;
		}
	}

	public GlamourEditorPanel(@Nonnull Plate plate,
							  int itemId,
							  @Nonnull TextureImages textureImages,
							  @Nonnull Config config,
							  boolean viewOnly,
							  @Nonnull Runnable onPreview,
							  @Nonnull Consumer<RegionSelection> onHighlightChanged)
	{
		this.plate = plate;
		this.itemId = itemId;
		this.textureImages = textureImages;
		this.config = config;
		this.viewOnly = viewOnly;
		this.onPreview = onPreview;
		this.onHighlightChanged = onHighlightChanged;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		rebuild();
	}

	@Nullable
	public Glamour getGlamour()
	{
		int index = getGlamourIndex();
		return index == -1 ? null : plate.getGlamours().get(index);
	}

	private int getGlamourIndex()
	{
		var glamours = plate.getGlamours();
		for (int index = 0; index < glamours.size(); index++)
		{
			if (glamours.get(index).getPrimaryItemId() == itemId)
			{
				return index;
			}
		}
		return -1;
	}

	public void rebuild()
	{
		stopHighlight();
		removeAll();
		rows.clear();
		Glamour glam = getGlamour();
		if (glam != null)
		{
			addColorRows(glam);
			addTextureRows(glam);
		}
		revalidate();
		repaint();
	}

	public void dispose()
	{
		stopHighlight();
	}

	public void openPicker(@Nonnull RegionSelection picked)
	{
		var row = findRow(picked);
		if (row != null)
		{
			row.component.scrollRectToVisible(new Rectangle(0, 0, row.component.getWidth(), row.component.getHeight()));
			row.showPicker.run();
		}
	}

	@Nonnull
	public RegionSelection getRowSelection(@Nonnull RegionSelection picked)
	{
		var row = findRow(picked);
		return row == null ? picked : row.selection;
	}

	@Nullable
	private Row findRow(@Nonnull RegionSelection picked)
	{
		for (Row row : rows)
		{
			if (row.selection.overlaps(picked))
			{
				return row;
			}
		}
		return null;
	}

	private void addColorRows(@Nonnull Glamour glam)
	{
		List<ColorReplacement> pairs = glam.getColorReplacements();
		var settings = new ColorGroupSettings(config.colorGroupHueDist(), config.colorGroupSatDist(),
			config.colorGroupLumDist(), config.colorGroupSeparateGrayscale());
		List<ColorGroup> groups = ColorGroup.groupColors(pairs, settings);

		int groupNum = 0;
		int displayNum = 1;
		for (ColorGroup group : groups)
		{
			if (groupNum > 0)
			{
				add(Box.createVerticalStrut(2));
			}

			boolean isMultiColor = group.getColorIndices().size() > 1;
			boolean isCoherent = group.areReplacementsCoherent(pairs);
			boolean isGroupExpanded = expandedGroups.contains(groupNum);

			if (isMultiColor && isCoherent)
			{
				if (isGroupExpanded)
				{
					displayNum = addExpandedGroupRows(glam, groupNum, group, pairs, displayNum);
				}
				else
				{
					addCollapsedGroupRow(glam, groupNum, group, pairs);
					displayNum += group.getColorIndices().size();
				}
			}
			else
			{
				displayNum = addSingleColorRows(glam, group, pairs, displayNum);
			}
			groupNum++;
		}
	}

	private void addTextureRows(@Nonnull Glamour glam)
	{
		List<TextureReplacement> textures = glam.getTextureReplacements();
		if (textures.isEmpty())
		{
			return;
		}

		add(Box.createVerticalStrut(2));
		for (int textureIdx = 0; textureIdx < textures.size(); textureIdx++)
		{
			TextureReplacement texture = textures.get(textureIdx);
			if (textureIdx > 0)
			{
				add(Box.createVerticalStrut(2));
			}

			var textureLabel = new TextureLabel(glam.getItemName() + " Texture " + (textureIdx + 1), texture, textureImages);
			textureLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			textureLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, textureLabel.getPreferredSize().height));
			if (viewOnly)
			{
				textureLabel.setViewOnly();
			}
			else
			{
				final int idx = textureIdx;
				final short beforeTexture = texture.getReplacement();
				textureLabel.setOnTextureChange(newTexture -> {
					int index = getGlamourIndex();
					if (index != -1)
					{
						plate.commitGlamourTexture(index, idx, beforeTexture, newTexture);
					}
				});
				textureLabel.setOnTexturePreview(newTexture -> {
					Glamour current = getGlamour();
					if (current != null)
					{
						current.replaceTextureIndex(idx, newTexture);
						onPreview.run();
					}
				});
				addRow(RegionSelection.ofTexture(textureIdx), textureLabel, textureLabel::showPicker);
			}
			add(textureLabel);
		}
	}

	private int addExpandedGroupRows(@Nonnull Glamour glam, int groupNum, @Nonnull ColorGroup group,
									 @Nonnull List<ColorReplacement> pairs, int displayNum)
	{
		boolean isFirst = true;
		for (int colorIdx : group.getColorIndices())
		{
			ColorReplacement pair = pairs.get(colorIdx);
			if (!isFirst)
			{
				add(Box.createVerticalStrut(2));
			}

			JPanel rowPanel = new JPanel(new BorderLayout(3, 0));
			rowPanel.setOpaque(false);
			rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, GROUP_ROW_HEIGHT));

			if (isFirst)
			{
				rowPanel.add(createGroupToggle(groupNum, true), BorderLayout.WEST);
			}
			else
			{
				rowPanel.setBorder(new EmptyBorder(0, 20, 0, 0));
			}

			var colorLabel = new SingleColorLabel(glam.getItemName() + " Color " + displayNum, pair);
			bindSingleColor(glam, colorLabel, pair, colorIdx);
			rowPanel.add(colorLabel, BorderLayout.CENTER);

			add(rowPanel);
			isFirst = false;
			displayNum++;
		}
		return displayNum;
	}

	private void addCollapsedGroupRow(@Nonnull Glamour glam, int groupNum, @Nonnull ColorGroup group,
									  @Nonnull List<ColorReplacement> pairs)
	{
		JPanel groupHeaderPanel = new JPanel(new BorderLayout(3, 0));
		groupHeaderPanel.setOpaque(false);
		groupHeaderPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		groupHeaderPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, GROUP_ROW_HEIGHT));
		groupHeaderPanel.add(createGroupToggle(groupNum, false), BorderLayout.WEST);

		List<ColorReplacement> groupReplacements = new ArrayList<>();
		for (int idx : group.getColorIndices())
		{
			groupReplacements.add(pairs.get(idx));
		}

		String label = glam.getItemName() + " Group " + (groupNum + 1)
			+ " (" + group.getColorIndices().size() + " colors)";
		var colorLabel = new GroupColorLabel(label, groupReplacements);
		if (viewOnly)
		{
			colorLabel.setViewOnly();
		}
		else
		{
			final List<Integer> indices = group.getColorIndices();
			final int[] colorIndices = indices.stream().mapToInt(Integer::intValue).toArray();
			final short[] beforeColors = new short[indices.size()];
			for (int i = 0; i < indices.size(); i++)
			{
				beforeColors[i] = pairs.get(indices.get(i)).getReplacement();
			}
			colorLabel.setOnColorChange(newColor -> {
				short[] afterColors = new short[colorIndices.length];
				for (int i = 0; i < colorIndices.length; i++)
				{
					afterColors[i] = group.calculateNewColor(newColor, i);
				}
				commitColors(colorIndices, beforeColors, afterColors);
			});
			colorLabel.setOnColorPreview(newColor -> {
				Glamour current = getGlamour();
				if (current == null)
				{
					return;
				}
				for (int i = 0; i < group.getColorIndices().size(); i++)
				{
					current.replaceColorIndex(group.getColorIndices().get(i), group.calculateNewColor(newColor, i));
				}
				onPreview.run();
			});
			colorLabel.setOnRevert(() -> {
				short[] revertColors = new short[colorIndices.length];
				for (int i = 0; i < colorIndices.length; i++)
				{
					revertColors[i] = groupReplacements.get(i).getOriginal();
				}
				commitColors(colorIndices, beforeColors, revertColors);
			});
			addRow(RegionSelection.ofColors(Set.copyOf(indices)), colorLabel, colorLabel::showPicker);
		}
		groupHeaderPanel.add(colorLabel, BorderLayout.CENTER);

		add(groupHeaderPanel);
	}

	private int addSingleColorRows(@Nonnull Glamour glam, @Nonnull ColorGroup group,
								   @Nonnull List<ColorReplacement> pairs, int displayNum)
	{
		boolean first = true;
		for (int colorIdx : group.getColorIndices())
		{
			ColorReplacement pair = pairs.get(colorIdx);
			if (!first)
			{
				add(Box.createVerticalStrut(2));
			}
			first = false;

			var colorLabel = new SingleColorLabel(glam.getItemName() + " Color " + displayNum, pair);
			colorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			colorLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, colorLabel.getPreferredSize().height));
			bindSingleColor(glam, colorLabel, pair, colorIdx);
			add(colorLabel);
			displayNum++;
		}
		return displayNum;
	}

	private void bindSingleColor(@Nonnull Glamour glam, @Nonnull SingleColorLabel colorLabel,
								 @Nonnull ColorReplacement pair, int colorIdx)
	{
		if (viewOnly)
		{
			colorLabel.setViewOnly();
			return;
		}

		final short beforeColor = pair.getReplacement();
		colorLabel.setOnColorChange(newColor -> {
			int index = getGlamourIndex();
			if (index != -1)
			{
				plate.commitGlamourColor(index, colorIdx, beforeColor, newColor);
			}
		});
		colorLabel.setOnColorPreview(newColor -> {
			Glamour current = getGlamour();
			if (current != null)
			{
				current.replaceColorIndex(colorIdx, newColor);
				onPreview.run();
			}
		});
		addRow(RegionSelection.ofColor(colorIdx), colorLabel, colorLabel::showPicker);
	}

	private JButton createGroupToggle(int groupNum, boolean expanded)
	{
		JButton button = new JButton();
		ImageIcons.setExpandIcon(button, expanded);
		button.addActionListener(e -> {
			if (expanded)
			{
				expandedGroups.remove(groupNum);
			}
			else
			{
				expandedGroups.add(groupNum);
			}
			rebuild();
		});
		return button;
	}

	private void commitColors(int[] colorIndices, short[] beforeColors, short[] afterColors)
	{
		int index = getGlamourIndex();
		if (index != -1)
		{
			plate.commitGlamourColors(index, colorIndices, beforeColors, afterColors);
		}
	}

	private <T extends JComponent & Hoverable> void addRow(@Nonnull RegionSelection selection, @Nonnull T label,
														  @Nonnull Runnable showPicker)
	{
		label.setOnHoverStart(() -> startHighlight(selection));
		label.setOnHoverEnd(this::stopHighlight);
		rows.add(new Row(selection, label, showPicker));
	}

	private void startHighlight(@Nonnull RegionSelection selection)
	{
		highlighting = true;
		onHighlightChanged.accept(selection);
	}

	private void stopHighlight()
	{
		if (highlighting)
		{
			highlighting = false;
			onHighlightChanged.accept(null);
		}
	}
}
