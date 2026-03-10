package io.huze.glamourer.color;

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

public class ColorReplacement implements Serializable
{
	@Getter
	private final short original;
	@Getter
	@Setter
	private short replacement;
	/// The underlying model face color, used to disambiguate when duplicate original colors exist on the item.
	@Getter
	@Setter
	private Short model;

	public ColorReplacement(short original, short replacement)
	{
		this.original = original;
		this.replacement = replacement;
	}

	public boolean hasChanged()
	{
		return original != replacement;
	}

	@Override
	public String toString()
	{
		return String.format("<%d,%d>", original, replacement);
	}
}
