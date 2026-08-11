package io.huze.glamourer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
public class CsvLoader
{
	private static final String BASE_URL = "https://raw.githubusercontent.com/jhughes/glamourer/master/src/main/resources/";
	private final OkHttpClient httpClient;
	private final boolean developerMode;

	@Inject
	public CsvLoader(OkHttpClient httpClient, @Named("developerMode") boolean developerMode)
	{
		this.httpClient = httpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.readTimeout(Duration.ofSeconds(5))
			.build();
		this.developerMode = developerMode;
	}

	public @Nonnull <T> List<T> load(Class<?> resourceClass, String filename, String[] expectedHeaders, Function<String[], T> rowMapper)
	{
		if (developerMode)
		{
			log.info("Dev Mode enabled; skipping remote repo fetch");
		}
		else
		{
			try
			{
				return parse(fetchRemote(resourceClass, filename), expectedHeaders, rowMapper);
			}
			catch (Exception e)
			{
				log.warn("Failed to load remote {}. Loading local resource", filename, e);
			}
		}
		try
		{
			return parse(fetchLocal(resourceClass, filename), expectedHeaders, rowMapper);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Failed to parse local resource " + filename, e);
		}
	}

	private @Nonnull <T> List<T> parse(InputStream is, String[] expectedHeaders, Function<String[], T> rowMapper) throws IOException
	{
		List<T> results = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)))
		{
			String line;
			int[] columnMapping = null;
			String[] reordered = new String[expectedHeaders.length];
			while ((line = br.readLine()) != null)
			{
				if (line.isBlank() || line.startsWith("#"))
				{
					continue;
				}
				if (columnMapping == null)
				{
					columnMapping = buildColumnMapping(line, expectedHeaders);
					continue;
				}
				String[] unordered = line.split(",", -1);
				for (int i = 0; i < expectedHeaders.length; i++)
				{
					reordered[i] = unordered[columnMapping[i]].trim();
				}
				results.add(rowMapper.apply(reordered));
			}
		}
		return results;
	}

	private int[] buildColumnMapping(String headerLine, String[] expectedHeaders) throws IOException
	{
		String[] actualHeaders = headerLine.split(",", -1);
		Map<String, Integer> nameToIndex = new HashMap<>();
		for (int i = 0; i < actualHeaders.length; i++)
		{
			nameToIndex.put(actualHeaders[i].trim(), i);
		}
		int[] mapping = new int[expectedHeaders.length];
		for (int i = 0; i < expectedHeaders.length; i++)
		{
			Integer idx = nameToIndex.get(expectedHeaders[i]);
			if (idx == null)
			{
				throw new IOException(
					"Missing expected column '" + expectedHeaders[i] + "' in headers: " + Arrays.toString(actualHeaders));
			}
			mapping[i] = idx;
		}
		return mapping;
	}

	private @Nonnull InputStream fetchRemote(Class<?> resourceClass, String filename) throws IOException
	{
		final var startTime = System.nanoTime();
		String resourcePath = resourceClass.getPackageName().replace('.', '/') + "/" + filename;
		Request request = new Request.Builder()
			.url(BASE_URL + resourcePath)
			.build();
		Response response = httpClient.newCall(request).execute();
		if (!response.isSuccessful() || response.body() == null)
		{
			response.close();
			throw new IOException("HTTP " + response.code() + " fetching " + filename);
		}
		log.debug("Fetched {} from GitHub in {}ms", filename, (System.nanoTime() - startTime) / 1_000_000);
		return response.body().byteStream();
	}

	private @Nonnull InputStream fetchLocal(Class<?> resourceClass, String filename)
	{
		var localStream = resourceClass.getResourceAsStream(filename);
		if (localStream == null)
		{
			throw new RuntimeException("Failed to find " + filename);
		}
		return localStream;
	}
}
