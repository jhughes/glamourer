package io.huze.glamourer.party;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.huze.glamourer.Config;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
@Singleton
public class PlayerBlacklist
{
	static final String CONFIG_KEY = "playerBlacklist";
	private static final Type STRING_SET_TYPE = new TypeToken<Set<String>>()
	{
	}.getType();

	private final ConfigManager configManager;
	private final Gson gson;
	private final Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

	@Inject
	public PlayerBlacklist(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	@Inject
	public synchronized void load()
	{
		names.clear();
		String json = configManager.getConfiguration(Config.GROUP, CONFIG_KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Set<String> parsed = gson.fromJson(json, STRING_SET_TYPE);
			if (parsed != null)
			{
				names.addAll(parsed);
			}
		}
		catch (JsonSyntaxException e)
		{
			log.error("Failed to parse hidden party members", e);
		}
	}

	public synchronized boolean contains(String name)
	{
		return names.contains(name);
	}

	public synchronized void add(String name)
	{
		if (names.add(name))
		{
			save();
		}
	}

	public synchronized void remove(String name)
	{
		if (names.remove(name))
		{
			save();
		}
	}

	private void save()
	{
		if (names.isEmpty())
		{
			configManager.unsetConfiguration(Config.GROUP, CONFIG_KEY);
		}
		else
		{
			configManager.setConfiguration(Config.GROUP, CONFIG_KEY, gson.toJson(names));
		}
	}
}
