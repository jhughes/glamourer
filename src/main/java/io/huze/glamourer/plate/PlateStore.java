package io.huze.glamourer.plate;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.huze.glamourer.Config;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class PlateStore
{
	static final String PLATE_LIST_KEY = "plateList";
	static final String PLATE_KEY_PREFIX = "plate.";
	static final String LEGACY_KEY = "userPlates";
	static final long TOMBSTONE_TTL_MS = 7L * 24 * 60 * 60 * 1000;

	private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>()
	{
	}.getType();
	private static final Type LEGACY_PLATES_TYPE = new TypeToken<List<PlateData>>()
	{
	}.getType();

	private final ConfigManager configManager;
	private final Gson gson;

	public List<String> getPlateOrder()
	{
		String json = configManager.getConfiguration(Config.GROUP, PLATE_LIST_KEY);
		if (json == null || json.isEmpty())
		{
			return Collections.emptyList();
		}
		try
		{
			List<String> ids = gson.fromJson(json, STRING_LIST_TYPE);
			return ids != null ? ids : Collections.emptyList();
		}
		catch (JsonSyntaxException e)
		{
			log.error("Failed to parse plate order", e);
			return Collections.emptyList();
		}
	}

	@Nullable
	PlateData loadPlate(String uuid) throws IOException
	{
		PlateConfig stored = readConfig(uuid);
		if (stored == null || stored.isTombstone())
		{
			return null;
		}
		return stored.toPlateData(uuid);
	}

	List<PlateData> loadAllPlates() throws IOException
	{
		List<String> order = getPlateOrder();
		List<PlateData> plates = new ArrayList<>();
		for (String uuid : order)
		{
			PlateData plate = loadPlate(uuid);
			if (plate != null)
			{
				plates.add(plate);
			}
		}
		return plates;
	}

	List<PlateData> loadOrphanedPlates() throws IOException
	{
		Set<String> ordered = new HashSet<>(getPlateOrder());
		List<PlateData> orphans = new ArrayList<>();
		String prefix = Config.GROUP + "." + PLATE_KEY_PREFIX;

		for (String fullKey : configManager.getConfigurationKeys(prefix))
		{
			String uuid = fullKey.substring(prefix.length());
			if (ordered.contains(uuid))
			{
				continue;
			}
			PlateData plate = loadPlate(uuid);
			if (plate != null)
			{
				orphans.add(plate);
			}
		}
		return orphans;
	}

	boolean isLegacyFormat()
	{
		String plateList = configManager.getConfiguration(Config.GROUP, PLATE_LIST_KEY);
		if (plateList != null && !plateList.isEmpty())
		{
			return false;
		}
		String legacy = configManager.getConfiguration(Config.GROUP, LEGACY_KEY);
		return legacy != null && !legacy.isEmpty();
	}

	public void savePlate(Plate plate) throws IOException
	{
		writeConfig(plate.getId(), PlateConfig.fromPlate(plate));
		log.debug("savePlate '{}' ({})", plate.getName(), plate.getId());
	}

	public void savePlateOrder(List<String> uuids)
	{
		String json = gson.toJson(uuids, STRING_LIST_TYPE);
		configManager.setConfiguration(Config.GROUP, PLATE_LIST_KEY, json);
		log.debug("savePlateOrder {}", uuids);
	}

	public void tombstonePlate(String uuid)
	{
		PlateConfig stored = readConfig(uuid);
		if (stored != null)
		{
			stored.setDeletedAt(System.currentTimeMillis());
			writeConfig(uuid, stored);
			log.debug("tombstonePlate {}", uuid);
		}
	}

	public String exportJson(Plate plate, boolean verbose)
	{
		PlateData data = plate.getData(verbose);
		data.setEnabled(null);
		data.setDisplayStyle(null);
		data.setIconStyle(null);
		return gson.toJson(data);
	}

	PlateData parseImportJson(String json) throws JsonSyntaxException
	{
		return gson.fromJson(json, PlateData.class);
	}

	public void migrateFromLegacy()
	{
		String json = configManager.getConfiguration(Config.GROUP, LEGACY_KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}

		List<PlateData> legacyPlates;
		try
		{
			legacyPlates = gson.fromJson(json, LEGACY_PLATES_TYPE);
		}
		catch (Exception e)
		{
			log.error("Failed to parse legacy plates for migration", e);
			return;
		}

		if (legacyPlates == null || legacyPlates.isEmpty())
		{
			savePlateOrder(Collections.emptyList());
			return;
		}

		List<String> uuids = new ArrayList<>();
		for (PlateData plate : legacyPlates)
		{
			if (plate.getId() == null)
			{
				log.warn("Skipping legacy plate with null ID");
				continue;
			}
			try
			{
				writeConfig(plate.getId(), PlateConfig.fromPlateData(plate));
				uuids.add(plate.getId());
			}
			catch (Exception e)
			{
				log.error("Failed to migrate plate '{}'", plate.getName(), e);
			}
		}
		savePlateOrder(uuids);
		log.info("Migrated {} plates to per-plate config keys", uuids.size());
	}

	public void cleanupTombstones()
	{
		long now = System.currentTimeMillis();
		String prefix = Config.GROUP + "." + PLATE_KEY_PREFIX;

		for (String fullKey : configManager.getConfigurationKeys(prefix))
		{
			String uuid = fullKey.substring(prefix.length());
			PlateConfig stored = readConfig(uuid);
			if (stored != null && stored.isExpiredTombstone(now))
			{
				log.debug("Cleaning up tombstoned plate {}", uuid);
				configManager.unsetConfiguration(Config.GROUP, PLATE_KEY_PREFIX + uuid);
			}
		}
	}

	@Nullable
	private PlateConfig readConfig(String uuid)
	{
		String json = configManager.getConfiguration(Config.GROUP, PLATE_KEY_PREFIX + uuid);
		if (json == null || json.isEmpty())
		{
			return null;
		}
		try
		{
			return gson.fromJson(json, PlateConfig.class);
		}
		catch (JsonSyntaxException e)
		{
			log.error("Failed to read plate config {}", uuid, e);
			return null;
		}
	}

	private void writeConfig(String uuid, PlateConfig config)
	{
		configManager.setConfiguration(Config.GROUP, PLATE_KEY_PREFIX + uuid, gson.toJson(config));
	}
}
