package io.huze.glamourer.render;

import lombok.Value;

@Value
public class ModelPose
{
	public static final ModelPose UPRIGHT = new ModelPose(0, 0, 0);

	double tilt;
	double turn;
	double roll;
}
