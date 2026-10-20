package io.huze.glamourer.render;

import javax.annotation.Nonnull;

abstract class ModelCamera
{
	@Nonnull
	private final ViewTransform view;
	/// Image pixels per camera space unit at unit depth.
	private final double focalLength;
	private final double principalX;
	private final double principalY;

	ModelCamera(@Nonnull ViewTransform view, double focalLength, double principalX, double principalY)
	{
		this.view = view;
		this.focalLength = focalLength;
		this.principalX = principalX;
		this.principalY = principalY;
	}

	final double getFocalLength()
	{
		return focalLength;
	}

	final double toCameraX(double x, double y, double z)
	{
		return view.toCameraX(x, y, z);
	}

	final double toCameraY(double x, double y, double z)
	{
		return view.toCameraY(x, y, z);
	}

	final double toDepth(double x, double y, double z)
	{
		return view.toDepth(x, y, z);
	}

	final double toImageX(double cameraX, double depth)
	{
		return cameraX * focalLength / depth + principalX;
	}

	final double toImageY(double cameraY, double depth)
	{
		return cameraY * focalLength / depth + principalY;
	}
}
