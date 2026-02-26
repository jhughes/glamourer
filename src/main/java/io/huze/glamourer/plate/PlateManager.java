package io.huze.glamourer.plate;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.huze.glamourer.Config;
import io.huze.glamourer.glam.IconService;
import io.huze.glamourer.glam.Glamourer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
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

	public void loadPlates()
	{
		plates.clear();
		String json = configManager.getConfiguration(Config.GROUP, PLATES_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				List<PlateData> dataList = gson.fromJson(json, PLATES_LIST_TYPE);
				if (dataList != null)
				{
					for (PlateData data : dataList)
					{
						Plate plate = Plate.fromData(data, glamourer);
						plate.setOnChange(this::savePlates);
						plates.add(plate);
					}
					log.info("Loaded {} plates", plates.size());
				}
			}
			catch (Throwable e)
			{
				log.error("Failed to load plates", e);
			}
		}
		notifyPlatesChanged();
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

	public void importPlate(PlateData data)
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
			plates.get(existingIndex).revertAll();
			plates.remove(existingIndex);
		}

		Plate plate = Plate.fromData(data, glamourer);
		plate.setOnChange(this::savePlates);
		if (existingIndex >= 0)
		{
			plates.add(existingIndex, plate);
		}
		else
		{
			plates.add(plate);
		}
		reapplyAllPlates();
		savePlates();
		notifyPlatesChanged();
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
				plate.revertAll();
				plates.remove(plate);
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

	public void applyAllPlates()
	{
		for (Plate plate : plates)
		{
			if (plate.isEnabled())
			{
				plate.applyAll();
			}
		}
	}

	public void reapplyAllPlates()
	{
		revertAllPlates();
		applyAllPlates();
	}

	public void setPlateEnabled(Plate plate, boolean enabled)
	{
		revertAllPlates();
		plate.setEnabled(enabled);
		applyAllPlates();
		notifyPlatesChanged();
	}

	public void revertAllPlates()
	{
		for (Plate plate : plates)
		{
			plate.revertAll();
		}
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
