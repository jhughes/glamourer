package io.huze.glamourer.glam;

import io.huze.glamourer.Extensions;
import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.color.Colors;
import io.huze.glamourer.item.DedupeKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ColorTextureOverride;
import net.runelite.api.ItemComposition;

@Slf4j
@ExtensionMethod({java.util.Arrays.class, Extensions.class})
public class PrimedItem
{
	@Getter
	@Setter
	private ItemComposition itemComposition;
	// Store name and model in case other plugins tamper with them.
	@Getter
	private final String name;
	@Getter
	private final int model;

	// The itemComposition's backup visual data.
	// Must never be mutated; only stored to revert the item back to its preprimed state on shutdown.
	@Nullable
	private final short[] backupColorFind;
	@Nullable
	private final short[] backupColorReplace;
	@Nullable
	private final short[] backupTexFind;
	@Nullable
	private final short[] backupTexReplace;

	// Primed data is visually equivalent to the backup data, but includes all unique colors and textures in all models.
	@Nonnull
	private final short[] primedColorFind;
	@Nonnull
	private final short[] primedColorReplace;
	@Nonnull
	private final short[] primedTexFind;
	@Nonnull
	private final short[] primedTexReplace;

	@Getter(lazy = true)
	private final List<ColorReplacement> colorReplacements = initColorReplacements();

	private PrimedItem(ItemComposition itemComposition,
					   @Nonnull short[] modelColors,
					   @Nonnull short[] modelTextures)
	{
		this.name = itemComposition.getMembersName();
		this.model = itemComposition.getInventoryModel();

		this.backupColorFind = itemComposition.getColorToReplace();
		this.backupColorReplace = itemComposition.getColorToReplaceWith();
		this.backupTexFind = itemComposition.getTextureToReplace();
		this.backupTexReplace = itemComposition.getTextureToReplaceWith();

		this.primedColorFind = modelColors;
		this.primedColorReplace = applyReplacements(modelColors, backupColorFind, backupColorReplace);
		this.primedTexFind = modelTextures;
		this.primedTexReplace = applyReplacements(modelTextures, backupTexFind, backupTexReplace);

		reprime(itemComposition);
	}

	@Nonnull
	public static PrimedItem of(@Nonnull ItemComposition pureItemComposition, @Nonnull PaletteFactory paletteFactory,
								@Nonnull Collection<ItemComposition> groupMembers)
	{
		var palette = paletteFactory.of(groupMembers);
		return new PrimedItem(pureItemComposition, palette.getColors(), palette.getTextures());
	}

	@Nonnull
	public static PrimedItem ofDummy(@Nonnull ItemComposition pureItemComposition, @Nonnull PrimedItem realItem)
	{
		return new PrimedItem(pureItemComposition, realItem.primedColorFind.clone(), realItem.primedTexFind.clone());
	}

	private static short[] applyReplacements(@Nonnull short[] values, @Nullable short[] find, @Nullable short[] replace)
	{
		var replacements = values.clone();
		for (int i = 0; i < replacements.length; i++)
		{
			replacements[i] = Colors.applyReplacements(replacements[i], find, replace);
		}
		return replacements;
	}

	short getOriginalColor(short modelColor)
	{
		return Colors.applyReplacements(modelColor, backupColorFind, backupColorReplace);
	}

	@Nonnull
	ModelEdits getOriginalEdits()
	{
		return new ModelEdits(backupColorFind.emptyIfNull(), backupColorReplace.emptyIfNull(),
			backupTexFind.emptyIfNull(), backupTexReplace.emptyIfNull());
	}

	public String getDedupeKey()
	{
		return DedupeKey.fromComponents(name, model, backupColorReplace, backupTexReplace);
	}

	public void reprime()
	{
		itemComposition.setColorToReplace(primedColorFind.clone());
		itemComposition.setColorToReplaceWith(primedColorReplace.clone());
		itemComposition.setTextureToReplace(primedTexFind.clone().nullIfEmpty());
		itemComposition.setTextureToReplaceWith(primedTexReplace.clone().nullIfEmpty());
	}

	public void reprime(ItemComposition itemComposition)
	{
		this.itemComposition = itemComposition;
		reprime();
	}

	public void revert()
	{
		itemComposition.setColorToReplace(backupColorFind);
		itemComposition.setColorToReplaceWith(backupColorReplace);
		itemComposition.setTextureToReplace(backupTexFind);
		itemComposition.setTextureToReplaceWith(backupTexReplace);
	}

	public void prime(@Nonnull ColorTextureOverride override)
	{
		try
		{
			Extensions.arrayCopyEqualLength(primedColorReplace, override.getColorToReplaceWith());
			Extensions.arrayCopyEqualLength(primedTexReplace, override.getTextureToReplaceWith());
		}
		catch (Throwable t)
		{
			log.warn("Failed to prime {} {}", name, itemComposition.getId(), t);
		}
	}

	@Nonnull
	GlamState getPrimedState()
	{
		return new GlamState(
			model,
			primedColorFind.clone(),
			primedColorReplace.clone(),
			primedTexFind.clone(),
			primedTexReplace.clone(),
			false
		);
	}

	private List<ColorReplacement> initColorReplacements()
	{
		assert primedColorFind != null;
		assert primedColorReplace != null;
		List<ColorReplacement> colorReplacements = new ArrayList<>();
		for (int i = 0; i < primedColorFind.length; i++)
		{
			colorReplacements.add(new ColorReplacement(primedColorFind[i], primedColorReplace[i]));
		}
		return colorReplacements;
	}
}
