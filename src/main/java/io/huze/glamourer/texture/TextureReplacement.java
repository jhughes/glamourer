package io.huze.glamourer.texture;

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

public class TextureReplacement implements Serializable
{
	@Getter
	private final short original;
	@Getter
	@Setter
	private short replacement;

	public TextureReplacement(short original, short replacement)
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
