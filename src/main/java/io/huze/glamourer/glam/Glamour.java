package io.huze.glamourer.glam;

import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.texture.TextureReplacement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
	private final AtomicBoolean dirty = new AtomicBoolean();

	@Nonnull
	public GlamourData getData(boolean verbose)
	{
		return getData(-1, verbose);
	}

	@Nonnull
	public GlamourData getData(int itemId, boolean verbose)
	{
		var colorList = getColorReplacements().stream()
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
	@Nonnull
	public static Glamour load(@Nonnull PrimedItem primedItem, @Nonnull Collection<Integer> duplicateItemIds, @Nonnull GlamourData data)
	{
		var glamState = primedItem.getPrimedState();
		var colorReplacements = data.getColorReplacements();
		glamState.applyColorReplacements(colorReplacements);
		var textureReplacements = data.getTextureReplacements();
		if (textureReplacements != null)
		{
			glamState.applyTextureReplacements(textureReplacements);
		}
		return new Glamour(data.getItemKey(), primedItem, duplicateItemIds, glamState);
	}

	@Nonnull
	public static Glamour start(@Nonnull PrimedItem primedItem, @Nonnull Collection<Integer> duplicateItemIds)
	{
		return new Glamour(primedItem.getDedupeKey(), primedItem, duplicateItemIds, primedItem.getPrimedState());
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
			var textureReplace = override.getTextureToReplaceWith();
			var actualSize = textureReplace == null ? 0 : textureReplace.length;
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
		dirty.set(true);
	}

	public void replaceTextureIndex(int index, short after)
	{
		staged.replaceTexture(index, after);
		dirty.set(true);
	}

	@Nonnull
	public List<TextureReplacement> getTextureReplacements()
	{
		return staged.getTextureReplacements();
	}

	@Nonnull
	public List<ColorReplacement> getColorReplacements()
	{
		return getColorReplacementsFrom(staged);
	}

	public List<ColorReplacement> getDisplayedColorReplacements()
	{
		return getColorReplacementsFrom(snapshotState());
	}

	private List<ColorReplacement> getColorReplacementsFrom(GlamState state)
	{
		List<ColorReplacement> colorReplacements = new ArrayList<>();
		for (var stagedReplacement : state.getColorReplacements())
		{
			short modelColor = stagedReplacement.getModel();
			colorReplacements.add(new ColorReplacement(
				modelColor, primedItem.getOriginalColor(modelColor), stagedReplacement.getReplacement()));
		}
		return colorReplacements;
	}

	boolean isDirty()
	{
		return dirty.get();
	}

	boolean clearDirty()
	{
		return dirty.getAndSet(false);
	}

	GlamState snapshotState()
	{
		return staged.deepCopy(true);
	}
}
