package io.huze.glamourer.glam;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum IconScale
{
	PERCENT_75(75),
	PERCENT_100(100),
	PERCENT_150(150),
	PERCENT_200(200);

	@Getter
	private final int percent;

	public float getScale()
	{
		return percent / 100f;
	}

	@Override
	public String toString()
	{
		return percent + "%";
	}
}
