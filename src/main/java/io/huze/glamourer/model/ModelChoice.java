package io.huze.glamourer.model;

import javax.annotation.Nonnull;
import lombok.Value;

@Value
public class ModelChoice
{
	public enum Kind
	{
		ITEM,
		WORN,
		PET
	}

	private static final int[] NO_MODELS = {};
	public static final ModelChoice ITEM = new ModelChoice(Kind.ITEM, NO_MODELS, "Item", 0);
	public static final ModelChoice PET = new ModelChoice(Kind.PET, NO_MODELS, "Pet", 0);

	Kind kind;
	/// Empty for the icon and the pet.
	int[] wornModelIds;
	String label;
	/// How many worn models share this choice's name.
	int alternatives;

	@Nonnull
	static ModelChoice worn(@Nonnull String name, @Nonnull int[] modelIds, int index, int alternatives)
	{
		return new ModelChoice(Kind.WORN, modelIds, alternatives > 1 ? name + " " + index : name, alternatives);
	}

	@Nonnull
	public String getTooltip()
	{
		return label + " model";
	}

	@Override
	public String toString()
	{
		return label;
	}
}
