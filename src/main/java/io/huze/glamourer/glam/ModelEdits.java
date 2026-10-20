package io.huze.glamourer.glam;

import io.huze.glamourer.Extensions;
import javax.annotation.Nonnull;
import lombok.Value;
import lombok.With;
import lombok.experimental.ExtensionMethod;
import net.runelite.api.ItemComposition;
import net.runelite.api.ModelData;

/// Edits to draw a model with. Color chains should be broken.
@Value
@ExtensionMethod({Extensions.class})
public class ModelEdits
{
	private static final short[] NONE = {};
	public static final ModelEdits EMPTY = new ModelEdits(NONE, NONE, NONE, NONE);

	// These arrays must be treated as const.
	@With
	short[] colorFind;
	short[] colorReplace;
	short[] textureFind;
	short[] textureReplace;

	/// Client thread only.
	public static ModelEdits of(ItemComposition itemComp)
	{
		return new ModelEdits(
			itemComp.getColorToReplace().emptyIfNull(), itemComp.getColorToReplaceWith().emptyIfNull(),
			itemComp.getTextureToReplace().emptyIfNull(), itemComp.getTextureToReplaceWith().emptyIfNull());
	}

	/// Clones and applies edits.
	@Nonnull
	public ModelData apply(@Nonnull ModelData modelData)
	{
		if (colorFind.length > 0)
		{
			modelData = modelData.cloneColors();
		}
		// cloneTextures throws on a model with no textures.
		if (textureFind.length > 0 && modelData.getFaceTextures() != null)
		{
			modelData = modelData.cloneTextures();
		}
		applyInPlace(modelData);
		return modelData;
	}

	/// Applies in place for already cloned models.
	public void applyInPlace(@Nonnull ModelData modelData)
	{
		for (int i = 0; i < colorFind.length; i++)
		{
			modelData.recolor(colorFind[i], colorReplace[i]);
		}
		if (modelData.getFaceTextures() != null)
		{
			for (int i = 0; i < textureFind.length; i++)
			{
				modelData.retexture(textureFind[i], textureReplace[i]);
			}
		}
	}

	@Override
	public String toString()
	{
		return colorReplace.toHex() + ":" + textureReplace.toHex();
	}
}
