package io.huze.glamourer.glam;

import net.runelite.api.JagexColor;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
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

		assertArrayEquals(new short[]{21, 30}, replace);
	}

	@Test
	public void multipleChains_allNudged()
	{
		short[] find = {hsl(0), hsl(10), hsl(20)};
		short[] replace = {hsl(10), hsl(20), hsl(30)};

		GlamState.breakColorChains(find, replace);

		assertArrayEquals(new short[]{11, 21, 30}, replace);
	}

	@Test
	public void nudgeCascade_rechecksCatchesNewCollision()
	{
		short[] find = {hsl(0), hsl(10), hsl(11)};
		short[] replace = {hsl(10), hsl(11), hsl(13)};

		GlamState.breakColorChains(find, replace);

		assertArrayEquals(new short[]{12, 12, 13}, replace);
	}

	@Test
	public void backwardChain_noChange()
	{
		short[] find = {hsl(0), hsl(10)};
		short[] replace = {hsl(20), hsl(0)};

		GlamState.breakColorChains(find, replace);

		assertArrayEquals(new short[]{20, 0}, replace);
	}

	@Test
	public void luminanceWraps_stopsAtOriginal()
	{
		// All 128 luminance values are present in find[1..],
		// so nudging can never escape — should stop without infinite loop
		short[] find = new short[128];
		short[] replace = new short[128];
		for (int l = 0; l < 128; l++)
		{
			find[l] = hsl(l);
			replace[l] = hsl((l + 1) % 128);
		}

		GlamState.breakColorChains(find, replace);

		assertEquals(hsl(0), replace[0]);
		assertEquals(hsl(0), replace[1]);
		assertEquals(hsl(0), replace[127]);
	}
}
