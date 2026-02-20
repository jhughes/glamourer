package io.huze.glamourer.plate;

import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.glam.GlamourConflictException;
import io.huze.glamourer.glam.GlamourData;
import io.huze.glamourer.glam.Glamourer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

	private final List<Glamour> glamours;
	private final List<GlamourData> failedGlamours;
	private final Set<Glamour> appliedGlamours;

	@Setter
	private Runnable onChange;

	private Plate(String id, String name, boolean enabled, boolean expanded,
				 Glamourer glamourer, List<GlamourData> glamourDataList)
	{
		this.id = id;
		this.name = name;
		this.enabled = enabled;
		this.expanded = expanded;
		this.glamours = new ArrayList<>();
		this.failedGlamours = new ArrayList<>();
		this.appliedGlamours = new HashSet<>();

		if (glamourer != null && glamourDataList != null)
		{
			for (GlamourData data : glamourDataList)
			{
				try
				{
					Glamour glam = glamourer.loadGlamour(data);
					glamours.add(glam);
				}
				catch (Exception e)
				{
					log.error("Failed to initialize glamour for item {}", data, e);
					failedGlamours.add(data);
				}
			}
		}
	}

	public static Plate newEmptyPlate()
	{
		String id = UUID.randomUUID().toString();
		return new Plate(id, "New Plate", true, true, null, null);
	}

	public static Plate fromData(PlateData data, Glamourer glamourer)
	{
		var enabled = data.getEnabled() != null ? data.getEnabled() : false;
		var expanded = data.getExpanded() != null ? data.getExpanded() : true;
		return new Plate(
			data.getId(),
			data.getName(),
			enabled,
			expanded,
			glamourer,
			data.getGlamours()
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
		return new PlateData(id, name, enabled, expanded, dataList);
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


	public void addGlamour(Glamourer glamourer, int itemId)
	{
		try
		{
			Glamour glam = glamourer.startGlamour(itemId);
			glamours.add(glam);
			if (enabled)
			{
				applyOrDisable(glamourer, glam);
				appliedGlamours.add(glam);
			}
		}
		finally
		{
			notifyChange();
		}
	}

	public void removeGlamour(Glamourer glamourer, int index)
	{
		if (index < 0 || index >= glamours.size())
		{
			return;
		}

		Glamour glam = glamours.get(index);
		if (appliedGlamours.contains(glam))
		{
			glamourer.revert(glam);
			appliedGlamours.remove(glam);
		}
		glamours.remove(index);
		notifyChange();
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

	public Glamour extractGlamour(Glamourer glamourer, int index)
	{
		if (index < 0 || index >= glamours.size())
		{
			return null;
		}

		Glamour glam = glamours.get(index);

		// Revert if applied
		if (appliedGlamours.contains(glam))
		{
			glamourer.revert(glam);
			appliedGlamours.remove(glam);
		}

		glamours.remove(index);
		notifyChange();

		return glam;
	}

	public void insertGlamour(Glamourer glamourer, int index, Glamour glam)
	{
		int insertIndex = Math.max(0, Math.min(index, glamours.size()));

		glamours.add(insertIndex, glam);

		if (enabled)
		{
			applyOrDisable(glamourer, glam);
			appliedGlamours.add(glam);
		}
		notifyChange();
	}

	public void updateGlamourColor(Glamourer glamourer, int glamourIndex, int colorIndex, short newColor)
	{
		if (glamourIndex < 0 || glamourIndex >= glamours.size())
		{
			return;
		}

		Glamour glam = glamours.get(glamourIndex);
		glam.replaceIndex(colorIndex, newColor);

		if (appliedGlamours.contains(glam))
		{
			applyOrDisable(glamourer, glam);
			glamourer.scheduleCacheReset();
		}
		notifyChange();
	}

	public void updateGlamourColors(Glamourer glamourer, int glamourIndex, List<int[]> colorUpdates)
	{
		if (glamourIndex < 0 || glamourIndex >= glamours.size())
		{
			return;
		}

		Glamour glam = glamours.get(glamourIndex);
		for (int[] update : colorUpdates)
		{
			glam.replaceIndex(update[0], (short) update[1]);
		}

		if (appliedGlamours.contains(glam))
		{
			applyOrDisable(glamourer, glam);
			glamourer.scheduleCacheReset();
		}
		notifyChange();
	}

	public void applyOrDisable(Glamourer glamourer, Glamour glamour) {
		try
		{
			glamourer.apply(glamour);
		}
		catch (GlamourConflictException e)
		{
			this.enabled = false;
			revertAll(glamourer);
			throw e;
		}
	}

	public void applyAll(Glamourer glamourer)
	{
		for (Glamour glam : glamours)
		{
			if (!appliedGlamours.contains(glam))
			{
				applyOrDisable(glamourer, glam);
				appliedGlamours.add(glam);
			}
		}
	}

	public void revertAll(Glamourer glamourer)
	{
		for (Glamour glam : appliedGlamours)
		{
			glamourer.revert(glam);
		}
		appliedGlamours.clear();
	}

	public void setEnabledAndApply(Glamourer glamourer, boolean enabled)
	{
		this.enabled = enabled;
		if (enabled)
		{
			applyAll(glamourer);
		}
		else
		{
			revertAll(glamourer);
		}
		notifyChange();
	}

	private void notifyChange()
	{
		if (onChange != null) onChange.run();
	}
}
