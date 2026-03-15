package io.huze.glamourer.glam;

import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.item.DedupeItemComposition;
import io.huze.glamourer.item.ItemSheet;
import io.huze.glamourer.texture.TextureReplacement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ColorTextureOverride;
import net.runelite.api.ItemComposition;

@Slf4j
public class Glamour
{
	private final DedupeItemComposition itemComposition;
	private final GlamState backup;
	protected final GlamState staged;
	private volatile boolean dirty;

	public GlamourData getData(boolean verbose)
	{
		var colorReplacements = getColorReplacements();
		if (!verbose)
		{
			// Only keep modelColor for entries with duplicate original colors
			Set<Short> seen = new HashSet<>();
			Set<Short> duplicates = new HashSet<>();
			for (var cr : colorReplacements)
			{
				if (!seen.add(cr.getOriginal()))
				{
					duplicates.add(cr.getOriginal());
				}
			}
			for (var cr : colorReplacements)
			{
				if (!duplicates.contains(cr.getOriginal()))
				{
					cr.setModel(null);
				}
			}
		}

		var colorArray = colorReplacements.stream()
			.filter((cr) -> verbose || cr.hasChanged())
			.toArray(ColorReplacement[]::new);

		var textureArray = getTextureReplacements().stream()
			.filter(tr -> verbose || tr.hasChanged())
			.toArray(TextureReplacement[]::new);
		textureArray = textureArray.length > 0 ? textureArray : null;

		// item composition might be edited at this point, must use the backup glamstate as the dedupekey
		return new GlamourData(
			backup.toDedupeKey(itemComposition.getMembersName()),
			colorArray,
			textureArray,
			null);
	}

	/// Load glamour from serialized GlamourData.
	/// The itemComposition must be pure, so any existing glamour must be reverted before calling this method.
	public static Glamour load(ItemSheet sheet, DedupeItemComposition itemComposition, GlamourData data)
	{
		var glamour = new Glamour(sheet, itemComposition);
		var savedPairs = data.getColorReplacements();
		if (savedPairs != null)
		{
			var stagedPairs = glamour.staged.getColorReplacements();
			var matched = new boolean[stagedPairs.size()];
			for (ColorReplacement saved : savedPairs)
			{
				boolean found = false;
				// Prefer model color match to unambiguously identify the slot,
				// even if original colors are duplicated or reordered.
				Short modelColor = saved.getModel();
				if (modelColor != null)
				{
					for (int i = 0; i < stagedPairs.size(); i++)
					{
						if (!matched[i] && stagedPairs.get(i).getOriginal() == modelColor)
						{
							glamour.replaceColorIndex(i, saved.getReplacement());
							matched[i] = true;
							found = true;
							break;
						}
					}
				}
				// Fallback: match by original color value, first unmatched slot wins.
				// Handles data from before model color was introduced.
				if (!found)
				{
					for (int i = 0; i < stagedPairs.size(); i++)
					{
						if (matched[i])
						{
							continue;
						}
						var pair = stagedPairs.get(i);
						if (pair.getReplacement() == saved.getOriginal() || pair.getOriginal() == saved.getOriginal())
						{
							glamour.replaceColorIndex(i, saved.getReplacement());
							matched[i] = true;
							break;
						}
					}
				}
			}
		}

		var savedTextures = data.getTextureReplacements();
		if (savedTextures != null)
		{
			var stagedTextures = glamour.staged.getTextureReplacements();
			var matched = new boolean[stagedTextures.size()];
			for (TextureReplacement saved : savedTextures)
			{
				for (int i = 0; i < stagedTextures.size(); i++)
				{
					if (!matched[i] && stagedTextures.get(i).getOriginal() == saved.getOriginal())
					{
						glamour.replaceTextureIndex(i, saved.getReplacement());
						matched[i] = true;
						break;
					}
				}
			}
		}
		return glamour;
	}

	/// The itemComposition must be pure, so any existing glamour must be reverted before calling this method.
	public static Glamour start(ItemSheet sheet, DedupeItemComposition itemComposition) {
		return new Glamour(sheet, itemComposition);
	}

	private Glamour(ItemSheet sheet, DedupeItemComposition itemComposition)
	{
		this.itemComposition = itemComposition;
		this.backup = GlamState.backup(itemComposition);
		staged = GlamState.initialize(itemComposition, sheet.getModels(itemComposition.getId()));
	}

	protected Glamour(Glamour source)
	{
		this.itemComposition = source.itemComposition;
		this.backup = source.backup;
		this.staged = source.staged;
	}

	public int getPrimaryItemId()
	{
		return itemComposition.getId();
	}

	public Collection<Integer> getItemIds()
	{
		return itemComposition.getIds();
	}

	public String getItemName()
	{
		return itemComposition.getMembersName();
	}

	public boolean isEquivalent(Glamour other)
	{
		return this == other || other instanceof WornOnlyGlamour && this == ((WornOnlyGlamour) other).source;
	}

	protected void apply(ItemComposition itemComposition)
	{
		staged.applyTo(itemComposition);
	}

	protected void apply()
	{
		apply(itemComposition);
	}

	private boolean sizeMismatch(@Nonnull ColorTextureOverride override)
	{
		{
			var expectedSize = staged.getColorReplacements().size();
			var actualSize = override.getColorToReplaceWith().length;
			if (expectedSize != actualSize)
			{
				log.warn("Mismatched color replace size ({} != {}) for item {}:{}", expectedSize, actualSize, getPrimaryItemId(), getItemName());
				return true;
			}
		}
		{
			var expectedSize = staged.getTextureReplacements().size();
			var actualSize = override.getTextureToReplaceWith().length;
			if (expectedSize != actualSize)
			{
				log.warn("Mismatched texture replace size ({} != {}) for item {}:{}", expectedSize, actualSize, getPrimaryItemId(), getItemName());
				return true;
			}
		}
		return false;
	}

	protected void applyOriginal(@Nonnull ColorTextureOverride override)
	{
		if (sizeMismatch(override))
		{
			return;
		}
		staged.applyOriginalTo(override);

		var overrideColors = override.getColorToReplaceWith();
		for (int i = 0; i < overrideColors.length; i++)
		{
			var modelColor = overrideColors[i];
			for (var backupReplacement : backup.getColorReplacements())
			{
				if (backupReplacement.getOriginal() == modelColor)
				{
					overrideColors[i] = backupReplacement.getReplacement();
				}
			}
		}
	}

	protected void applyReplacement(@Nonnull ColorTextureOverride override)
	{
		if (sizeMismatch(override))
		{
			return;
		}
		staged.applyTo(override);
	}

	protected void revert()
	{
		backup.applyTo(itemComposition);
	}

	public void replaceColorIndex(int index, short after)
	{
		staged.replaceColor(index, after);
		dirty = true;
	}

	public void replaceTextureIndex(int index, short after)
	{
		staged.replaceTexture(index, after);
		dirty = true;
	}

	public List<TextureReplacement> getTextureReplacements()
	{
		return staged.getTextureReplacements();
	}

	public List<ColorReplacement> getColorReplacements()
	{
		List<ColorReplacement> colorReplacements = new ArrayList<>();
		for (var stagedReplacement : staged.getColorReplacements())
		{
			var modelColor = stagedReplacement.getOriginal();
			var originalHsl = modelColor;
			for (var backupReplacement : backup.getColorReplacements())
			{
				if (backupReplacement.getOriginal() == originalHsl)
				{
					originalHsl = backupReplacement.getReplacement();
				}
			}
			var cr = new ColorReplacement(originalHsl, stagedReplacement.getReplacement());
			cr.setModel(modelColor);
			colorReplacements.add(cr);
		}
		return colorReplacements;
	}

	boolean isDirty()
	{
		return dirty;
	}

	boolean clearDirty()
	{
		final var prev = dirty;
		dirty = false;
		return prev;
	}

	GlamState snapshotState()
	{
		return staged.immutableDeepCopy();
	}

	GlamState getHighlightState(HighlightMask mask, float t)
	{
		return staged.immutableDeepCopyWithHighlight(mask, t);
	}
}
