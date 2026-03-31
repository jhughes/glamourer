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
		var applied = new boolean[replacements.size()];
		for (int i = 0; i < replacements.size(); i++)
		{
			applied[i] = applyModelColorReplacement(replacements.get(i));
		}
		for (int i = 0; i < replacements.size(); i++)
		{
			if (!applied[i])
			{
				applied[i] = apply(replacements.get(i));
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

	private boolean apply(ColorReplacement replacement)
	{
		for (int i = 0; i < colorFind.length; i++)
		{
			if (colorReplace[i] == replacement.getOriginal())
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