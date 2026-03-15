package io.huze.glamourer.glam;

import io.huze.glamourer.Extensions;
import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.color.Colors;
import io.huze.glamourer.item.DedupeKey;
import io.huze.glamourer.texture.TextureReplacement;
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

	public GlamState immutableDeepCopy() {
		return new GlamState(
			model,
			colorFind.deepCopy(),
			colorReplace.deepCopy(),
			textureFind.deepCopy(),
			textureReplace.deepCopy(),
			true);
	}

	public GlamState immutableDeepCopyWithHighlight(HighlightMask mask, float t)
	{
		short[] highlightColorReplace = colorReplace.deepCopy();
		if (highlightColorReplace != null)
		{
			for (int i = 0; i < highlightColorReplace.length; i++)
			{
				var color = highlightColorReplace[i];
				var highlightColor = Colors.lerpHsl(color, Colors.highlight(color), t);
				var darkenColor = Colors.darken(color);
				highlightColorReplace[i] = mask.getColorIndices().contains(i) ? highlightColor : darkenColor;
			}
		}

		return new GlamState(
			model,
			colorFind.deepCopy(),
			highlightColorReplace,
			textureFind.deepCopy(),
			textureReplace.deepCopy(),
			true
		);
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
		// Merge colors and textures from inventory and equipment models.
		var colorSet = new HashSet<Short>();
		var textureSet = new HashSet<Short>();
		for (ModelData datum : modelData)
		{
			for (var color : datum.getFaceColors()) {
				colorSet.add(color);
			}
			if (datum.getFaceTextures() != null)
			{
				for (var texture : datum.getFaceTextures())
				{
					if (texture != -1)
					{
						textureSet.add(texture);
					}
				}
			}
		}
		var modelColors = colorSet.toShortArray();
		modelColors.sort();
		var modelTextures = textureSet.toShortArray();
		modelTextures.sort();

		return new GlamState(
			comp.getInventoryModel(),
			modelColors,
			applyReplacements(modelColors, comp.getColorToReplace(), comp.getColorToReplaceWith()),
			modelTextures,
			applyReplacements(modelTextures, comp.getTextureToReplace(), comp.getTextureToReplaceWith()),
			false
		);
	}

	private static short[] applyReplacements(short[] modelValues, short[] toFind, short[] toReplaceWith)
	{
		var replacements = modelValues.deepCopy();
		if (toFind != null && toReplaceWith != null)
		{
			for (int i = 0; i < modelValues.length; i++)
			{
				for (int j = 0; j < toFind.length; j++)
				{
					if (replacements[i] == toFind[j])
					{
						replacements[i] = toReplaceWith[j];
					}
				}
			}
		}
		return replacements;
	}
	
	void replaceColor(int i, short color)
	{
		if (immutable)
		{
			throw new IllegalStateException("Cannot modify immutable GlamState");
		}
		colorReplace[i] = color;
	}

	void replaceTexture(int i, short textureId)
	{
		if (immutable)
		{
			throw new IllegalStateException("Cannot modify immutable GlamState");
		}
		textureReplace[i] = textureId;
	}

	void applyOriginalTo(final ItemComposition comp)
	{
		comp.setColorToReplace(colorFind);
		comp.setColorToReplaceWith(colorFind);
		comp.setTextureToReplace(textureFind);
		comp.setTextureToReplaceWith(textureFind);
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

	void applyTo(final ColorTextureOverride override)
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

	public List<TextureReplacement> getTextureReplacements()
	{
		List<TextureReplacement> textureReplacements = new ArrayList<>();
		if (textureFind != null)
		{
			for (int i = 0; i < textureFind.length; i++)
			{
				textureReplacements.add(new TextureReplacement(textureFind[i], textureReplace[i]));
			}
		}
		return textureReplacements;
	}
}