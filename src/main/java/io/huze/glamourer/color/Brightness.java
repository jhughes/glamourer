package io.huze.glamourer.color;

import java.awt.Color;
import javax.annotation.Nonnull;

/// Jagex HSL to RGB, adjusted by the client's brightness setting.
public final class Brightness
{
	/// The client's brightness values run from 0.9 (dimmest) to 0.6 (brightest).
	public static final double DEFAULT = 0.8;

	private static final int CHANNEL_RANGE = 256;

	/// Rebuilt on the client thread when the brightness changes. The UI reads the last one built.
	private static volatile Brightness current = new Brightness(DEFAULT);

	private final double brightness;
	private final int[] gammaTable;

	@Nonnull
	public static Brightness getCurrent()
	{
		return current;
	}

	/// Client thread only.
	@Nonnull
	public static Brightness of(double brightness)
	{
		var loaded = current;
		if (!loaded.isForBrightness(brightness))
		{
			loaded = new Brightness(brightness);
			current = loaded;
		}
		return loaded;
	}

	Brightness(double brightness)
	{
		this.brightness = brightness;
		this.gammaTable = new int[CHANNEL_RANGE];
		for (int channel = 0; channel < CHANNEL_RANGE; channel++)
		{
			gammaTable[channel] = (int) (Math.pow((double) channel / CHANNEL_RANGE, brightness) * CHANNEL_RANGE);
		}
	}

	boolean isForBrightness(double brightness)
	{
		return this.brightness == brightness;
	}

	public int toRgb(short hsl)
	{
		var color = Colors.hslToColor(hsl);
		return (gammaTable[color.getRed()] << 16)
			| (gammaTable[color.getGreen()] << 8)
			| gammaTable[color.getBlue()];
	}

	@Nonnull
	public Color applyBrightness(@Nonnull Color color)
	{
		return new Color(
			gammaTable[color.getRed()], gammaTable[color.getGreen()], gammaTable[color.getBlue()]);
	}
}
