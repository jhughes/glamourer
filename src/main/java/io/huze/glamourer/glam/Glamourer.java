package io.huze.glamourer.glam;

import io.huze.glamourer.plate.DisplayStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class Glamourer
{
	private final GlamourEngine engine;
	private final ClientThread clientThread;

	public CompletableFuture<Glamour> startGlamourAsync(int itemId)
	{
		CompletableFuture<Glamour> future = new CompletableFuture<>();
		clientThread.invokeLater(() -> {
			future.complete(engine.startGlamour(itemId));
		});
		return future;
	}

	public CompletableFuture<GlamourLoadResult> loadGlamoursAsync(List<GlamourData> dataList)
	{
		CompletableFuture<GlamourLoadResult> future = new CompletableFuture<>();
		clientThread.invokeLater(() -> {
			var loaded = new ArrayList<Glamour>();
			var failed = new ArrayList<GlamourData>();
			for (GlamourData data : dataList)
			{
				try
				{
					loaded.add(engine.loadGlamour(data));
				}
				catch (Throwable e)
				{
					log.error("Failed to load glamour for item {}", data, e);
					failed.add(data);
				}
			}
			future.complete(new GlamourLoadResult(loaded, failed));
		});
		return future;
	}

	public void apply(Glamour glam, DisplayStyle displayStyle)
	{
		engine.stageApply(glam, displayStyle);
	}

	public void batch(Runnable action)
	{
		engine.batch(action);
	}

	public GlamourVisibility getVisibility(Glamour glam, boolean plateEnabled)
	{
		if (!plateEnabled)
		{
			return GlamourVisibility.DISABLED;
		}
		return engine.getStagedVisibility(glam);
	}

	public void setLocalEquipmentOverride(@Nonnull Glamour override)
	{
		engine.setLocalEquipmentOverride(override);
	}

	public void clearLocalEquipmentOverride()
	{
		engine.setLocalEquipmentOverride(null);
	}
}
