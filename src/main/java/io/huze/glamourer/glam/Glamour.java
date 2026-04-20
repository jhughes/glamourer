package io.huze.glamourer.glam;

import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.texture.TextureReplacement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ColorTextureOverride;
import net.runelite.api.ItemComposition;

@Slf4j
public class Glamour
{
	@Nullable
	private final String key;
	private final PrimedItem primedItem;
	@Getter
	private final Collection<Integer> itemIds;
	final GlamState staged;
	private volatile boolean dirty;

	public GlamourData getData(boolean verbose)
	{
		return getData(-1, verbose);
	}

	public GlamourData getData(int itemId, boolean verbose)
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

		var colorList = colorReplacements.stream()
			.filter((cr) -> verbose || cr.hasChanged())
			.collect(Collectors.toList());

		var textureList = getTextureReplacements().stream()
			.filter(tr -> verbose || tr.hasChanged())
			.collect(Collectors.toList());

		return new GlamourData(
			itemId == -1 ? key : null,
			itemId == -1 ? getPrimaryItemId() : itemId,
			colorList,
			textureList.isEmpty() ? null : textureList);
	}

	/// Load glamour from serialized GlamourData.
	public static Glamour load(PrimedItem primedItem, Collection<Integer> duplicateItemIds, GlamourData data)
	{
		var glamState = GlamState.initialize(primedItem);
		var colorReplacements = data.getColorReplacements();
		glamState.applyColorReplacements(colorReplacements);
		var textureReplacements = data.getTextureReplacements();
		if (textureReplacements != null)
		{
			glamState.applyTextureReplacements(textureReplacements);
		}
		return new Glamour(data.getItemKey(), primedItem, duplicateItemIds, glamState);
	}

	public static Glamour start(PrimedItem primedItem, Collection<Integer> duplicateItemIds)
	{
		return new Glamour(primedItem.getDedupeKey(), primedItem, duplicateItemIds, GlamState.initialize(primedItem));
	}

	private Glamour(@Nullable String key,
					PrimedItem primedItem,
					Collection<Integer> itemIds,
					GlamState staged)
	{
		this.key = key;
		this.primedItem = primedItem;
		this.itemIds = itemIds;
		this.staged = staged;
	}

	protected Glamour(Glamour source)
	{
		this.key = source.key;
		this.primedItem = source.primedItem;
		this.itemIds = source.itemIds;
		this.staged = source.staged;
	}

	public int getPrimaryItemId()
	{
		return primedItem.getItemComposition().getId();
	}

	public String getItemName()
	{
		return primedItem.getName();
	}

	public boolean isEquivalent(Glamour other)
	{
		return this == other || other instanceof WornOnlyGlamour && this == ((WornOnlyGlamour) other).source;
	}

	protected void apply(ItemComposition itemComposition)
	{
		staged.applyTo(itemComposition);
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

	protected void applyReplacement(@Nonnull ColorTextureOverride override)
	{
		if (sizeMismatch(override))
		{
			return;
		}
		staged.applyTo(override);
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
			for (var originalReplacement : primedItem.getOriginalColorReplacements())
			{
				if (originalReplacement.getOriginal() == modelColor)
				{
					originalHsl = originalReplacement.getReplacement();
					break;
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
