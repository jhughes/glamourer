package io.huze.glamourer;

import static io.huze.glamourer.Config.GROUP;
import io.huze.glamourer.color.ColorGroupSettings;
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

	String KEY_COLOR_GROUP_HUE_DIST = "colorGroupHueDist";
	@ConfigItem(
		keyName = KEY_COLOR_GROUP_HUE_DIST,
		name = "Color Group Hue Distance",
		description = "Max hue distance for color grouping (0 = identical only, 1 = any).",
		hidden = true
	)
	default double colorGroupHueDist()
	{
		return ColorGroupSettings.DEFAULT.getMaxHueDist();
	}
	@ConfigItem(
		keyName = KEY_COLOR_GROUP_HUE_DIST,
		name = "Color Group Hue Distance",
		description = "Max hue distance for color grouping (0 = identical only, 1 = any).",
		hidden = true
	)
	void setColorGroupHueDist(double dist);


	String KEY_COLOR_GROUP_SAT_DIST = "colorGroupSatDist";
	@ConfigItem(
		keyName = KEY_COLOR_GROUP_SAT_DIST,
		name = "Color Group Saturation Distance",
		description = "Max saturation distance for color grouping (0 = identical only, 1 = any).",
		hidden = true
	)
	default double colorGroupSatDist()
	{
		return ColorGroupSettings.DEFAULT.getMaxSatDist();
	}
	@ConfigItem(
		keyName = KEY_COLOR_GROUP_SAT_DIST,
		name = "Color Group Saturation Distance",
		description = "Max saturation distance for color grouping (0 = identical only, 1 = any).",
		hidden = true
	)
	void setColorGroupSatDist(double dist);


	String KEY_COLOR_GROUP_LUM_DIST = "colorGroupLumDist";
	@ConfigItem(
		keyName = KEY_COLOR_GROUP_LUM_DIST,
		name = "Color Group Luminance Distance",
		description = "Max luminance distance for color grouping (0 = identical only, 1 = any).",
		hidden = true
	)
	default double colorGroupLumDist()
	{
		return ColorGroupSettings.DEFAULT.getMaxLumDist();
	}
	@ConfigItem(
		keyName = KEY_COLOR_GROUP_LUM_DIST,
		name = "Color Group Saturation Distance",
		description = "Max saturation distance for color grouping (0 = identical only, 1 = any).",
		hidden = true
	)
	void setColorGroupLumDist(double dist);


	String KEY_COLOR_GROUP_SEPARATE_GRAYSCALE = "colorGroupSeparateGrayscale";
	@ConfigItem(
		keyName = KEY_COLOR_GROUP_SEPARATE_GRAYSCALE,
		name = "Color Group Separate Grayscale",
		description = "Keep grayscale (black/white/gray) colors in their own groups, separate from colored entries.",
		hidden = true
	)
	default boolean colorGroupSeparateGrayscale()
	{
		return ColorGroupSettings.DEFAULT.isSeparateGrayscale();
	}
	@ConfigItem(
		keyName = KEY_COLOR_GROUP_SEPARATE_GRAYSCALE,
		name = "Color Group Separate Grayscale",
		description = "Keep grayscale (black/white/gray) colors in their own groups, separate from colored entries.",
		hidden = true
	)
	void setColorGroupSeparateGrayscale(boolean separateGrayscale);
}
