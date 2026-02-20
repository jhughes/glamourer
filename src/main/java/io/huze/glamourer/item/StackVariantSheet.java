package io.huze.glamourer.item;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

	private final CompletableFuture<Void> future;
	private volatile Map<Integer, Set<Integer>> variantsByItemId;

	@Inject
	public StackVariantSheet()
	{
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
		return CompletableFuture.runAsync(() -> {
			long startTime = System.nanoTime();
			Map<Integer, Set<Integer>> map = new HashMap<>();

			var is = getClass().getResourceAsStream("stack_variant_sheet.csv");
			if (is == null)
			{
				throw new RuntimeException("Failed to find stack variant sheet");
			}
			try (BufferedReader br = new BufferedReader(new InputStreamReader(is)))
			{
				String line;
				boolean isFirstLine = true;
				while ((line = br.readLine()) != null)
				{
					if (line.startsWith("#"))
					{
						continue;
					}
					if (isFirstLine)
					{
						if (!line.equals(String.join(",", CSV_HEADERS)))
						{
							throw new IllegalArgumentException();
						}
						isFirstLine = false;
						continue;
					}
					String[] parts = line.split(",");
					int id = Integer.parseInt(parts[0]);
					int variantId = Integer.parseInt(parts[1]);
					map.computeIfAbsent(id, k -> new HashSet<>()).add(variantId);
				}
			}
			catch (IOException | NumberFormatException e)
			{
				throw new RuntimeException("Failed to parse stack variant sheet CSV", e);
			}
			this.variantsByItemId = map;
			log.debug("StackVariantSheet load took {}ms", (System.nanoTime() - startTime) / 1_000_000);
		});
	}
}
