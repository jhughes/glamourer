package io.huze.glamourer.plate;

import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.glam.IconService;
import io.huze.glamourer.glam.Glamourer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class PlateManager
{
	private final PlateStore plateStore;
	@Getter
	private final Glamourer glamourer;
	@Getter
	private final IconService iconService;
	@Getter
	private final ChangeLog changeLog;

	@Getter
	private final List<Plate> plates = new ArrayList<>();
	@Setter
	private Consumer<Void> onPlatesChanged;

	@Getter
	private boolean starterPlateNeeded;
	private int loadGeneration = 0;


	public CompletableFuture<Void> loadPlates() throws IOException
	{
		final int generation = ++loadGeneration;
		plates.clear();
		starterPlateNeeded = false;

		if (plateStore.isLegacyFormat())
		{
			plateStore.migrateFromLegacy();
		}
		plateStore.cleanupTombstones();

		List<PlateData> dataList = plateStore.loadAllPlates();
		if (dataList.isEmpty())
		{
			starterPlateNeeded = true;
			notifyPlatesChanged();
			return CompletableFuture.completedFuture(null);
		}

		List<CompletableFuture<Plate>> futures = new ArrayList<>();
		for (PlateData data : dataList)
		{
			futures.add(Plate.loadFromData(data, glamourer, changeLog, this));
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
			.thenRun(() -> {
				if (generation != loadGeneration)
				{
					log.debug("Discarding stale plate load (gen {} != {})", generation, loadGeneration);
					return;
				}
				for (CompletableFuture<Plate> f : futures)
				{
					Plate plate = f.join();
					plates.add(plate);
				}
				starterPlateNeeded = plates.isEmpty();
				log.info("Loaded {} plates", plates.size());
				notifyPlatesChanged();
			});
	}

	void saveSinglePlate(Plate plate)
	{
		try
		{
			plateStore.savePlate(plate);
		}
		catch (Exception e)
		{
			log.error("Failed to save plate '{}'", plate.getName(), e);
		}
	}

	void savePlateOrder()
	{
		plateStore.savePlateOrder(plates.stream().map(Plate::getId).collect(Collectors.toList()));
	}

	void tombstonePlate(String id)
	{
		plateStore.tombstonePlate(id);
	}

	public void createPlate()
	{
		Plate plate = Plate.newEmptyPlate(glamourer, changeLog, this);
		apply(new PlateCreateChange(this, plate));
	}

	public CompletableFuture<Void> createStarterPlate(Collection<Integer> itemIds)
	{
		final int generation = loadGeneration;
		starterPlateNeeded = false;
		List<CompletableFuture<Glamour>> futures = new ArrayList<>();
		for (int itemId : itemIds)
		{
			futures.add(glamourer.startGlamourAsync(itemId));
		}
		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
			.thenRun(() -> {
				if (generation != loadGeneration)
				{
					log.debug("Discarding stale starter plate creation (gen {} != {})", generation, loadGeneration);
					return;
				}
				ArrayList<Glamour> glamours = new ArrayList<>();
				for (CompletableFuture<Glamour> f : futures)
				{
					glamours.add(f.join());
				}
				glamours.sort(Comparator.comparing(Glamour::getItemName));
				Plate plate = Plate.newPlate("My Equipment (Example)", glamourer, changeLog, this, glamours);
				plates.add(plate);
				reapplyAllPlates();
				saveSinglePlate(plate);
				savePlateOrder();
				notifyPlatesChanged();
			});
	}

	public void deletePlate(String id)
	{
		for (int i = 0; i < plates.size(); i++)
		{
			if (plates.get(i).getId().equals(id))
			{
				apply(new PlateDeleteChange(this, plates.get(i), i));
				return;
			}
		}
	}

	public void movePlate(int fromIndex, int toIndex)
	{
		int size = plates.size();
		if (fromIndex < 0 || fromIndex >= size || toIndex < 0 || toIndex >= size || fromIndex == toIndex)
		{
			return;
		}
		apply(new PlateReorderChange(this, fromIndex, toIndex));
	}

	public void setPlateEnabled(Plate plate, boolean enabled)
	{
		if (plate.isEnabled() == enabled)
		{
			return;
		}
		apply(new PlateEnableChange(this, plate, plate.isEnabled()));
	}

	public void setPlateDisplayStyle(Plate plate, DisplayStyle displayStyle)
	{
		apply(new PlateDisplayStyleChange(this, plate, plate.getDisplayStyle(), displayStyle));
	}

	public void setPlateIconStyle(Plate plate, IconStyle iconStyle)
	{
		apply(new PlateIconStyleChange(this, plate, plate.getIconStyle(), iconStyle));
	}

	public void removeGlamour(Plate plate, int glamourIndex)
	{
		Glamour removed = plate.getGlamours().get(glamourIndex);
		apply(new GlamourRemoveChange(this, plate, removed, glamourIndex));
	}

	public CompletableFuture<Void> addGlamour(Plate plate, int itemId)
	{
		return glamourer.startGlamourAsync(itemId).thenAccept(glamour ->
			apply(new GlamourAddChange(this, plate, glamour, plate.getGlamours().size())));
	}

	public void moveGlamour(Plate plate, int fromIndex, int toIndex)
	{
		String itemName = plate.getGlamours().get(fromIndex).getItemName();
		apply(new GlamourReorderChange(this, plate, fromIndex, toIndex, itemName));
	}

	public void transferGlamour(Plate sourcePlate, int sourceIndex, Plate targetPlate, int targetIndex)
	{
		Glamour glam = sourcePlate.getGlamours().get(sourceIndex);
		if (targetPlate.containsItem(glam.getPrimaryItemId()))
		{
			return;
		}
		apply(new GlamourMoveChange(this, sourcePlate, sourceIndex, targetPlate, targetIndex, glam));
	}

	public CompletableFuture<Void> importPlateAsNew(String json)
	{
		PlateData data = plateStore.parseImportJson(json);
		data.setId(UUID.randomUUID().toString());
		return importPlateData(data);
	}

	public CompletableFuture<Void> importPlate(String json)
	{
		return importPlateData(plateStore.parseImportJson(json));
	}

	private CompletableFuture<Void> importPlateData(PlateData data)
	{
		data.setEnabled(true);

		final int generation = loadGeneration;
		final String importId = data.getId();
		return Plate.loadFromData(data, glamourer, changeLog, this).thenAccept(plate -> {
			if (generation != loadGeneration)
			{
				log.debug("Discarding stale plate import (gen {} != {})", generation, loadGeneration);
				return;
			}
			int existingIndex = -1;
			for (int i = 0; i < plates.size(); i++)
			{
				if (plates.get(i).getId().equals(importId))
				{
					existingIndex = i;
					break;
				}
			}
			if (existingIndex >= 0)
			{
				plates.set(existingIndex, plate);
			}
			else
			{
				plates.add(plate);
			}

			reapplyAllPlates();
			saveSinglePlate(plate);
			savePlateOrder();
			changeLog.clear();
			notifyPlatesChanged();
		}).exceptionally(e -> {
			log.error("Failed to import plate", e);
			return null;
		});
	}

	public String exportPlateJson(Plate plate, boolean verbose)
	{
		return plateStore.exportJson(plate, verbose);
	}

	public CompletableFuture<Plate> loadImportPreview(String json)
	{
		PlateData data = plateStore.parseImportJson(json);
		if (data == null || data.getName() == null || data.getGlamours() == null)
		{
			throw new IllegalArgumentException("Invalid plate data: missing required fields.");
		}
		return Plate.loadFromData(data, glamourer, changeLog, null);
	}

	public boolean hasPlateWithId(String id)
	{
		return plates.stream().anyMatch(plate -> plate.getId().equals(id));
	}

	public void reapplyAllPlates()
	{
		glamourer.batch(() -> plates.forEach(Plate::applyAll));
	}

	private void apply(Change change)
	{
		change.redo();
		change.save();
		changeLog.record(change);
		notifyPlatesChanged();
	}

	private void notifyPlatesChanged()
	{
		if (onPlatesChanged != null)
		{
			onPlatesChanged.accept(null);
		}
	}
}
