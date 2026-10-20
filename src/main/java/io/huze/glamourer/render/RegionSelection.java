package io.huze.glamourer.render;

import java.util.Collections;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.Value;

@Value
public class RegionSelection
{
	@Nonnull
	Set<Integer> colorIndices;
	@Nonnull
	Set<Integer> textureIndices;

	@Nonnull
	public static RegionSelection ofColors(@Nonnull Set<Integer> colorIndices)
	{
		return new RegionSelection(Set.copyOf(colorIndices), Set.of());
	}

	@Nonnull
	public static RegionSelection ofColor(int colorIndex)
	{
		return new RegionSelection(Set.of(colorIndex), Set.of());
	}

	@Nonnull
	public static RegionSelection ofTexture(int textureIndex)
	{
		return new RegionSelection(Set.of(), Set.of(textureIndex));
	}

	boolean selects(int colorIndex, int textureIndex)
	{
		return colorIndices.contains(colorIndex) || textureIndices.contains(textureIndex);
	}

	public boolean overlaps(@Nonnull RegionSelection other)
	{
		return !Collections.disjoint(colorIndices, other.colorIndices)
			|| !Collections.disjoint(textureIndices, other.textureIndices);
	}
}
