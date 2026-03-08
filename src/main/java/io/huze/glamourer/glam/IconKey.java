package io.huze.glamourer.glam;

import io.huze.glamourer.Extensions;
import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.texture.TextureReplacement;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.experimental.ExtensionMethod;

@Value
@ExtensionMethod({Extensions.class})
public class IconKey
{
	int itemId;
	String colorReplace;
	String textureReplace;

	public static IconKey of(Glamour glamour)
	{
		var colorReplace = glamour.getColorReplacements().stream()
			.map(ColorReplacement::getReplacement)
			.collect(Collectors.toList());
		var textureReplace = glamour.getTextureReplacements().stream()
			.map(TextureReplacement::getReplacement)
			.collect(Collectors.toList());
		return new IconKey(
			glamour.getPrimaryItemId(),
			colorReplace.toHex(),
			textureReplace.toHex());
	}

	@Override
	public String toString()
	{
		return String.format("%d:%s:%s",
			itemId,
			colorReplace,
			textureReplace);
	}
}
