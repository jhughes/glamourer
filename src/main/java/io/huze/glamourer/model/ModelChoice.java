package io.huze.glamourer.model;

import javax.annotation.Nonnull;
import lombok.Value;

@Value
public class ModelChoice
{
	public enum Kind
	{
		ITEM,
		WORN_MALE,
		WORN_FEMALE,
		PET,
		SPOT_ANIM
	}

	public static final ModelChoice ITEM = new ModelChoice(Kind.ITEM, -1, "Item");

	Kind kind;
	/// The item ID, the pet's NPC ID, or the spotanim ID.
	int id;
	String label;

	@Nonnull
	static ModelChoice worn(@Nonnull Kind gender, int itemId, @Nonnull String label)
	{
		return new ModelChoice(gender, itemId, label);
	}

	@Nonnull
	static ModelChoice pet(int npcId)
	{
		return new ModelChoice(Kind.PET, npcId, "Pet");
	}

	@Nonnull
	static ModelChoice spotAnim(int spotAnimId, @Nonnull String label)
	{
		return new ModelChoice(Kind.SPOT_ANIM, spotAnimId, label);
	}

	@Override
	public String toString()
	{
		return label;
	}
}
