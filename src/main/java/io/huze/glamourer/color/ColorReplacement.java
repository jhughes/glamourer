package io.huze.glamourer.color;

import java.awt.Color;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
public class ColorReplacement implements Serializable
{
	@Getter
	private final short original;
	@Getter
	@Setter
	private short replacement;

	public boolean hasChanged()
	{
		return original != replacement;
	}

	public static Color[] getReplacementColors(List<ColorReplacement> replacements)
	{
		return replacements.stream().map(r -> Colors.hslToColor(r.getReplacement())).toArray(Color[]::new);
	}

	public static Color[] getOriginalColors(List<ColorReplacement> replacements)
	{
		return replacements.stream().map(r -> Colors.hslToColor(r.getOriginal())).toArray(Color[]::new);
	}

	@Override
	public String toString()
	{
		return String.format("<%d,%d>", original, replacement);
	}
}
