package io.huze.glamourer.glam;

import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.color.Colors;
import io.huze.glamourer.texture.TextureReplacement;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ColorTextureOverride;
import net.runelite.api.ItemComposition;

@Slf4j
class GlamState
{
	private final int model;
	@Nonnull
	private final short[] colorFind;
	@Nonnull
	private final short[] colorReplace;
	@Nonnull
	private final short[] textureFind;
	@Nonnull
	private final short[] textureReplace;
	private final boolean immutable;
	public final int colorLength;

	GlamState(final int model,
			  @Nonnull final short[] colorFind,
			  @Nonnull final short[] colorReplace,
			  @Nonnull final short[] textureFind,
			  @Nonnull final short[] textureReplace,
			  final boolean immutable)
	{
		this.model = model;
		assert colorFind.length == colorReplace.length;
		this.colorFind = colorFind;
		this.colorReplace = colorReplace;
		assert textureFind.length == textureReplace.length;
		this.textureFind = textureFind;
		this.textureReplace = textureReplace;
		this.immutable = immutable;
		this.colorLength = colorFind.length;
	}

	synchronized GlamState deepCopy(boolean immutable)
	{
		return new GlamState(
			model,
			colorFind.clone(),
			colorReplace.clone(),
			textureFind.clone(),
			textureReplace.clone(),
			immutable);
	}

	/// Assumes the state is primed
	synchronized void applyColorReplacements(List<ColorReplacement> replacements)
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

	synchronized short getColor(int i)
	{
		return colorReplace[i];
	}

	synchronized void replaceColor(int i, short color)
	{
		if (immutable)
		{
			throw new IllegalStateException("Cannot modify immutable GlamState");
		}
		colorReplace[i] = color;
	}

	/// Assumes the state is primed
	synchronized void applyTextureReplacements(List<TextureReplacement> replacements)
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
	}

	synchronized void replaceTexture(int i, short textureId)
	{
		if (immutable)
		{
			throw new IllegalStateException("Cannot modify immutable GlamState");
		}
		textureReplace[i] = textureId;
	}

	synchronized void applyOriginalTo(final ItemComposition comp)
	{
		comp.setColorToReplace(colorFind);
		comp.setColorToReplaceWith(colorFind);
		comp.setTextureToReplace(textureFind);
		comp.setTextureToReplaceWith(textureFind);
	}

	synchronized void applyTo(final ItemComposition comp)
	{
		var colorReplace = this.colorReplace.clone();
		Colors.breakColorChains(colorFind, colorReplace);
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

	synchronized void applyTo(final ColorTextureOverride override)
	{
		var colorReplace = override.getColorToReplaceWith();
		arrayCopyEqualLength(this.colorReplace, colorReplace);
		Colors.breakColorChains(colorFind, colorReplace);
		arrayCopyEqualLength(textureReplace, override.getTextureToReplaceWith());
	}

	@Nonnull
	public synchronized List<ColorReplacement> getColorReplacements()
	{
		List<ColorReplacement> colorReplacements = new ArrayList<>();
		for (int i = 0; i < colorFind.length; i++)
		{
			colorReplacements.add(new ColorReplacement(colorFind[i], colorReplace[i]));
		}
		return colorReplacements;
	}

	@Nonnull
	public synchronized List<TextureReplacement> getTextureReplacements()
	{
		List<TextureReplacement> textureReplacements = new ArrayList<>();
		for (int i = 0; i < textureFind.length; i++)
		{
			textureReplacements.add(new TextureReplacement(textureFind[i], textureReplace[i]));
		}
		return textureReplacements;
	}
}