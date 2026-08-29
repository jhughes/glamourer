package io.huze.glamourer.glam;

import io.huze.glamourer.plate.DisplayStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class Glamourer
{
	private final GlamourEngine engine;
	private final ClientThread clientThread;

	@Nonnull
	public CompletableFuture<Glamour> startGlamourAsync(int itemId)
	{
		CompletableFuture<Glamour> future = new CompletableFuture<>();
		clientThread.invokeLater(() -> {
			future.complete(engine.startGlamour(itemId));
		});
		return future;
	}

	@Nonnull
	public CompletableFuture<GlamourLoadResult> loadGlamoursAsync(@Nonnull List<GlamourData> dataList)
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

	public void apply(@Nonnull Glamour glam, @Nonnull DisplayStyle displayStyle)
	{
		engine.stageApply(glam, displayStyle);
	}

	public void batch(@Nonnull Runnable action)
	{
		engine.batch(action);
	}

	@Nonnull
	public GlamourVisibility getVisibility(@Nonnull Glamour glam, boolean plateEnabled)
	{
		if (!plateEnabled)
		{
			return GlamourVisibility.DISABLED;
		}
		return engine.getStagedVisibility(glam);
	}

	@Setter
	@Nonnull
	private Consumer<Glamour> onHighlightOverrideChanged = override -> {};

	public void setHighlightOverride(@Nonnull Glamour override)
	{
		engine.setHighlightOverride(override);
		onHighlightOverrideChanged.accept(override);
	}

	public void clearHighlightOverride()
	{
		engine.setHighlightOverride(null);
		onHighlightOverrideChanged.accept(null);
	}
}
