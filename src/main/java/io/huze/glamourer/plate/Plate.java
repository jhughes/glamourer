package io.huze.glamourer.plate;

import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.glam.GlamourData;
import io.huze.glamourer.glam.GlamourVisibility;
import io.huze.glamourer.glam.Glamourer;
import io.huze.glamourer.glam.WornOnlyGlamour;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Plate
{
	@Getter
	private final String id;
	@Getter
	private String name;
	@Setter
	@Getter
	private boolean enabled;
	@Setter
	@Getter
	private DisplayStyle displayStyle;
	@Setter
	@Getter
	private IconStyle iconStyle;

	private final Glamourer glamourer;
	@Nonnull
	private final ChangeLog changeLog;
	@Nullable
	private final PlateManager plateManager;
	private final List<Glamour> glamours;
	@Getter
	private final List<GlamourData> failedGlamours;

	private Plate(@Nonnull String id, @Nonnull String name, boolean enabled,
				  @Nonnull DisplayStyle displayStyle, @Nonnull IconStyle iconStyle, @Nonnull Glamourer glamourer,
				  @Nonnull ChangeLog changeLog, @Nullable PlateManager plateManager,
				  @Nonnull ArrayList<Glamour> loadedGlamours, @Nonnull List<GlamourData> failedGlamours)
	{
		this.id = id;
		this.name = name;
		this.enabled = enabled;
		this.displayStyle = displayStyle;
		this.iconStyle = iconStyle;
		this.glamourer = glamourer;
		this.changeLog = changeLog;
		this.plateManager = plateManager;
		this.glamours = loadedGlamours;
		this.failedGlamours = failedGlamours;
	}

	public static Plate newEmptyPlate(Glamourer glamourer, @Nonnull ChangeLog changeLog, @Nullable PlateManager plateManager)
	{
		return newPlate("New Plate", glamourer, changeLog, plateManager, new ArrayList<>());
	}

	public static Plate newPlate(String name, Glamourer glamourer, @Nonnull ChangeLog changeLog,
								 @Nullable PlateManager plateManager, ArrayList<Glamour> glamours)
	{
		String id = UUID.randomUUID().toString();
		return new Plate(id, name, true, DisplayStyle.LOCAL, IconStyle.NORMAL, glamourer,
			changeLog, plateManager, glamours, Collections.emptyList());
	}

	public static CompletableFuture<Plate> loadFromData(PlateData data, Glamourer glamourer, @Nonnull ChangeLog changeLog, @Nullable PlateManager plateManager)
	{
		var enabled = data.getEnabled() != null ? data.getEnabled() : false;
		var displayStyle = data.getDisplayStyle() != null ? data.getDisplayStyle() : DisplayStyle.LOCAL;
		var iconStyle = data.getIconStyle() != null ? data.getIconStyle() : IconStyle.NORMAL;
		return glamourer.loadGlamoursAsync(data.getGlamours()).thenApply(result ->
			new Plate(data.getId(), data.getName(), enabled, displayStyle,
				iconStyle, glamourer, changeLog, plateManager, result.getLoaded(), result.getFailed())
		);
	}

	PlateData getData(boolean verbose)
	{
		return new PlateData(id, name, enabled, displayStyle,
			iconStyle != IconStyle.NORMAL ? iconStyle : null, getGlamourData(verbose));
	}

	List<GlamourData> getGlamourData()
	{
		return getGlamourData(false);
	}

	List<GlamourData> getGlamourData(boolean verbose)
	{
		List<GlamourData> dataList = new ArrayList<>();
		for (Glamour glam : glamours)
		{
			dataList.add(glam.getData(verbose));
		}
		dataList.addAll(failedGlamours);
		return dataList;
	}

	public void setName(String name)
	{
		if (!name.equals(this.name))
		{
			apply(new PlateRenameChange(plateManager, this, this.name, name));
		}
	}

	/**
	 * Set name without committing to ChangeLog. Used by undo/redo.
	 */
	void applyName(String name)
	{
		this.name = name;
	}

	public Set<Integer> getItemIds()
	{
		Set<Integer> plateItemIds = new HashSet<>();
		for (var glam : glamours)
		{
			plateItemIds.addAll(glam.getItemIds());
		}
		return plateItemIds;
	}

	public List<Glamour> getGlamours()
	{
		return Collections.unmodifiableList(glamours);
	}

	public GlamourVisibility getVisibility(Glamour glam)
	{
		return glamourer.getVisibility(glam, enabled);
	}

	public boolean containsItem(int itemId)
	{
		return glamours.stream().anyMatch(g -> g.getPrimaryItemId() == itemId);
	}

	void removeGlamour(int index)
	{
		if (index < 0 || index >= glamours.size())
		{
			return;
		}
		glamours.remove(index);
	}

	void moveGlamour(int fromIndex, int toIndex)
	{
		int size = glamours.size();
		if (fromIndex < 0 || fromIndex >= size || toIndex < 0 || toIndex >= size || fromIndex == toIndex)
		{
			return;
		}

		Glamour glam = glamours.remove(fromIndex);
		glamours.add(toIndex, glam);
	}

	void insertGlamour(int index, Glamour glam)
	{
		if (containsItem(glam.getPrimaryItemId()))
		{
			return;
		}

		int insertIndex = Math.max(0, Math.min(index, glamours.size()));
		glamours.add(insertIndex, glam);
	}

	public void commitGlamourColor(int glamourIndex, int colorIndex, short beforeColor, short newColor)
	{
		if (newColor != beforeColor)
		{
			apply(new ColorChange(plateManager, this, glamourIndex, colorIndex, beforeColor, newColor, glamourName(glamourIndex)));
		}
	}

	public void commitGlamourColors(int glamourIndex, int[] colorIndices, short[] beforeColors, short[] afterColors)
	{
		if (!Arrays.equals(beforeColors, afterColors))
		{
			apply(new GroupColorChange(plateManager, this, glamourIndex, colorIndices, beforeColors, afterColors, glamourName(glamourIndex)));
		}
	}

	public void commitGlamourTexture(int glamourIndex, int textureIndex, short beforeTexture, short newTexture)
	{
		if (newTexture != beforeTexture)
		{
			apply(new TextureChange(plateManager, this, glamourIndex, textureIndex, beforeTexture, newTexture, glamourName(glamourIndex)));
		}
	}

	private void apply(Change change)
	{
		change.redo();
		change.save();
		changeLog.record(change);
	}

	/**
	 * Apply a single color change and trigger visual reconcile, without saving.
	 * Used by ChangeLog for undo/redo.
	 */
	void applyGlamourColor(int glamourIndex, int colorIndex, short newColor)
	{
		if (glamourIndex < 0 || glamourIndex >= glamours.size())
		{
			return;
		}

		Glamour glam = glamours.get(glamourIndex);
		glam.replaceColorIndex(colorIndex, newColor);
		tryApplyGlam(glam);
	}

	/**
	 * Apply batch color changes and trigger visual reconcile, without saving.
	 * Used by ChangeLog for undo/redo.
	 */
	void applyGlamourColors(int glamourIndex, int[] colorIndices, short[] colors)
	{
		if (glamourIndex < 0 || glamourIndex >= glamours.size())
		{
			return;
		}

		Glamour glam = glamours.get(glamourIndex);
		for (int i = 0; i < colorIndices.length; i++)
		{
			glam.replaceColorIndex(colorIndices[i], colors[i]);
		}
		tryApplyGlam(glam);
	}

	/**
	 * Apply a texture change and trigger visual reconcile, without saving.
	 * Used by ChangeLog for undo/redo.
	 */
	void applyGlamourTexture(int glamourIndex, int textureIndex, short newTextureId)
	{
		if (glamourIndex < 0 || glamourIndex >= glamours.size())
		{
			return;
		}

		Glamour glam = glamours.get(glamourIndex);
		glam.replaceTextureIndex(textureIndex, newTextureId);
		tryApplyGlam(glam);
	}

	public void applyAll()
	{
		for (Glamour glam : glamours)
		{
			tryApplyGlam(glam);
		}
	}

	private String glamourName(int glamourIndex)
	{
		return glamourIndex >= 0 && glamourIndex < glamours.size()
			? glamours.get(glamourIndex).getItemName() : "item";
	}
	
	private void tryApplyGlam(Glamour glam)
	{
		if (enabled)
		{
			Glamour toApply = iconStyle == IconStyle.WORN_ONLY ? new WornOnlyGlamour(glam) : glam;
			glamourer.apply(toApply, displayStyle);
		}
	}
}
