package io.huze.glamourer.color;

import java.io.Serializable;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

public class ColorReplacement implements Serializable
{
	@Getter
	private final short original;
	@Getter
	@Setter
	private short replacement;
	/// Only stored when it differs from the original.
	/// TODO swap to storing model as the primary original color.
	@Nullable
	private final Short model;

	public ColorReplacement(short model, short original, short replacement)
	{
		this.original = original;
		this.replacement = replacement;
		this.model = model == original ? null : model;
	}

	public ColorReplacement(short original, short replacement)
	{
		this(original, original, replacement);
	}

	public short getModel()
	{
		return model == null ? original : model;
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
