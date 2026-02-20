package io.huze.glamourer.glam;

import io.huze.glamourer.Extensions;
import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.item.DedupeKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.ExtensionMethod;
import net.runelite.api.ItemComposition;
import net.runelite.api.ModelData;

@ExtensionMethod({java.util.Arrays.class, Extensions.class})
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class GlamState
{
	@Getter
	private final int model;
	@Getter
	private final int xAng;
	@Getter
	private final int yAng;
	@Getter
	private final int zAng;
	private final short[] colorToReplace;
	private final short[] colorToReplaceWith;
	private final short[] textureToReplace;
	private final short[] textureToReplaceWith;
	private final boolean immutable;

	public String toDedupeKey(String membersName)
	{
		return new DedupeKey(
			DedupeKey.stripName(membersName),
			model,
			colorToReplaceWith,
			textureToReplaceWith).toString();
	}

	public static GlamState backup(final ItemComposition comp)
	{
		return new GlamState(
			comp.getInventoryModel(),
			comp.getXan2d(),
			comp.getYan2d(),
			comp.getZan2d(),
			comp.getColorToReplace().deepCopy(),
			comp.getColorToReplaceWith().deepCopy(),
			comp.getTextureToReplace().deepCopy(),
			comp.getTextureToReplaceWith().deepCopy(),
			true
		);
	}

	public static GlamState initialize(final ItemComposition comp, Collection<ModelData> modelData)
	{
		// Merge colors from inventory and equipment models.
		var colorSet = new HashSet<Short>();
		for (ModelData datum : modelData)
		{
			for (var color : datum.getFaceColors()) {
				colorSet.add(color);
			}
		}
		var modelColors = colorSet.toShortArray();
		modelColors.sort();

		var replacementColors = modelColors.deepCopy();
		{
			var colorsToReplace = comp.getColorToReplace();
			var colorsToReplaceWith = comp.getColorToReplaceWith();

			if (colorsToReplace != null && colorsToReplaceWith != null)
			{
				for (int i = 0; i < modelColors.length; i++)
				{
					for (int j = 0; j < colorsToReplace.length; j++)
					{
						if (replacementColors[i] == colorsToReplace[j])
						{
							replacementColors[i] = colorsToReplaceWith[j];
						}
					}
				}
			}
		}

		return new GlamState(
			comp.getInventoryModel(),
			comp.getXan2d(),
			comp.getYan2d(),
			comp.getZan2d(),
			modelColors,
			replacementColors,
			comp.getTextureToReplace().deepCopy(),
			comp.getTextureToReplaceWith().deepCopy(),
			false
		);
	}

	void replace(int i, short color)
	{
		if (immutable)
		{
			throw new IllegalStateException("Cannot modify immutable GlamState");
		}
		colorToReplaceWith[i] = color;
	}

	void applyTo(final ItemComposition comp)
	{
		// TODO This makes it possible to treat variant items as dupes, but needs to be fixed for model glams.
		// No glamours use the inventory model currently, so don't apply it.
		// comp.setInventoryModel(model);
		comp.setXan2d(xAng);
		comp.setYan2d(yAng);
		comp.setZan2d(zAng);
		comp.setColorToReplace(colorToReplace);
		comp.setColorToReplaceWith(colorToReplaceWith);
		comp.setTextureToReplace(textureToReplace);
		comp.setTextureToReplaceWith(textureToReplaceWith);
	}

	public List<ColorReplacement> getColorReplacements()
	{
		List<ColorReplacement> colorReplacements = new ArrayList<>();
		if (colorToReplace != null)
		{
			for (int i = 0; i < colorToReplace.length; i++)
			{
				colorReplacements.add(new ColorReplacement(colorToReplace[i], colorToReplaceWith[i]));
			}
		}
		return colorReplacements;
	}
}