package io.huze.glamourer.item;

import java.util.Collection;
import javax.annotation.Nullable;

public interface DedupeItemComposition
{
	int getId();

	Collection<Integer> getIds();

	String getMembersName();

	int getInventoryModel();

	@Nullable
	short[] getColorToReplace();

	void setColorToReplace(final short[] colorsToReplace);

	@Nullable
	short[] getColorToReplaceWith();

	void setColorToReplaceWith(short[] colorToReplaceWith);

	@Nullable
	short[] getTextureToReplace();

	void setTextureToReplace(short[] textureToFind);

	@Nullable
	short[] getTextureToReplaceWith();

	void setTextureToReplaceWith(short[] textureToReplaceWith);
}
