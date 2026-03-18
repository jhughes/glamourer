package io.huze.glamourer.color;

import lombok.Value;

@Value
public class ColorGroupSettings
{
	double maxHueDist; // 0.0-1.0 (0 = identical hue only, 1 = any hue)
	double maxSatDist; // 0.0-1.0
	double maxLumDist; // 0.0-1.0
	boolean separateGrayscale;

	public static final ColorGroupSettings DEFAULT = new ColorGroupSettings(0.04, 0.4, 0.08, true);
}
