package io.huze.glamourer.plate;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.huze.glamourer.Config;
import io.huze.glamourer.glam.IconService;
import io.huze.glamourer.glam.Glamourer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class PlateManager
{
	private static final String PLATES_KEY = "userPlates";
	private static final Type PLATES_LIST_TYPE = new TypeToken<List<PlateData>>()
	{
	}.getType();

	private final ConfigManager configManager;
	@Getter
	private final Gson gson;
	@Getter
	private final Glamourer glamourer;
	@Getter
	private final IconService iconService;

	@Getter
	private final List<Plate> plates = new ArrayList<>();
	@Setter
	private Consumer<Void> onPlatesChanged;

	public CompletableFuture<Void> loadPlates()
	{
		plates.clear();
		String json = configManager.getConfiguration(Config.GROUP, PLATES_KEY);
		if (json == null || json.isEmpty())
		{
			notifyPlatesChanged();
			return CompletableFuture.completedFuture(null);
		}

		List<PlateData> dataList;
		try
		{
			dataList = gson.fromJson(json, PLATES_LIST_TYPE);
		}
		catch (Throwable e)
		{
			log.error("Failed to load plates", e);
			notifyPlatesChanged();
			return CompletableFuture.completedFuture(null);
		}

		if (dataList == null || dataList.isEmpty())
		{
			notifyPlatesChanged();
			return CompletableFuture.completedFuture(null);
		}

		List<CompletableFuture<Plate>> futures = new ArrayList<>();
		for (PlateData data : dataList)
		{
			futures.add(Plate.loadFromData(data, glamourer));
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
			.thenRun(() -> {
				for (CompletableFuture<Plate> f : futures)
				{
					Plate plate = f.join();
					plate.setOnChange(this::savePlates);
					plates.add(plate);
				}
				log.info("Loaded {} plates", plates.size());
				notifyPlatesChanged();
			});
	}

	public void savePlates()
	{
		try
		{
			List<PlateData> dataList = plates.stream()
				.map(Plate::getData)
				.collect(Collectors.toList());
			String json = gson.toJson(dataList, PLATES_LIST_TYPE);
			configManager.setConfiguration(Config.GROUP, PLATES_KEY, json);
			log.debug("Saved {} plates", plates.size());
		}
		catch (Exception e)
		{
			log.error("Failed to save plates", e);
		}
	}

	public void createPlate()
	{
		Plate plate = Plate.newEmptyPlate(glamourer);
		plate.setOnChange(this::savePlates);
		plates.add(plate);
		savePlates();
		notifyPlatesChanged();
	}

	public CompletableFuture<Void> importPlate(PlateData data)
	{
		data.setEnabled(true);
		data.setExpanded(true);

		// Overwrite existing plate with same ID, preserving its position
		int existingIndex = -1;
		for (int i = 0; i < plates.size(); i++)
		{
			if (plates.get(i).getId().equals(data.getId()))
			{
				existingIndex = i;
				break;
			}
		}
		if (existingIndex >= 0)
		{
			plates.remove(existingIndex);
		}

		final int insertIndex = existingIndex;
		return Plate.loadFromData(data, glamourer).thenAccept(plate -> {
			plate.setOnChange(this::savePlates);
			if (insertIndex >= 0)
			{
				plates.add(insertIndex, plate);
			}
			else
			{
				plates.add(plate);
			}
			reapplyAllPlates();
			savePlates();
			notifyPlatesChanged();
		});
	}

	public boolean hasPlateWithId(String id)
	{
		return plates.stream().anyMatch(plate -> plate.getId().equals(id));
	}

	public void deletePlate(String id)
	{
		plates.stream()
			.filter(plate -> plate.getId().equals(id))
			.findFirst()
			.ifPresent(plate -> {
				plates.remove(plate);
				reapplyAllPlates();
				savePlates();
				notifyPlatesChanged();
			});
	}

	public void movePlate(int fromIndex, int toIndex)
	{
		int size = plates.size();
		if (fromIndex < 0 || fromIndex >= size || toIndex < 0 || toIndex >= size || fromIndex == toIndex)
		{
			return;
		}
		Plate plate = plates.remove(fromIndex);
		plates.add(toIndex, plate);
		reapplyAllPlates();
		savePlates();
		notifyPlatesChanged();
	}

	public void reapplyAllPlates()
	{
		glamourer.batch(() -> plates.forEach(Plate::applyAll));
	}

	public void setPlateEnabled(Plate plate, boolean enabled)
	{
		plate.setEnabled(enabled);
		reapplyAllPlates();
		notifyPlatesChanged();
	}

	public void removeGlamour(Plate plate, int glamourIndex)
	{
		plate.removeGlamour(glamourIndex);
		reapplyAllPlates();
		notifyPlatesChanged();
	}

	public void setPlateDisplayStyle(Plate plate, DisplayStyle displayStyle)
	{
		plate.setDisplayStyle(displayStyle);
		if (plate.isEnabled())
		{
			reapplyAllPlates();
		}
		notifyPlatesChanged();
	}

	public void setPlateIconStyle(Plate plate, IconStyle iconStyle)
	{
		plate.setIconStyle(iconStyle);
		if (plate.isEnabled())
		{
			reapplyAllPlates();
		}
		notifyPlatesChanged();
	}

	public void runBatched(Runnable action)
	{
		for (Plate plate : plates)
		{
			plate.setOnChange(null);
		}
		try
		{
			action.run();
		}
		finally
		{
			for (Plate plate : plates)
			{
				plate.setOnChange(this::savePlates);
			}
			savePlates();
		}
	}

	private void notifyPlatesChanged()
	{
		if (onPlatesChanged != null)
		{
			onPlatesChanged.accept(null);
		}
	}
}
