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
import lombok.experimental.ExtensionMethod;
import net.runelite.api.ColorTextureOverride;
import net.runelite.api.ItemComposition;
import net.runelite.api.ModelData;

@ExtensionMethod({java.util.Arrays.class, Extensions.class})
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class GlamState
{
	private final int model;
	private final short[] colorFind;
	private final short[] colorReplace;
	private final short[] textureFind;
	private final short[] textureReplace;
	private final boolean immutable;

	public GlamState immutableCopy() {
		return new GlamState(
			model,
			colorFind,
			colorReplace,
			textureFind,
			textureReplace,
			true);
	}

	public String toDedupeKey(String membersName)
	{
		return new DedupeKey(
			DedupeKey.stripName(membersName),
			model,
			colorReplace,
			textureReplace).toString();
	}

	public static GlamState backup(final ItemComposition comp)
	{
		return new GlamState(
			comp.getInventoryModel(),
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
		colorReplace[i] = color;
	}

	void applyTo(final ItemComposition comp)
	{
		// TODO This makes it possible to treat variant items as dupes, but needs to be fixed for model glams.
		// No glamours use the inventory model currently, so don't apply it.
		// comp.setInventoryModel(model);
		comp.setColorToReplace(colorFind);
		comp.setColorToReplaceWith(colorReplace);
		comp.setTextureToReplace(textureFind);
		comp.setTextureToReplaceWith(textureReplace);
	}

	private static void arrayCopyEqualLength(short[] src, short[] dest)
	{
		if (src == null || dest == null)
		{
			return;
		}
		System.arraycopy(src, 0, dest, 0, src.length);
	}

	void applyOriginalTo(final ColorTextureOverride override)
	{
		arrayCopyEqualLength(colorFind, override.getColorToReplaceWith());
		arrayCopyEqualLength(textureFind, override.getTextureToReplaceWith());
	}

	void applyReplacementTo(final ColorTextureOverride override)
	{
		arrayCopyEqualLength(colorReplace, override.getColorToReplaceWith());
		arrayCopyEqualLength(textureReplace, override.getTextureToReplaceWith());
	}

	public List<ColorReplacement> getColorReplacements()
	{
		List<ColorReplacement> colorReplacements = new ArrayList<>();
		if (colorFind != null)
		{
			for (int i = 0; i < colorFind.length; i++)
			{
				colorReplacements.add(new ColorReplacement(colorFind[i], colorReplace[i]));
			}
		}
		return colorReplacements;
	}
}