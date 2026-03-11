package io.huze.glamourer.color;

import java.awt.Color;
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
	// Model lighting clamps face brightness to this range
	private static final int MIN_BRIGHTNESS = 2;
	private static final int MAX_BRIGHTNESS = 126;

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

	public static void hslToShadedColors(short hsl, Color[] out)
	{
		hslToShadedColors(unpackHue(hsl), unpackSaturation(hsl), unpackLuminance(hsl), out);
	}

	public static void hslToShadedColors(int hue, int sat, int lum, Color[] out)
	{
		int steps = out.length;
		if (steps == 1)
		{
			out[0] = hslToShadedColor(hue, sat, lum);
			return;
		}
		for (int i = 0; i < steps; i++)
		{
			int brightness = MIN_BRIGHTNESS + (i * (MAX_BRIGHTNESS - MIN_BRIGHTNESS)) / (steps - 1);
			int effectiveLum = lum * brightness / 128;
			out[i] = hslToColor(hue, sat, effectiveLum);
		}
	}

	public static Color hslToShadedColor(short hsl)
	{
		return hslToShadedColor(unpackHue(hsl), unpackSaturation(hsl), unpackLuminance(hsl));
	}

	public static Color hslToShadedColor(int hue, int sat, int lum)
	{
		int medianBrightness = (MIN_BRIGHTNESS + MAX_BRIGHTNESS) / 2;
		return hslToColor(hue, sat, lum * medianBrightness / 128);
	}

	/// Generate a highlighted color for the provided HSL color.
	/// Highlighted colors retain the hue of the input color for better lerping.
	public static short highlight(short hsl)
	{
		return packHSL(unpackHue(hsl), 0, MAX_LUM);
	}

	/// Generate a darkened color for the provided HSL color.
	/// Darkened colors retain the hue of the input color for better lerping.
	public static short darken(short hsl)
	{
		return packHSL(unpackHue(hsl), 0, 0);
	}

	/**
	 * Calculates the perceptual color distance between two HSL colors.
	 * <p>
	 * Uses weighted sum where luminance differences are most important,
	 * saturation differences are moderate, and hue differences are minimal.
	 * Hue importance is further modulated by saturation - at low saturation (grays),
	 * hue becomes perceptually irrelevant.
	 */
	public static double calculateColorDistance(int h1, int s1, int l1, int h2, int s2, int l2)
	{
		// Normalize to 0-1 range
		double hDiff = Math.abs(h1 - h2) / MAX_HUE_D;
		double sDiff = Math.abs(s1 - s2) / MAX_SAT_D;
		double lDiff = Math.abs(l1 - l2) / MAX_LUM_D;
		// Hue is circular - use minimum distance (0 and 63 are adjacent)
		hDiff = Math.min(hDiff, 1.0 - hDiff);

		double hueWeight = 1.2;
		double satWeight = 0.35;
		double lumWeight = 0.8;

		return hueWeight * hDiff + satWeight * sDiff + lumWeight * lDiff;
	}

	public static short lerpHsl(short start, short end, float t)
	{
		int hue = lerp(unpackHue(start), unpackHue(end), t);
		int sat = lerp(unpackSaturation(start), unpackSaturation(end), t);
		int lum = lerp(unpackLuminance(start), unpackLuminance(end), t);
		return packHSL(hue, sat, lum);
	}

	static int lerp(int start, int end, float t)
	{
		return start + Math.round((end - start) * t);
	}
}
