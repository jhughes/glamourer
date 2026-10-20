package io.huze.glamourer;

import static io.huze.glamourer.Config.GROUP;
import io.huze.glamourer.color.ColorGroupSettings;
import io.huze.glamourer.glam.IconScale;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

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

	String KEY_HIDE_SIDEBAR_ICON = "hideSidebarIcon";

	@ConfigItem(
		keyName = KEY_HIDE_SIDEBAR_ICON,
		name = "Hide Sidebar Icon",
		description = "Remove the Glamourer button from the sidebar. Glamours stay active."
	)
	default boolean hideSidebarIcon()
	{
		return false;
	}

	String KEY_ICON_SCALE = "iconScale";

	@ConfigItem(
		keyName = KEY_ICON_SCALE,
		name = "Icon Scale",
		description = "Scale of item icons in the panel."
	)
	default IconScale iconScale()
	{
		return IconScale.PERCENT_100;
	}

	String KEY_SHADE_INTENSITY = "shadeIntensity";

	@ConfigItem(
		keyName = KEY_SHADE_INTENSITY,
		name = "Shade Intensity",
		description = "How much of the game's lighting the item viewer and color swatches show, from none to all of it."
	)
	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	default int shadeIntensity()
	{
		return 100;
	}

	// --- Party Sync Section ---

	@ConfigSection(
		name = "Party Sync",
		description = "Glamour synchronization for party members",
		position = 3
	)
	String SECTION_PARTY_SYNC = "sectionPartySync";

	String KEY_PARTY_SYNC_SEND = "partySyncSend";
	String NAME_PARTY_SYNC_SEND = "Sync your appearance";
	String DESC_PARTY_SYNC_SEND = "Send your glamours to others in the same RuneLite party.";
	@ConfigItem(
		keyName = KEY_PARTY_SYNC_SEND,
		name = NAME_PARTY_SYNC_SEND,
		description = DESC_PARTY_SYNC_SEND,
		section = SECTION_PARTY_SYNC
	)
	default boolean partySyncSend()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_PARTY_SYNC_SEND,
		name = "",
		description = "",
		section = SECTION_PARTY_SYNC
	)
	void setPartySyncSend(boolean sync);

	String KEY_PARTY_SYNC_RECV = "partySyncReceive";
	String NAME_PARTY_SYNC_RECV = "Sync others' appearances";
	String DESC_PARTY_SYNC_RECV = "Apply glamours received from others in the same RuneLite party.";
	@ConfigItem(
		keyName = KEY_PARTY_SYNC_RECV,
		name = NAME_PARTY_SYNC_RECV,
		description = DESC_PARTY_SYNC_RECV,
		section = SECTION_PARTY_SYNC
	)
	default boolean partySyncReceive()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_PARTY_SYNC_RECV,
		name = "",
		description = "",
		section = SECTION_PARTY_SYNC
	)
	void setPartySyncReceive(boolean sync);

	String KEY_PARTY_SYNC_PROMPTED = "partySyncPrompted";
	@ConfigItem(
		keyName = KEY_PARTY_SYNC_PROMPTED,
		name = "",
		description = "",
		hidden = true
	)
	default boolean partySyncPrompted()
	{
		return false;
	}
	@ConfigItem(
		keyName = KEY_PARTY_SYNC_PROMPTED,
		name = "",
		description = "",
		hidden = true
	)
	void setPartySyncPrompted(boolean prompted);

	// --- Advanced Section ---

	@ConfigSection(
		name = "Advanced options",
		description = "Power user / developer options",
		position = 4,
		closedByDefault = true
	)
	String SECTION_ADVANCED = "sectionAdvancedOptions";

	String KEY_EXPANDED_RIGHT_CLICK = "advancedOptions";

	@ConfigItem(
		keyName = KEY_EXPANDED_RIGHT_CLICK,
		name = "Expanded Right Click Options",
		description = "Show advanced options in menus.",
		section = SECTION_ADVANCED
	)
	default boolean expandedRightClick()
	{
		return false;
	}

	// --- Hidden ---

	String KEY_GLAMOUR_WINDOW_WIDTH = "glamourWindowWidth";
	@ConfigItem(
		keyName = KEY_GLAMOUR_WINDOW_WIDTH,
		name = "",
		description = "",
		hidden = true
	)
	default int glamourWindowWidth()
	{
		return 680;
	}

	String KEY_GLAMOUR_WINDOW_HEIGHT = "glamourWindowHeight";
	@ConfigItem(
		keyName = KEY_GLAMOUR_WINDOW_HEIGHT,
		name = "",
		description = "",
		hidden = true
	)
	default int glamourWindowHeight()
	{
		return 420;
	}

	String KEY_GLAMOUR_WINDOW_COLORS_WIDTH = "glamourWindowColorsWidth";
	@ConfigItem(
		keyName = KEY_GLAMOUR_WINDOW_COLORS_WIDTH,
		name = "",
		description = "",
		hidden = true
	)
	default int glamourWindowColorsWidth()
	{
		return 300;
	}

	String KEY_GLAMOUR_GIZMO = "glamourGizmo";
	@ConfigItem(
		keyName = KEY_GLAMOUR_GIZMO,
		name = "",
		description = "",
		hidden = true
	)
	default boolean glamourGizmo()
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
		name = "",
		description = "",
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
		name = "",
		description = "",
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
		name = "",
		description = "",
		hidden = true
	)
	void setColorGroupLumDist(double dist);


	String KEY_COLOR_GROUP_SEPARATE_GRAYSCALE = "colorGroupSeparateGrayscale";
	@ConfigItem(
		keyName = KEY_COLOR_GROUP_SEPARATE_GRAYSCALE,
		name = "Color Group Separate Grayscale",
		description = "",
		hidden = true
	)
	default boolean colorGroupSeparateGrayscale()
	{
		return ColorGroupSettings.DEFAULT.isSeparateGrayscale();
	}
	@ConfigItem(
		keyName = KEY_COLOR_GROUP_SEPARATE_GRAYSCALE,
		name = "Color Group Separate Grayscale",
		description = "",
		hidden = true
	)
	void setColorGroupSeparateGrayscale(boolean separateGrayscale);
}
