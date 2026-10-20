package io.huze.glamourer.model;

import io.huze.glamourer.color.Shade;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Value;
import net.runelite.api.Model;
import net.runelite.api.ModelData;

@Value
public class Lighting
{
	private static final int ITEM_CONTRAST = 768;
	private static final int ITEM_LIGHT_X = -50;
	private static final int ITEM_LIGHT_Y = -10;
	private static final int ITEM_LIGHT_Z = -50;

	private static final int ACTOR_CONTRAST = 850;
	private static final int ACTOR_LIGHT_X = -30;
	private static final int ACTOR_LIGHT_Y = -50;
	private static final int ACTOR_LIGHT_Z = -30;

	/// Large enough to round off the directional term.
	private static final int FLAT_CONTRAST = 1_000_000;

	int ambient;
	int contrast;
	int lightX;
	int lightY;
	int lightZ;

	@Nonnull
	public static Lighting forItem(int ambientOffset, int contrastOffset)
	{
		return new Lighting(Shade.AMBIENT + ambientOffset, ITEM_CONTRAST + contrastOffset,
			ITEM_LIGHT_X, ITEM_LIGHT_Y, ITEM_LIGHT_Z);
	}

	@Nonnull
	public static Lighting forActor(int ambientOffset, int contrastOffset)
	{
		return new Lighting(Shade.AMBIENT + ambientOffset, ACTOR_CONTRAST + contrastOffset,
			ACTOR_LIGHT_X, ACTOR_LIGHT_Y, ACTOR_LIGHT_Z);
	}

	@Nonnull
	public Lighting withIntensity(double intensity)
	{
		if (intensity >= 1)
		{
			return this;
		}
		if (intensity <= 0)
		{
			return new Lighting(Shade.FULL, FLAT_CONTRAST, lightX, lightY, lightZ);
		}
		return new Lighting(
			Shade.fade(ambient, intensity),
			(int) Math.min(FLAT_CONTRAST, Math.round(contrast / intensity)),
			lightX, lightY, lightZ);
	}

	/// Null when the client cannot light the model.
	@Nullable
	public Model light(@Nonnull ModelData modelData)
	{
		return modelData.light(ambient, contrast, lightX, lightY, lightZ);
	}
}
