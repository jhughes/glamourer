package io.huze.glamourer.glam;

import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.item.DedupeItemComposition;
import io.huze.glamourer.item.ItemSheet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ColorTextureOverride;
import net.runelite.api.ItemComposition;

@Slf4j
public class Glamour
{
	private final DedupeItemComposition itemComposition;
	private final GlamState backup;
	private final GlamState staged;
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
		// item composition might be edited at this point, must use the original glamstate as the dedupekey
		return new GlamourData(
			backup.toDedupeKey(itemComposition.getMembersName()),
			colorReplacements.stream()
				.filter((replacement) -> verbose || replacement.hasChanged())
				.collect(Collectors.toList())
				.toArray(new ColorReplacement[0]),
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
							glamour.replaceIndex(i, saved.getReplacement());
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
							glamour.replaceIndex(i, saved.getReplacement());
							matched[i] = true;
							break;
						}
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

	protected void apply(ItemComposition itemComposition)
	{
		staged.applyTo(itemComposition);
	}

	protected void apply()
	{
		apply(itemComposition);
	}

	protected void applyOriginal(@Nonnull ColorTextureOverride override)
	{
		var overrideColors = override.getColorToReplaceWith();
		var expectedSize = staged.getColorReplacements().size();
		var actualSize = overrideColors.length;
		if (expectedSize != actualSize) {
			log.warn("Mismatched color replace size ({} != {}) for item {}:{}", expectedSize, actualSize, getPrimaryItemId(), getItemName());
			return;
		}
		staged.applyOriginalTo(override);

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
		var expectedSize = staged.getColorReplacements().size();
		var actualSize = override.getColorToReplaceWith().length;
		if (expectedSize != actualSize) {
			log.warn("Mismatched color replace size ({} != {}) for item {}:{}", expectedSize, actualSize, getPrimaryItemId(), getItemName());
			return;
		}
		staged.applyReplacementTo(override);
	}

	protected void revert()
	{
		backup.applyTo(itemComposition);
	}

	public void replaceIndex(int index, short after)
	{
		staged.replace(index, after);
		dirty = true;
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

	GlamState getCurrentState()
	{
		return staged.immutableCopy();
	}
}
