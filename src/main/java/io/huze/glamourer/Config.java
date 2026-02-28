package io.huze.glamourer;

import static io.huze.glamourer.Config.GROUP;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(GROUP)
public interface Config extends net.runelite.client.config.Config
{
	String GROUP = "glamourer";

	String KEY_NAV_PRIORITY = "navPriority";

	@ConfigItem(
		keyName = KEY_NAV_PRIORITY,
		name = "Navigation Bar Priority",
		description = "Adjusts the order in the nav bar."
	)
	default int navPriority()
	{
		return 10;
	}

	String KEY_ICON_SCALE = "iconScale";

	@ConfigItem(
		keyName = KEY_ICON_SCALE,
		name = "Icon Scale",
		description = "Scale of item icons in the panel (percentage)."
	)
	@Range(min = 50, max = 200)
	default int iconScale()
	{
		return 150;
	}

	String KEY_ADVANCED_OPTIONS = "advancedOptions";

	@ConfigItem(
		keyName = KEY_ADVANCED_OPTIONS,
		name = "Advanced Options",
		description = "Show advanced options in menus."
	)
	default boolean advancedOptions()
	{
		return false;
	}
}
