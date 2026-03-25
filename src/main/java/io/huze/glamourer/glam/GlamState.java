package io.huze.glamourer.glam;

import io.huze.glamourer.Extensions;
import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.color.Colors;
import io.huze.glamourer.item.DedupeKey;
import io.huze.glamourer.item.DedupeItemComposition;
import io.huze.glamourer.texture.TextureReplacement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import javax.annotation.Nonnull;
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
			colorFind.clone(),
			colorReplace.clone(),
			textureFind.clone(),
			textureReplace.clone(),
			true);
	}

	public GlamState immutableDeepCopyWithHighlight(HighlightMask mask, float t)
	{
		short[] highlightColorReplace = colorReplace.clone();
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
			colorFind.clone(),
			highlightColorReplace,
			textureFind.clone(),
			textureReplace.clone(),
			true
		);
	}

	public String toDedupeKey(String membersName)
	{
		return DedupeKey.fromComponents(
			membersName,
			model,
			colorReplace,
			textureReplace);
	}

	public static GlamState backup(final ItemComposition comp)
	{
		return new GlamState(
			comp.getInventoryModel(),
			comp.getColorToReplace().nullableClone(),
			comp.getColorToReplaceWith().nullableClone(),
			comp.getTextureToReplace().nullableClone(),
			comp.getTextureToReplaceWith().nullableClone(),
			true
		);
	}

	public static GlamState backup(final DedupeItemComposition comp)
	{
		return new GlamState(
			comp.getInventoryModel(),
			comp.getColorToReplace().nullableClone(),
			comp.getColorToReplaceWith().nullableClone(),
			comp.getTextureToReplace().nullableClone(),
			comp.getTextureToReplaceWith().nullableClone(),
			true
		);
	}

	public static GlamState initialize(final DedupeItemComposition comp, Collection<ModelData> modelData)
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
		var replacements = modelValues.clone();
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
		applyTo(comp, true);
	}

	void applyTo(final ItemComposition comp, boolean breakChains)
	{
		var colorReplace = this.colorReplace;
		if (breakChains)
		{
			colorReplace = colorReplace.clone();
			breakColorChains(colorFind, colorReplace);
		}
		comp.setColorToReplace(colorFind);
		comp.setColorToReplaceWith(colorReplace);
		comp.setTextureToReplace(textureFind);
		comp.setTextureToReplaceWith(textureReplace);
	}

	void applyTo(final DedupeItemComposition comp, boolean breakChains)
	{
		var colorReplace = this.colorReplace;
		if (breakChains)
		{
			colorReplace = colorReplace.clone();
			breakColorChains(colorFind, colorReplace);
		}
		comp.setColorToReplace(colorFind);
		comp.setColorToReplaceWith(colorReplace);
		comp.setTextureToReplace(textureFind);
		comp.setTextureToReplaceWith(textureReplace);
	}

	private static void arrayCopyEqualLength(@Nonnull short[] src, @Nonnull short[] dest)
	{
		assert src.length == dest.length;
		System.arraycopy(src, 0, dest, 0, src.length);
	}

	void applyOriginalTo(final ColorTextureOverride override)
	{
		arrayCopyEqualLength(colorFind, override.getColorToReplaceWith());
		arrayCopyEqualLength(textureFind, override.getTextureToReplaceWith());
	}

	void applyTo(final ColorTextureOverride override)
	{
		var colorReplace = override.getColorToReplaceWith();
		arrayCopyEqualLength(this.colorReplace, colorReplace);
		breakColorChains(colorFind, colorReplace);
		arrayCopyEqualLength(textureReplace, override.getTextureToReplaceWith());
	}

	/**
	 * Nudge replace values to prevent sequential replacement from chaining.
	 * If replace[i] == find[j] for j > i, the engine applies both replacements to the same face.
	 * This nudges the replacement luminance by 1 to break any such chain.
	 * <p>
	 * Sorting the colors to minimize chaining is a better solution, but more difficult to implement with the player's
	 * color override system, and luminance nudging looks fine.
	 */
	static void breakColorChains(@Nonnull short[] find, @Nonnull short[] replace)
	{
		assert find.length == replace.length;
		final int n = find.length;
		for (int i = 0; i < n; i++)
		{
			final var start = replace[i];
			for (int j = i + 1; j < n; j++)
			{
				if (replace[i] == find[j])
				{
					replace[i] = Colors.nudgeLuminance(replace[i]);
					if (replace[i] == start)
					{
						break;
					}
					j = i; // restart check in case the nudged luminance matches another find color.
				}
			}
		}
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