package io.huze.glamourer.glam;

import java.util.Collections;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class HighlightMask
{
	@Getter
	Set<Integer> colorIndices;

	public static HighlightMask forColorIndices(Set<Integer> colorIndices)
	{
		return new HighlightMask(Collections.unmodifiableSet(colorIndices));
	}
}
