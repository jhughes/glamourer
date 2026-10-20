package io.huze.glamourer.color;

public final class Shade
{
	public static final int FULL = 128;
	public static final int AMBIENT = 64;
	/// The client clamps every face to this range.
	public static final int DARKEST = 2;
	public static final int BRIGHTEST = 126;
	private static final int LUMINANCE_MASK = 127;

	private Shade()
	{
	}

	public static int fade(int litShade, double intensity)
	{
		return (int) Math.round(FULL - (FULL - litShade) * intensity);
	}

	public static int scale(int value, int shade)
	{
		return value * shade / FULL;
	}

	public static short shadeHsl(short hsl, int shade)
	{
		int luminance = Math.max(DARKEST, Math.min(BRIGHTEST, scale(hsl & LUMINANCE_MASK, shade)));
		return (short) ((hsl & ~LUMINANCE_MASK) | luminance);
	}
}
