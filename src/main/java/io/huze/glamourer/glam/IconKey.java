package io.huze.glamourer.glam;

import io.huze.glamourer.Extensions;
import io.huze.glamourer.color.ColorReplacement;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.experimental.ExtensionMethod;

@Value
@ExtensionMethod({Extensions.class})
public class IconKey
{
	int itemId;
	int modelIdOverride;
	String colorReplace;
	String textureReplace;

	public static String of(Glamour glamour)
	{
		var colorReplace = glamour.getColorReplacements().stream()
			.map(ColorReplacement::getReplacement)
			.collect(Collectors.toList());
		return new IconKey(
			glamour.getPrimaryItemId(),
			glamour.getReplacementModelId() != null ? glamour.getReplacementModelId() : -1,
			colorReplace.toHex(),
			"").toString();
	}

	@Override
	public String toString()
	{
		return String.format("%d:%d:%s:%s",
			itemId,
			modelIdOverride,
			colorReplace,
			textureReplace);
	}
}
