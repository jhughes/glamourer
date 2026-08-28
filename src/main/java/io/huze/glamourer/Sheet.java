package io.huze.glamourer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class Sheet<T>
{
	private final String filename;
	private final String[] headers;
	private final Function<String[], T> rowMapper;

	@Inject
	private CsvLoader csvLoader;
	private CompletableFuture<Void> future;

	protected Sheet(String filename, String[] headers, Function<String[], T> rowMapper)
	{
		this.filename = filename;
		this.headers = headers;
		this.rowMapper = rowMapper;
	}

	/// Guice injects fields before methods, so the loader is set by the time this runs.
	@Inject
	public final void start()
	{
		future = CompletableFuture.runAsync(() -> {
			final var startTime = System.nanoTime();
			load(csvLoader.load(filename, headers, rowMapper));
			log.debug("{} loaded in {}ms", getClass().getSimpleName(), (System.nanoTime() - startTime) / 1_000_000);
		});
	}

	public final boolean isLoadedOrRethrow()
	{
		if (future.isCompletedExceptionally())
		{
			future.join();
		}
		return future.isDone();
	}

	protected abstract void load(List<T> rows);
}
