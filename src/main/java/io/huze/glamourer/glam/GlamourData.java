package io.huze.glamourer.glam;

import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.texture.TextureReplacement;
import java.io.Serializable;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Data;

@Data
public class GlamourData implements Serializable
{
	public static final int SUPPORTED_VERSION = 1;
	private final int version;
	// one of
	@Nullable
	private final String itemKey;
	@Nullable
	private final Integer itemId;

	@Nonnull
	private List<ColorReplacement> colorReplacements;
	@Nullable
	private List<TextureReplacement> textureReplacements;

	public GlamourData(@Nullable String itemKey,
					   int itemId,
					   @Nonnull List<ColorReplacement> colorReplacements,
					   @Nullable List<TextureReplacement> textureReplacements)
	{
		this.version = SUPPORTED_VERSION;
		this.itemKey = itemKey;
		this.itemId = itemKey == null ? itemId : null;
		this.colorReplacements = colorReplacements;
		this.textureReplacements = textureReplacements;
	}

	public GlamourData withNewKey(String newKey)
	{
		return new GlamourData(
			newKey,
			itemId == null ? -1 : itemId,
			colorReplacements,
			textureReplacements);
	}
}
