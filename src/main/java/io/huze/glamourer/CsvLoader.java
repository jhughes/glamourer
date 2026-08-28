package io.huze.glamourer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@Singleton
public class CsvLoader
{
	private static final String BASE_URL = "https://raw.githubusercontent.com/jhughes/glamourer_data/master/v1/";

	private final OkHttpClient httpClient;
	private final String baseUrl;
	private final File cacheDir;
	private final File devSheetDir;

	@Inject
	public CsvLoader(OkHttpClient httpClient, @Named("developerMode") boolean developerMode)
	{
		this(httpClient, BASE_URL, new File(RuneLite.RUNELITE_DIR, "glamourer"), devSheetDir(developerMode));
	}

	CsvLoader(OkHttpClient httpClient, String baseUrl, File cacheDir, @Nullable File devSheetDir)
	{
		this.httpClient = httpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.readTimeout(Duration.ofSeconds(5))
			.build();
		this.baseUrl = baseUrl;
		this.cacheDir = cacheDir;
		this.devSheetDir = devSheetDir;
	}

	private static @Nullable File devSheetDir(boolean developerMode)
	{
		String dir = System.getProperty("glamourer.sheets");
		return developerMode && dir != null ? new File(dir) : null;
	}

	public static @Nonnull <T> List<T> parseList(String column, Function<String, T> elementMapper)
	{
		if (column.isEmpty())
		{
			return List.of();
		}
		List<T> parsed = new ArrayList<>();
		for (String part : column.split("\\|"))
		{
			parsed.add(elementMapper.apply(part.trim()));
		}
		return Collections.unmodifiableList(parsed);
	}

	public @Nonnull <T> List<T> load(String filename, String[] expectedHeaders, Function<String[], T> rowMapper)
	{
		if (devSheetDir != null)
		{
			try
			{
				return parse(new FileInputStream(new File(devSheetDir, filename)), expectedHeaders, rowMapper);
			}
			catch (IOException e)
			{
				throw new RuntimeException("Failed to load " + filename + " from " + devSheetDir, e);
			}
		}

		try
		{
			byte[] remote = fetchRemote(filename);
			List<T> rows = parse(new ByteArrayInputStream(remote), expectedHeaders, rowMapper);
			cache(filename, remote);
			return rows;
		}
		catch (Exception e)
		{
			log.warn("Failed to load remote {}. Trying the cached copy", filename, e);
		}
		try
		{
			return parse(new FileInputStream(new File(cacheDir, filename)), expectedHeaders, rowMapper);
		}
		catch (IOException e)
		{
			throw new RuntimeException("No remote or cached copy of " + filename, e);
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

	private byte[] fetchRemote(String filename) throws IOException
	{
		final var startTime = System.nanoTime();
		Request request = new Request.Builder()
			.url(baseUrl + filename)
			.build();
		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null)
			{
				throw new IOException("HTTP " + response.code() + " fetching " + filename);
			}
			byte[] body = response.body().bytes();
			log.debug("Fetched {} from GitHub in {}ms", filename, (System.nanoTime() - startTime) / 1_000_000);
			return body;
		}
	}

	/// The last good fetch, kept for runs where the repo is unreachable. Written only after the
	/// content parsed, so a bad publish can't poison it.
	private void cache(String filename, byte[] content)
	{
		try
		{
			cacheDir.mkdirs();
			Path target = new File(cacheDir, filename).toPath();
			Path temp = new File(cacheDir, filename + ".tmp").toPath();
			Files.write(temp, content);
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e)
		{
			log.warn("Failed to cache {}", filename, e);
		}
	}
}
