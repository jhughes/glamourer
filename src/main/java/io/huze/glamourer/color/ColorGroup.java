package io.huze.glamourer.color;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.runelite.api.JagexColor;

public class ColorGroup
{
	private static final double GROUP_DISTANCE_THRESHOLD = 0.10;
	private static final short SKIN_COLOR = 4550;
	private static final short HAIR_COLOR = 6798;
	private static final short EYE_COLOR = 0;
	// Anchor is the first color added (used for offset calculations)
	private final int anchorHue;
	private final int anchorSat;
	private final int anchorLum;

	// Indices into the glamour's color array
	@Getter
	private final List<Integer> colorIndices = new ArrayList<>();

	// Each color's [hDelta, sDelta, lDelta] offset from anchor (based on original colors)
	@Getter
	private final List<int[]> offsets = new ArrayList<>();

	// All original HSL values in this group (for checking if new colors can join)
	private final List<short[]> originalHslValues = new ArrayList<>();

	private ColorGroup(short anchorHsl)
	{
		this.anchorHue = JagexColor.unpackHue(anchorHsl);
		this.anchorSat = JagexColor.unpackSaturation(anchorHsl);
		this.anchorLum = JagexColor.unpackLuminance(anchorHsl);
	}

	private void addColor(int index, short originalHsl)
	{
		int h = JagexColor.unpackHue(originalHsl);
		int s = JagexColor.unpackSaturation(originalHsl);
		int l = JagexColor.unpackLuminance(originalHsl);

		colorIndices.add(index);
		offsets.add(new int[]{h - anchorHue, s - anchorSat, l - anchorLum});
		originalHslValues.add(new short[]{(short) h, (short) s, (short) l});
	}

	private static boolean areGroupable(int h1, int s1, int l1, int h2, int s2, int l2)
	{
		return Colors.calculateColorDistance(h1, s1, l1, h2, s2, l2) <= GROUP_DISTANCE_THRESHOLD;
	}

	private boolean canGroup(short hsl)
	{
		if (hsl == SKIN_COLOR || containsSkinColor())
		{
			return false;
		}

		int h = JagexColor.unpackHue(hsl);
		int s = JagexColor.unpackSaturation(hsl);
		int l = JagexColor.unpackLuminance(hsl);

		for (short[] existing : originalHslValues)
		{
			if (areGroupable(h, s, l, existing[0], existing[1], existing[2]))
			{
				return true;
			}
		}
		return false;
	}

	private boolean containsSkinColor()
	{
		for (short[] hsl : originalHslValues)
		{
			if (JagexColor.packHSL(hsl[0], hsl[1], hsl[2]) == SKIN_COLOR)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the replacement colors still maintain the expected relative offsets.
	 * If any color has been individually edited to break the pattern, returns false.
	 *
	 * @param pairs The color pairs to check
	 * @return true if replacements are coherent (maintain same relative offsets), false otherwise
	 */
	public boolean areReplacementsCoherent(List<ColorReplacement> pairs)
	{
		if (colorIndices.size() <= 1)
		{
			return true;
		}

		// Get the first replacement color as reference
		ColorReplacement firstPair = pairs.get(colorIndices.get(0));
		int refH = JagexColor.unpackHue(firstPair.getReplacement());
		int refS = JagexColor.unpackSaturation(firstPair.getReplacement());
		int refL = JagexColor.unpackLuminance(firstPair.getReplacement());

		// Check if all other colors maintain the same relative offsets
		for (int i = 1; i < colorIndices.size(); i++)
		{
			int colorIdx = colorIndices.get(i);
			int[] expectedOffset = offsets.get(i);

			ColorReplacement pair = pairs.get(colorIdx);
			int actualH = JagexColor.unpackHue(pair.getReplacement());
			int actualS = JagexColor.unpackSaturation(pair.getReplacement());
			int actualL = JagexColor.unpackLuminance(pair.getReplacement());

			// Calculate expected values based on first replacement + original offset
			// Hue wraps around (circular), sat/lum clamp
			int expectedH = ((refH + expectedOffset[0]) % 64 + 64) % 64;
			int expectedS = clamp(refS + expectedOffset[1], 0, 7);
			int expectedL = clamp(refL + expectedOffset[2], 0, 127);

			// Allow small tolerance for clamping/rounding effects
			// For hue, also check wrapped distance (e.g., 0 and 63 are close)
			int hueDiff = Math.abs(actualH - expectedH);
			int wrappedHueDiff = 64 - hueDiff;
			if (Math.min(hueDiff, wrappedHueDiff) > 1 ||
				Math.abs(actualS - expectedS) > 0 ||
				Math.abs(actualL - expectedL) > 1)
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * Groups colors by original HSL proximity using a connected-components approach.
	 * A color joins a group if it's similar to ANY color already in that group.
	 * Groups are then merged if any of their colors are within threshold of each other.
	 * Colors within each group are sorted by luminance (brightest first).
	 *
	 * @param pairs List of ColorReplacements from the glamour
	 * @return List of ColorGroups, each containing indices and offsets for grouped colors
	 */
	public static List<ColorGroup> groupColors(List<ColorReplacement> pairs)
	{
		List<ColorGroup> groups = assignToInitialGroups(pairs);
		mergeConnectedGroups(groups);
		finalizeGroups(groups);
		return groups;
	}

	private static List<ColorGroup> assignToInitialGroups(List<ColorReplacement> pairs)
	{
		List<ColorGroup> groups = new ArrayList<>();
		int index = 0;

		for (ColorReplacement pair : pairs)
		{
			short originalHsl = pair.getOriginal();
			ColorGroup matchingGroup = findMatchingGroup(groups, originalHsl);

			if (matchingGroup != null)
			{
				matchingGroup.addColor(index, originalHsl);
			}
			else
			{
				ColorGroup newGroup = new ColorGroup(originalHsl);
				newGroup.addColor(index, originalHsl);
				groups.add(newGroup);
			}
			index++;
		}
		return groups;
	}

	private static ColorGroup findMatchingGroup(List<ColorGroup> groups, short hsl)
	{
		for (ColorGroup group : groups)
		{
			if (group.canGroup(hsl))
			{
				return group;
			}
		}
		return null;
	}

	private static void mergeConnectedGroups(List<ColorGroup> groups)
	{
		boolean merged;
		do
		{
			merged = tryMergeOnePair(groups);
		} while (merged);
	}

	private static boolean tryMergeOnePair(List<ColorGroup> groups)
	{
		for (int i = 0; i < groups.size(); i++)
		{
			for (int j = i + 1; j < groups.size(); j++)
			{
				if (groups.get(i).shouldMergeWith(groups.get(j)))
				{
					groups.get(i).mergeFrom(groups.get(j));
					groups.remove(j);
					return true;
				}
			}
		}
		return false;
	}

	private static void finalizeGroups(List<ColorGroup> groups)
	{
		for (ColorGroup group : groups)
		{
			group.sortByLuminance();
		}
		groups.sort((a, b) -> {
			boolean aSkin = a.containsSkinColor();
			boolean bSkin = b.containsSkinColor();
			if (aSkin != bSkin)
			{
				return aSkin ? 1 : -1;
			}
			return Short.compare(b.originalHslValues.get(0)[2], a.originalHslValues.get(0)[2]);
		});
	}

	/**
	 * Checks if this group should be merged with another group.
	 * Returns true if any color in this group is within threshold of any color in the other group.
	 */
	private boolean shouldMergeWith(ColorGroup other)
	{
		if (containsSkinColor() || other.containsSkinColor())
		{
			return false;
		}
		for (short[] myHsl : originalHslValues)
		{
			for (short[] otherHsl : other.originalHslValues)
			{
				if (areGroupable(myHsl[0], myHsl[1], myHsl[2],
					otherHsl[0], otherHsl[1], otherHsl[2]))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Merges all colors from another group into this group.
	 */
	private void mergeFrom(ColorGroup other)
	{
		colorIndices.addAll(other.colorIndices);
		offsets.addAll(other.offsets);
		originalHslValues.addAll(other.originalHslValues);
	}

	/**
	 * Sorts the colors in this group by luminance (brightest first).
	 * Recalculates offsets relative to the new first color.
	 */
	private void sortByLuminance()
	{
		if (colorIndices.size() <= 1)
		{
			return;
		}

		Integer[] sortOrder = createLuminanceSortOrder();
		reorderBySortOrder(sortOrder);
		recalculateOffsets();
	}

	private Integer[] createLuminanceSortOrder()
	{
		Integer[] sortOrder = new Integer[colorIndices.size()];
		for (int i = 0; i < sortOrder.length; i++)
		{
			sortOrder[i] = i;
		}

		java.util.Arrays.sort(sortOrder, (a, b) -> {
			short[] hslA = originalHslValues.get(a);
			short[] hslB = originalHslValues.get(b);
			int cmp = Short.compare(hslB[2], hslA[2]); // Luminance descending
			if (cmp != 0) return cmp;
			cmp = Short.compare(hslA[0], hslB[0]); // Hue ascending
			if (cmp != 0) return cmp;
			return Short.compare(hslA[1], hslB[1]); // Saturation ascending
		});

		return sortOrder;
	}

	private void reorderBySortOrder(Integer[] sortOrder)
	{
		List<Integer> newColorIndices = new ArrayList<>();
		List<int[]> newOffsets = new ArrayList<>();
		List<short[]> newOriginalHslValues = new ArrayList<>();

		for (int i : sortOrder)
		{
			newColorIndices.add(colorIndices.get(i));
			newOffsets.add(offsets.get(i));
			newOriginalHslValues.add(originalHslValues.get(i));
		}

		colorIndices.clear();
		colorIndices.addAll(newColorIndices);
		offsets.clear();
		offsets.addAll(newOffsets);
		originalHslValues.clear();
		originalHslValues.addAll(newOriginalHslValues);
	}

	private void recalculateOffsets()
	{
		short[] newAnchor = originalHslValues.get(0);
		for (int i = 0; i < offsets.size(); i++)
		{
			short[] hsl = originalHslValues.get(i);
			offsets.set(i, new int[]{hsl[0] - newAnchor[0], hsl[1] - newAnchor[1], hsl[2] - newAnchor[2]});
		}
	}

	/**
	 * Calculates the new HSL value for a color in this group when the anchor color changes.
	 *
	 * @param newAnchorHsl The new HSL value for the anchor (from user selection)
	 * @param offsetIndex  Index into the offsets list for the specific color
	 * @return Adjusted HSL value for the color (hue wraps, sat/lum clamp)
	 */
	public short calculateNewColor(short newAnchorHsl, int offsetIndex)
	{
		int newH = JagexColor.unpackHue(newAnchorHsl);
		int newS = JagexColor.unpackSaturation(newAnchorHsl);
		int newL = JagexColor.unpackLuminance(newAnchorHsl);

		int[] offset = offsets.get(offsetIndex);
		int finalH = ((newH + offset[0]) % 64 + 64) % 64;
		int finalS = clamp(newS + offset[1], 0, 7);
		int finalL = clamp(newL + offset[2], 0, 127);

		return JagexColor.packHSL(finalH, finalS, finalL);
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
