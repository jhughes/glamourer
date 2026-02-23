package io.huze.glamourer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.inject.Inject;
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

	@Inject
	public CsvLoader(OkHttpClient httpClient)
	{
		this.httpClient = httpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.readTimeout(Duration.ofSeconds(5))
			.build();
	}

	public @Nonnull <T> List<T> load(Class<?> resourceClass, String filename, String[] expectedHeaders, Function<String[], T> rowMapper)
	{
		try
		{
			return parse(fetchRemote(resourceClass, filename), expectedHeaders, rowMapper);
		}
		catch (IOException e)
		{
			log.warn("Failed to load remote {}. Loading local resource", filename, e);
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
					if (!line.equals(String.join(",", expectedHeaders)))
					{
						throw new IllegalArgumentException("Unexpected CSV headers: " + line);
					}
					isFirstLine = false;
					continue;
				}
				results.add(rowMapper.apply(line.split(",", -1)));
			}
		}
		return results;
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
