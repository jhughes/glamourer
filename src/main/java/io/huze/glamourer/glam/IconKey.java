package io.huze.glamourer.glam;

import io.huze.glamourer.Extensions;
import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.texture.TextureReplacement;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.experimental.ExtensionMethod;

@Value
@ExtensionMethod({Extensions.class})
class IconKey
{
	int itemId;
	String colorReplace;
	String textureReplace;

	static IconKey of(int itemId, GlamState state)
	{
		var colorReplace = state.getColorReplacements().stream()
			.map(ColorReplacement::getReplacement)
			.collect(Collectors.toList());
		var textureReplace = state.getTextureReplacements().stream()
			.map(TextureReplacement::getReplacement)
			.collect(Collectors.toList());
		return new IconKey(
			itemId,
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
