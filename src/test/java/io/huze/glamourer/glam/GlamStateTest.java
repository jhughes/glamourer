package io.huze.glamourer.glam;

import net.runelite.api.JagexColor;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

public class GlamStateTest
{
	private static short hsl(int l)
	{
		return JagexColor.packHSL(0, 0, l);
	}

	@Test
	public void identicalFindReplace_noChange()
	{
		short[] find = {hsl(10), hsl(20)};
		short[] replace = {hsl(10), hsl(20)};
		short[] expected = replace.clone();

		GlamState.breakColorChains(find, replace);

		assertArrayEquals(expected, replace);
	}

	@Test
	public void noChain_noChange()
	{
		short[] find = {hsl(10), hsl(20)};
		short[] replace = {hsl(30), hsl(40)};
		short[] expected = replace.clone();

		GlamState.breakColorChains(find, replace);

		assertArrayEquals(expected, replace);
	}

	@Test
	public void simpleChain_isNudged()
	{
		short[] find = {hsl(10), hsl(20)};
		short[] replace = {hsl(20), hsl(30)};

		GlamState.breakColorChains(find, replace);

		assertArrayEquals(new short[]{hsl(21), hsl(30)}, replace);
	}

	@Test
	public void multipleChains_allNudged()
	{
		short[] find = {hsl(0), hsl(10), hsl(20)};
		short[] replace = {hsl(10), hsl(20), hsl(30)};

		GlamState.breakColorChains(find, replace);

		assertArrayEquals(new short[]{hsl(11), hsl(21), hsl(30)}, replace);
	}

	@Test
	public void nudgePrefersCloserValue()
	{
		short[] find = {hsl(0), hsl(10), hsl(11)};
		short[] replace = {hsl(10), hsl(11), hsl(13)};

		GlamState.breakColorChains(find, replace);

		assertArrayEquals(new short[]{hsl(9), hsl(12), hsl(13)}, replace);
	}

	@Test
	public void backwardChain_noChange()
	{
		short[] find = {hsl(0), hsl(10)};
		short[] replace = {hsl(20), hsl(0)};

		GlamState.breakColorChains(find, replace);

		assertArrayEquals(new short[]{hsl(20), hsl(0)}, replace);
	}

	@Test
	public void identityReplacement_noNudge()
	{
		// Gray recolored to white, white not recolored (identity).
		// No chain risk, so no nudge needed.
		short gray = JagexColor.packHSL(0, 0, 60);
		short white = JagexColor.packHSL(0, 0, 120);
		short[] find = {gray, white};
		short[] replace = {white, white};

		GlamState.breakColorChains(find, replace);

		assertArrayEquals(new short[]{white, white}, replace);
	}

	@Test
	public void interleavesHueWithLuminance()
	{
		// Lum ±1 and ±2 all chain. Hue ±1 is tried at d=2 before lum ±3.
		short[] find = {hsl(0), hsl(8), hsl(9), hsl(10), hsl(11), hsl(12)};
		short[] replace = {hsl(10), hsl(20), hsl(20), hsl(20), hsl(20), hsl(20)};

		GlamState.breakColorChains(find, replace);

		assertEquals(JagexColor.packHSL(1, 0, 10), replace[0]);
	}
}
