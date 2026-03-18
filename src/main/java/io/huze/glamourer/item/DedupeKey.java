package io.huze.glamourer.item;

import io.huze.glamourer.Extensions;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.ExtensionMethod;
import net.runelite.api.ItemComposition;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ExtensionMethod({Extensions.class})
public class DedupeKey implements Comparable<DedupeKey>
{
	String strippedName;
	int modelId;
	short[] colorReplace;
	short[] textureReplace;

	public static String fromComponents(String membersName, int modelId, short[] colorReplace, short[] textureReplace)
	{
		return new DedupeKey(
			stripName(membersName),
			similarModelId(modelId),
			colorReplace,
			textureReplace).toString();
	}

	public static String of(ItemComposition itemComposition)
	{
		return fromComponents(
			itemComposition.getMembersName(),
			similarModelId(itemComposition.getInventoryModel()),
			itemComposition.getColorToReplaceWith(),
			itemComposition.getTextureToReplaceWith());
	}

	@Override
	public int compareTo(DedupeKey o)
	{
		return Integer.compare(modelId, o.modelId);
	}

	@Override
	public String toString()
	{
		return String.format("%s:%d:%s:%s",
			strippedName,
			modelId,
			colorReplace.toHex(),
			textureReplace.toHex());
	}

	public static String removeOverrides(String dedupeKey)
	{
		var split = dedupeKey.split(":");
		if (split.length != 4)
		{
			return dedupeKey;
		}
		return split[0] + ":" + split[1] + "::";
	}

	private static final Pattern PAREN_REPLACE = Pattern.compile("\\(.*\\)");

	private static String stripName(String name)
	{
		String noParens = PAREN_REPLACE.matcher(name).replaceAll("");
		return noParens.replaceAll("[^A-Za-z]+", "");
	}

	private static int similarModelId(int modelId)
	{
		switch (modelId)
		{
			// Normal
			case 2621:
			case 2384:
			case 2697:
				return 2789;
			// Divine
			case 37966:
			case 37975:
			case 37944:
				return 37951;
			// CoX
			case 32768:
			case 32766:
			case 32772:
				return 32527;
			// Barb Mix
			case 26825:
				return 26826;
		}
		return modelId;
	}
}
