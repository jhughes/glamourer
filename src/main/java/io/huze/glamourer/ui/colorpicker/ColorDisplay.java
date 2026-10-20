package io.huze.glamourer.ui.colorpicker;

import io.huze.glamourer.color.Brightness;
import io.huze.glamourer.color.Colors;
import io.huze.glamourer.color.Shade;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.Getter;

public final class ColorDisplay
{
	private static final int GRADIENT_STEPS = 21;
	private static final double FULL_LIGHT = 1;

	@Getter
	private static double lightIntensity = FULL_LIGHT;
	private static final List<Runnable> lightListeners = new ArrayList<>();

	private ColorDisplay()
	{
	}

	/// Swing thread only.
	public static void setLightIntensity(double intensity)
	{
		double clamped = Math.max(0, Math.min(FULL_LIGHT, intensity));
		if (clamped == lightIntensity)
		{
			return;
		}
		lightIntensity = clamped;
		for (var listener : new ArrayList<>(lightListeners))
		{
			listener.run();
		}
	}

	public static void addLightIntensityListener(@Nonnull Runnable listener)
	{
		lightListeners.add(listener);
	}

	public static void removeLightIntensityListener(@Nonnull Runnable listener)
	{
		lightListeners.remove(listener);
	}

	@Nonnull
	public static Color toColor(short hsl)
	{
		return brightened(Colors.hslToShadedColor(hsl, getMedianShade()));
	}

	@Nonnull
	public static Color toColor(int hue, int sat, int lum)
	{
		return brightened(Colors.hslToShadedColor(hue, sat, lum, getMedianShade()));
	}

	@Nonnull
	public static Color[] toColors(short hsl)
	{
		int darkest = getDarkestShade();
		int brightest = getBrightestShade();
		if (darkest == brightest)
		{
			return new Color[]{toColor(hsl)};
		}

		var colors = new Color[GRADIENT_STEPS];
		for (int i = 0; i < colors.length; i++)
		{
			int shade = darkest + (i * (brightest - darkest)) / (colors.length - 1);
			colors[i] = brightened(Colors.hslToShadedColor(hsl, shade));
		}
		return colors;
	}

	private static int getDarkestShade()
	{
		return Shade.fade(Shade.AMBIENT, lightIntensity);
	}

	private static int getBrightestShade()
	{
		return Shade.fade(Shade.BRIGHTEST, lightIntensity);
	}

	private static int getMedianShade()
	{
		return (getDarkestShade() + getBrightestShade()) / 2;
	}

	@Nonnull
	private static Color brightened(@Nonnull Color color)
	{
		return Brightness.getCurrent().applyBrightness(color);
	}
}
