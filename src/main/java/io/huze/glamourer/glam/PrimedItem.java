package io.huze.glamourer.glam;

import io.huze.glamourer.Extensions;
import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.item.DedupeKey;
import io.huze.glamourer.item.ItemSheet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ColorTextureOverride;
import net.runelite.api.ItemComposition;
import net.runelite.api.ModelData;

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
	private final List<ColorReplacement> originalColorReplacements = initBackupColorReplacements();
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
	public static PrimedItem of(@Nonnull ItemComposition pureItemComposition, @Nonnull ItemSheet sheet)
	{
		var modelData = sheet.getModels(pureItemComposition);

		var colorSet = new HashSet<Short>();
		var textureSet = new HashSet<Short>();
		for (ModelData datum : modelData)
		{
			for (var color : datum.getFaceColors())
			{
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

		return new PrimedItem(pureItemComposition, modelColors, modelTextures);
	}

	private static short[] applyReplacements(@Nonnull short[] values, @Nullable short[] find, @Nullable short[] replace)
	{
		var replacements = values.clone();
		if (find != null && replace != null)
		{
			for (int i = 0; i < values.length; i++)
			{
				for (int j = 0; j < find.length; j++)
				{
					if (replacements[i] == find[j])
					{
						replacements[i] = replace[j];
					}
				}
			}
		}
		return replacements;
	}

	public String getDedupeKey()
	{
		return DedupeKey.fromComponents(name, model, backupColorReplace, backupTexReplace);
	}

	public void reprime()
	{
		itemComposition.setColorToReplace(primedColorFind.clone());
		itemComposition.setColorToReplaceWith(primedColorReplace.clone());
		itemComposition.setTextureToReplace(primedTexFind.clone());
		itemComposition.setTextureToReplaceWith(primedTexReplace.clone());
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

	private static void arrayCopyEqualLength(/*@Nonnull*/ short[] src, /*@Nonnull*/ short[] dest)
	{
		if (src == null)
		{
			throw new IllegalArgumentException("arrayCopyEqualLength src was null");
		}
		if (dest == null)
		{
			throw new IllegalArgumentException("arrayCopyEqualLength dest was null");
		}
		if (src.length != dest.length)
		{
			throw new IllegalArgumentException("arrayCopyEqualLength " + src.length + " != " + dest.length);
		}
		System.arraycopy(src, 0, dest, 0, src.length);
	}

	public void prime(@Nonnull ColorTextureOverride override)
	{
		try
		{
			arrayCopyEqualLength(primedColorReplace, override.getColorToReplaceWith());
			arrayCopyEqualLength(primedTexReplace, override.getTextureToReplaceWith());
		}
		catch (Throwable t)
		{
			log.warn("Failed to prime {} {}", name, itemComposition.getId(), t);
		}
	}

	/// Run on a mutable item composition; the composition's visual changes are reset after.
	public void runOnMutableItemComp(Consumer<ItemComposition> runnable)
	{
		// TODO dont need to change colorfind because it never changes except on revert
		final var colorFind = itemComposition.getColorToReplace();
		final var colorReplace = itemComposition.getColorToReplaceWith();
		final var texFind = itemComposition.getTextureToReplace();
		final var texReplace = itemComposition.getTextureToReplaceWith();
		try
		{
			runnable.accept(itemComposition);
		}
		finally
		{
			itemComposition.setColorToReplace(colorFind);
			itemComposition.setColorToReplaceWith(colorReplace);
			itemComposition.setTextureToReplace(texFind);
			itemComposition.setTextureToReplaceWith(texReplace);
		}
	}

	private static final short[] EMPTY = {};
	@Nonnull
	private static short[] nullableClone(@Nullable short[] a)
	{
		return a == null ? EMPTY : a.clone();
	}

	@Nonnull
	GlamState getPrimedState()
	{
		return new GlamState(
			model,
			nullableClone(primedColorFind),
			nullableClone(primedColorReplace),
			nullableClone(primedTexFind),
			nullableClone(primedTexReplace),
			false
		);
	}

	private List<ColorReplacement> initBackupColorReplacements()
	{
		List<ColorReplacement> colorReplacements = new ArrayList<>();
		if (backupColorFind != null && backupColorReplace != null)
		{
			for (int i = 0; i < backupColorFind.length; i++)
			{
				colorReplacements.add(new ColorReplacement(backupColorFind[i], backupColorReplace[i]));
			}
		}
		return colorReplacements;
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
