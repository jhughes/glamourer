package io.huze.glamourer.item;

import io.huze.glamourer.CsvLoader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/// Maps dummy items to the real item they visually match.
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class DummyItemSheet
{
	private static final String[] CSV_HEADERS = {"dummyItemId", "itemId"};

	private final CsvLoader csvLoader;
	private CompletableFuture<Void> future;

	@Inject
	public void start()
	{
		this.future = loadAsync();
	}
	private volatile Map<Integer, Integer> dummyToRealId = Collections.emptyMap();

	public boolean isLoadedOrRethrow()
	{
		if (future.isCompletedExceptionally())
		{
			future.join();
		}
		return future.isDone();
	}

	public int getItemIdForVisibleItemId(int visibleItemId)
	{
		return dummyToRealId.getOrDefault(visibleItemId, visibleItemId);
	}

	private CompletableFuture<Void> loadAsync()
	{
		final var startTime = System.nanoTime();
		return CompletableFuture.runAsync(() -> {
			Map<Integer, Integer> map = new HashMap<>();
			for (int[] row : csvLoader.load(DummyItemSheet.class, "dummy_item_sheet.csv", CSV_HEADERS,
				cols -> new int[]{Integer.parseInt(cols[0]), Integer.parseInt(cols[1])}))
			{
				map.put(row[0], row[1]);
			}
			this.dummyToRealId = map;
			log.debug("DummyItemSheet loaded in {}ms", (System.nanoTime() - startTime) / 1_000_000);
		});
	}
}
