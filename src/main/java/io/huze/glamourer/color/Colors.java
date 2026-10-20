package io.huze.glamourer.color;

import java.awt.Color;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.runelite.api.JagexColor;
import static net.runelite.api.JagexColor.packHSL;
import static net.runelite.api.JagexColor.unpackHue;
import static net.runelite.api.JagexColor.unpackLuminance;
import static net.runelite.api.JagexColor.unpackSaturation;

public class Colors
{
	// Inclusive max ints
	public static final int MAX_HUE = JagexColor.HUE_MAX;
	public static final int MAX_SAT = JagexColor.SATURATION_MAX;
	public static final int MAX_LUM = JagexColor.LUMINANCE_MAX;
	// Exclusive max doubles
	private static final double MAX_HUE_D = MAX_HUE + 1;
	private static final double MAX_SAT_D = MAX_SAT + 1;
	private static final double MAX_LUM_D = MAX_LUM + 1;

	private static final double HUE_OFFSET = (.5D / MAX_HUE_D);
	private static final double SATURATION_OFFSET = (.5D / MAX_SAT_D);
	private static final double HUE_CONE_COUNT = 6;

	public static String formatHSL(short hsl)
	{
		return String.format("%dH/%dS/%dL", unpackHue(hsl), unpackSaturation(hsl), unpackLuminance(hsl));
	}

	public static Color hslToColor(short hsl)
	{
		return hslToColor(unpackHue(hsl), unpackSaturation(hsl), unpackLuminance(hsl));
	}

	public static Color hslToColor(int hue, int sat, int lum)
	{
		double h = (hue / MAX_HUE_D + HUE_OFFSET) * HUE_CONE_COUNT;
		double s = (double) sat / MAX_SAT_D + SATURATION_OFFSET;
		double l = (double) lum / MAX_LUM_D;

		double chroma = (1D - Math.abs(2D * l - 1D)) * s;
		double x = chroma * (1D - Math.abs(h % 2D - 1D));
		double lightness = l - (chroma / 2D);

		double r = lightness, g = lightness, b = lightness;
		switch ((int) h)
		{
			case 0:
				r += chroma;
				g += x;
				break;
			case 1:
				g += chroma;
				r += x;
				break;
			case 2:
				g += chroma;
				b += x;
				break;
			case 3:
				b += chroma;
				g += x;
				break;
			case 4:
				b += chroma;
				r += x;
				break;
			default:
				r += chroma;
				b += x;
				break;
		}
		return new Color((int) (r * 256D), (int) (g * 256D), (int) (b * 256D));
	}

	public static Color hslToShadedColor(short hsl, int shade)
	{
		return hslToShadedColor(unpackHue(hsl), unpackSaturation(hsl), unpackLuminance(hsl), shade);
	}

	public static Color hslToShadedColor(int hue, int sat, int lum, int shade)
	{
		return hslToColor(hue, sat, Shade.scale(lum, shade));
	}

	/// Applied in order so replacements will chain.
	public static short applyReplacements(short value, @Nullable short[] find, @Nullable short[] replace)
	{
		if (find == null || replace == null)
		{
			return value;
		}
		for (int i = 0; i < find.length && i < replace.length; i++)
		{
			if (value == find[i])
			{
				value = replace[i];
			}
		}
		return value;
	}

	/**
	 * Nudge replace values to prevent sequential replacement from chaining.
	 * If replace[i] == find[j] for j > i, the engine applies both replacements to the same face.
	 * Identity replacements (find[j] == replace[j]) are skipped since they cause no visual change.
	 * <p>
	 * When a chain is detected, the nearest non-chaining color is found by searching outward
	 * from the original value: luminance ±1, ±2, ..., then saturation ±1, ±2, ..., then hue.
	 */
	public static void breakColorChains(@Nonnull short[] find, @Nonnull short[] replace)
	{
		assert find.length == replace.length;
		for (int i = 0; i < find.length; i++)
		{
			if (chainsForward(find, replace, i, replace[i]))
			{
				replace[i] = findNearestNonChaining(find, replace, i);
			}
		}
	}

	private static final int[] NUDGE_DIRECTIONS = {1, -1};
	private static final int HUE_SCALE = (MAX_LUM + 1) / (MAX_HUE + 1);
	private static final int SAT_SCALE = (MAX_LUM + 1) / (MAX_SAT + 1);

	private static short findNearestNonChaining(short[] find, short[] replace, int i)
	{
		final var original = replace[i];
		final var h = unpackHue(original);
		final var s = unpackSaturation(original);
		final var l = unpackLuminance(original);

		for (int d = 1; d <= MAX_LUM; d++)
		{
			for (int dir : NUDGE_DIRECTIONS)
			{
				int newL = l + d * dir;
				if (newL >= 0 && newL <= MAX_LUM)
				{
					short candidate = packHSL(h, s, newL);
					if (!chainsForward(find, replace, i, candidate))
					{
						return candidate;
					}
				}
			}

			if (d % HUE_SCALE == 0)
			{
				int hueOffset = d / HUE_SCALE;
				for (int dir : NUDGE_DIRECTIONS)
				{
					int newH = Math.floorMod(h + hueOffset * dir, MAX_HUE + 1);
					short candidate = packHSL(newH, s, l);
					if (!chainsForward(find, replace, i, candidate))
					{
						return candidate;
					}
				}
			}

			if (d % SAT_SCALE == 0)
			{
				int satOffset = d / SAT_SCALE;
				for (int dir : NUDGE_DIRECTIONS)
				{
					int newS = s + satOffset * dir;
					if (newS >= 0 && newS <= MAX_SAT)
					{
						short candidate = packHSL(h, newS, l);
						if (!chainsForward(find, replace, i, candidate))
						{
							return candidate;
						}
					}
				}
			}
		}

		return original;
	}

	private static boolean chainsForward(short[] find, short[] replace, int i, short value)
	{
		for (int j = i + 1; j < find.length; j++)
		{
			if (find[j] != replace[j] && value == find[j])
			{
				return true;
			}
		}
		return false;
	}
}
