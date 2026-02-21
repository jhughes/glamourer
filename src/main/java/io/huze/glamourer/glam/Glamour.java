package io.huze.glamourer.glam;

import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.item.DedupeItemComposition;
import io.huze.glamourer.item.ItemSheet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;

@Slf4j
public class Glamour
{
	private final DedupeItemComposition itemComposition;
	private final GlamState original;
	private final GlamState staged;

	public GlamourData getData(boolean verbose)
	{
		// item composition might be edited at this point, must use the original glamstate as the dedupekey
		return new GlamourData(
			original.toDedupeKey(itemComposition.getMembersName()),
			getColorReplacements().stream()
				.filter((replacement) -> verbose || replacement.hasChanged())
				.collect(Collectors.toList())
				.toArray(new ColorReplacement[0]),
			getReplacementModelId());
	}

	/// Load glamour from serialized GlamourData.
	/// The item composition could be modified by an existing active glamour. If so, it must be specified.
	public static Glamour load(ItemSheet sheet, DedupeItemComposition itemComposition, @Nullable Glamour active, GlamourData data)
	{
		var glamour = new Glamour(sheet, itemComposition, active != null ? active.original : null);
		// Restore saved colors by matching on original color
		var savedPairs = data.getColorReplacements();
		if (savedPairs != null)
		{
			for (ColorReplacement saved : savedPairs)
			{
				int i = 0;
				for (var pair : glamour.staged.getColorReplacements())
				{
					if (pair.getReplacement() == saved.getOriginal() || pair.getOriginal() == saved.getOriginal())
					{
						glamour.replaceIndex(i, saved.getReplacement());
					}
					i++;
				}
			}
		}
		return glamour;
	}

	public static Glamour start(ItemSheet sheet, DedupeItemComposition itemComposition, @Nullable Glamour active) {
		return new Glamour(sheet, itemComposition, active != null ? active.original : null);
	}

	private Glamour(ItemSheet sheet, DedupeItemComposition itemComposition, @Nullable GlamState original)
	{
		this.itemComposition = itemComposition;
		this.original = original != null ? original : GlamState.backup(itemComposition);

		var current = GlamState.initialize(itemComposition, sheet.getModels(itemComposition.getId()));
		if (original != null)
		{
			original.applyTo(itemComposition);
			staged = GlamState.initialize(itemComposition, sheet.getModels(itemComposition.getId()));
			current.applyTo(itemComposition);
		}
		else
		{
			staged = current;
		}
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

	protected void revert()
	{
		original.applyTo(itemComposition);
	}

	public Integer getReplacementModelId()
	{
		if (original.getModel() != staged.getModel())
		{
			return staged.getModel();
		}
		return null;
	}

	public void replaceIndex(int index, short after)
	{
		staged.replace(index, after);
	}

	public List<ColorReplacement> getColorReplacements()
	{
		List<ColorReplacement> colorReplacements = new ArrayList<>();
		for (var stagedReplacement : staged.getColorReplacements())
		{
			var originalHsl = stagedReplacement.getOriginal();
			for (var originalReplacement : original.getColorReplacements())
			{
				if (originalReplacement.getOriginal() == originalHsl)
				{
					originalHsl = originalReplacement.getReplacement();
				}
			}
			colorReplacements.add(new ColorReplacement(originalHsl, stagedReplacement.getReplacement()));
		}
		return colorReplacements;
	}
}
