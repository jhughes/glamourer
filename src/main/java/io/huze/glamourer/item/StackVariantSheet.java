package io.huze.glamourer.item;

import io.huze.glamourer.CsvLoader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class StackVariantSheet
{
	private static final String[] CSV_HEADERS = {"id", "variantId"};

	private final CsvLoader csvLoader;
	private final CompletableFuture<Void> future;
	private volatile Map<Integer, Set<Integer>> variantsByItemId;

	@Inject
	public StackVariantSheet(CsvLoader csvLoader)
	{
		this.csvLoader = csvLoader;
		future = loadAsync();
	}

	public boolean isLoadedOrRethrow()
	{
		if (future.isCompletedExceptionally())
		{
			future.join();
		}
		return future.isDone();
	}

	public Set<Integer> getVariants(int itemId)
	{
		return variantsByItemId.getOrDefault(itemId, Collections.emptySet());
	}

	private CompletableFuture<Void> loadAsync()
	{
		final var startTime = System.nanoTime();
		return CompletableFuture.runAsync(() -> {
			Map<Integer, Set<Integer>> map = new HashMap<>();
			for (int[] row : csvLoader.load(StackVariantSheet.class, "stack_variant_sheet.csv", CSV_HEADERS,
				cols -> new int[]{Integer.parseInt(cols[0]), Integer.parseInt(cols[1])}))
			{
				map.computeIfAbsent(row[0], k -> new HashSet<>()).add(row[1]);
			}
			this.variantsByItemId = map;
			log.debug("StackVariantSheet loaded in {}ms", (System.nanoTime() - startTime) / 1_000_000);
		});
	}
}
