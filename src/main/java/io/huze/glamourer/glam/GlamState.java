package io.huze.glamourer.glam;

import io.huze.glamourer.Extensions;
import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.color.Colors;
import io.huze.glamourer.texture.TextureReplacement;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ColorTextureOverride;
import net.runelite.api.ItemComposition;
import net.runelite.api.JagexColor;

@Slf4j
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

	public static GlamState initialize(final PrimedItem item)
	{
		return item.runOnMutableItemComp(() ->
		{
			item.reprime();
			var comp = item.getItemComposition();
			return new GlamState(
				comp.getInventoryModel(),
				comp.getColorToReplace().nullableClone(),
				comp.getColorToReplaceWith().nullableClone(),
				comp.getTextureToReplace().nullableClone(),
				comp.getTextureToReplaceWith().nullableClone(),
				false
			);
		});
	}

	/// Assumes the state is primed
	void applyColorReplacements(List<ColorReplacement> replacements)
	{
		// Snapshot the original colorReplace so that mutations from earlier replacements
		// don't cause later replacements to match the wrong index.
		var originalColorReplace = colorReplace.clone();
		var applied = new boolean[replacements.size()];
		for (int i = 0; i < replacements.size(); i++)
		{
			applied[i] = applyModelColorReplacement(replacements.get(i));
		}
		for (int i = 0; i < replacements.size(); i++)
		{
			if (!applied[i])
			{
				applied[i] = applyOriginalColorReplacement(replacements.get(i), originalColorReplace);
			}
		}
		for (int i = 0; i < applied.length; i++)
		{
			if (!applied[i])
			{
				log.warn("Failed to apply color replacement: {}", replacements.get(i));
			}
		}
	}

	private boolean applyModelColorReplacement(ColorReplacement replacement)
	{
		if (replacement.getModel() != null)
		{
			for (int i = 0; i < colorFind.length; i++)
			{
				if (colorFind[i] == replacement.getModel())
				{
					replaceColor(i, replacement.getReplacement());
					return true;
				}
			}
		}
		return false;
	}

	private boolean applyOriginalColorReplacement(ColorReplacement replacement, short[] originalColorReplace)
	{
		for (int i = 0; i < colorFind.length; i++)
		{
			if (originalColorReplace[i] == replacement.getOriginal())
			{
				replaceColor(i, replacement.getReplacement());
				return true;
			}
		}
		return false;
	}

	void replaceColor(int i, short color)
	{
		if (immutable)
		{
			throw new IllegalStateException("Cannot modify immutable GlamState");
		}
		colorReplace[i] = color;
	}

	/// Assumes the state is primed
	void applyTextureReplacements(List<TextureReplacement> replacements)
	{
		for (TextureReplacement replacement : replacements)
		{
			apply(replacement);
		}
	}

	private void apply(TextureReplacement replacement)
	{
		for (int i = 0; i < textureFind.length; i++)
		{
			if (textureFind[i] == replacement.getOriginal())
			{
				replaceTexture(i, replacement.getReplacement());
			}
		}
		log.warn("Failed to apply texture replacement: {}", replacement);
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
		var colorReplace = this.colorReplace.clone();
		breakColorChains(colorFind, colorReplace);
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
	 * Identity replacements (find[j] == replace[j]) are skipped since they cause no visual change.
	 * <p>
	 * When a chain is detected, the nearest non-chaining color is found by searching outward
	 * from the original value: luminance ±1, ±2, ..., then saturation ±1, ±2, ..., then hue.
	 */
	static void breakColorChains(@Nonnull short[] find, @Nonnull short[] replace)
	{
		assert find.length == replace.length;
		for (int i = 0; i < find.length; i++)
		{
			if (chainsForward(find, replace, i, replace[i]))
			{
				replace[i] = findNearestNonChaining(find, replace, i);
			}
		}
	}

	private static final int[] NUDGE_DIRECTIONS = {1, -1};
	private static final int HUE_SCALE = (Colors.MAX_LUM + 1) / (Colors.MAX_HUE + 1);
	private static final int SAT_SCALE = (Colors.MAX_LUM + 1) / (Colors.MAX_SAT + 1);
	private static short findNearestNonChaining(short[] find, short[] replace, int i)
	{
		final var original = replace[i];
		final var h = JagexColor.unpackHue(original);
		final var s = JagexColor.unpackSaturation(original);
		final var l = JagexColor.unpackLuminance(original);

		for (int d = 1; d <= Colors.MAX_LUM; d++)
		{
			for (int dir : NUDGE_DIRECTIONS)
			{
				int newL = l + d * dir;
				if (newL >= 0 && newL <= Colors.MAX_LUM)
				{
					short candidate = JagexColor.packHSL(h, s, newL);
					if (!chainsForward(find, replace, i, candidate))
					{
						return candidate;
					}
				}
			}

			if (d % HUE_SCALE == 0)
			{
				int hueOffset = d / HUE_SCALE;
				for (int dir : NUDGE_DIRECTIONS)
				{
					int newH = Math.floorMod(h + hueOffset * dir, Colors.MAX_HUE + 1);
					short candidate = JagexColor.packHSL(newH, s, l);
					if (!chainsForward(find, replace, i, candidate))
					{
						return candidate;
					}
				}
			}

			if (d % SAT_SCALE == 0)
			{
				int satOffset = d / SAT_SCALE;
				for (int dir : NUDGE_DIRECTIONS)
				{
					int newS = s + satOffset * dir;
					if (newS >= 0 && newS <= Colors.MAX_SAT)
					{
						short candidate = JagexColor.packHSL(h, newS, l);
						if (!chainsForward(find, replace, i, candidate))
						{
							return candidate;
						}
					}
				}
			}
		}

		return original;
	}

	private static boolean chainsForward(short[] find, short[] replace, int i, short value)
	{
		for (int j = i + 1; j < find.length; j++)
		{
			if (find[j] != replace[j] && value == find[j])
			{
				return true;
			}
		}
		return false;
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