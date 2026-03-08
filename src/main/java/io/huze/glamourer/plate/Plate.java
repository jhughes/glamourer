package io.huze.glamourer.plate;

import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.glam.GlamourData;
import io.huze.glamourer.glam.GlamourVisibility;
import io.huze.glamourer.glam.Glamourer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
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
	@Getter
	private boolean enabled;
	@Getter
	private boolean expanded;
	@Getter
	private DisplayStyle displayStyle;

	private final Glamourer glamourer;
	private final List<Glamour> glamours;
	@Getter
	private final List<GlamourData> failedGlamours;

	@Setter
	private Runnable onChange;

	private Plate(@Nonnull String id, @Nonnull String name, boolean enabled, boolean expanded,
				  @Nonnull DisplayStyle displayStyle, @Nonnull Glamourer glamourer,
				  @Nonnull ArrayList<Glamour> loadedGlamours, @Nonnull List<GlamourData> failedGlamours)
	{
		this.id = id;
		this.name = name;
		this.enabled = enabled;
		this.expanded = expanded;
		this.displayStyle = displayStyle;
		this.glamourer = glamourer;
		this.glamours = loadedGlamours;
		this.failedGlamours = failedGlamours;
	}

	public static Plate newEmptyPlate(Glamourer glamourer)
	{
		String id = UUID.randomUUID().toString();
		return new Plate(id, "New Plate", true, true, DisplayStyle.LOCAL, glamourer,
			new ArrayList<>(), Collections.emptyList());
	}

	public static CompletableFuture<Plate> loadFromData(PlateData data, Glamourer glamourer)
	{
		var enabled = data.getEnabled() != null ? data.getEnabled() : false;
		var expanded = data.getExpanded() != null ? data.getExpanded() : true;
		var displayStyle = data.getDisplayStyle() != null ? data.getDisplayStyle() : DisplayStyle.LOCAL;
		return glamourer.loadGlamoursAsync(data.getGlamours()).thenApply(result ->
			new Plate(data.getId(), data.getName(), enabled, expanded, displayStyle,
				glamourer, result.getLoaded(), result.getFailed())
		);
	}

	public PlateData getData()
	{
		return getData(false);
	}

	public PlateData getData(boolean verbose)
	{
		List<GlamourData> dataList = new ArrayList<>();
		for (Glamour glam : glamours)
		{
			dataList.add(glam.getData(verbose));
		}
		dataList.addAll(failedGlamours);
		return new PlateData(id, name, enabled, expanded, displayStyle, dataList);
	}

	public void setExpanded(boolean expanded)
	{
		this.expanded = expanded;
		notifyChange();
	}

	public void setName(String name)
	{
		this.name = name;
		notifyChange();
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

	public CompletableFuture<Void> addGlamour(int itemId)
	{
		return glamourer.startGlamourAsync(itemId).thenAccept(glam ->
			insertGlamour(Integer.MAX_VALUE, glam));
	}

	public Glamour removeGlamour(int index)
	{
		if (index < 0 || index >= glamours.size())
		{
			return null;
		}

		Glamour glam = glamours.remove(index);
		notifyChange();
		return glam;
	}

	public void moveGlamour(int fromIndex, int toIndex)
	{
		int size = glamours.size();
		if (fromIndex < 0 || fromIndex >= size || toIndex < 0 || toIndex >= size || fromIndex == toIndex)
		{
			return;
		}

		Glamour glam = glamours.remove(fromIndex);
		glamours.add(toIndex, glam);
		notifyChange();
	}

	public void insertGlamour(int index, Glamour glam)
	{
		if (containsItem(glam.getPrimaryItemId()))
		{
			return;
		}

		int insertIndex = Math.max(0, Math.min(index, glamours.size()));

		glamours.add(insertIndex, glam);

		notifyChange();
	}

	public void updateGlamourColor(int glamourIndex, int colorIndex, short newColor)
	{
		if (glamourIndex < 0 || glamourIndex >= glamours.size())
		{
			return;
		}

		Glamour glam = glamours.get(glamourIndex);
		glam.replaceColorIndex(colorIndex, newColor);

		tryApplyGlam(glam);
		notifyChange();
	}

	public void updateGlamourColors(int glamourIndex, List<int[]> colorUpdates)
	{
		if (glamourIndex < 0 || glamourIndex >= glamours.size())
		{
			return;
		}

		Glamour glam = glamours.get(glamourIndex);
		for (int[] update : colorUpdates)
		{
			glam.replaceColorIndex(update[0], (short) update[1]);
		}

		tryApplyGlam(glam);
		notifyChange();
	}

	public void updateGlamourTexture(int glamourIndex, int textureIndex, short newTextureId)
	{
		if (glamourIndex < 0 || glamourIndex >= glamours.size())
		{
			return;
		}

		Glamour glam = glamours.get(glamourIndex);
		glam.replaceTextureIndex(textureIndex, newTextureId);

		tryApplyGlam(glam);
		notifyChange();
	}

	public void applyAll()
	{
		for (Glamour glam : glamours)
		{
			tryApplyGlam(glam);
		}
	}

	public void setEnabled(boolean enabled)
	{
		if (this.enabled == enabled)
		{
			return;
		}
		this.enabled = enabled;
		notifyChange();
	}

	public void setDisplayStyle(DisplayStyle displayStyle)
	{
		if (this.displayStyle == displayStyle)
		{
			return;
		}
		this.displayStyle = displayStyle;
		notifyChange();
	}

	private void notifyChange()
	{
		if (onChange != null) onChange.run();
	}

	private void tryApplyGlam(Glamour glam)
	{
		if (enabled)
		{
			glamourer.apply(glam, displayStyle);
		}
	}
}
