package io.huze.glamourer.render;

import javax.annotation.Nonnull;

final class OrbitCamera extends ModelCamera
{
	/// Per half viewport
	private static final double FOCAL_LENGTH = 512.0 / 18.0;
	private static final double FIT = 0.9;

	@Nonnull
	private final ViewTransform poseRotation;
	@Nonnull
	private final ViewTransform orbit;
	private final double distance;

	OrbitCamera(@Nonnull ModelBounds bounds, @Nonnull ModelPose pose, @Nonnull OrbitView view,
				int imageWidth, int imageHeight)
	{
		super(getPosed(bounds, pose).then(getOrbit(bounds, view)),
			Math.min(imageWidth, imageHeight) / 2.0 * FOCAL_LENGTH,
			imageWidth / 2.0 + view.getPanX(),
			imageHeight / 2.0 + view.getPanY());
		this.poseRotation = getPoseRotation(pose);
		this.orbit = getOrbit(bounds, view);
		this.distance = getDistance(bounds, view);
	}

	@Nonnull
	double[] toPosedOffset(double x, double y, double z)
	{
		return new double[]{
			poseRotation.toCameraX(x, y, z),
			poseRotation.toCameraY(x, y, z),
			poseRotation.toDepth(x, y, z)};
	}

	@Nonnull
	double[] projectModelOffset(double x, double y, double z)
	{
		double[] posed = toPosedOffset(x, y, z);
		double depth = orbit.toDepth(posed[0], posed[1], posed[2]);
		return new double[]{
			toImageX(orbit.toCameraX(posed[0], posed[1], posed[2]), depth),
			toImageY(orbit.toCameraY(posed[0], posed[1], posed[2]), depth),
			depth};
	}

	double getModelUnitsPerPixelAtCentre()
	{
		return distance / getFocalLength();
	}

	@Nonnull
	private static ViewTransform getPosed(@Nonnull ModelBounds bounds, @Nonnull ModelPose pose)
	{
		return ViewTransform.translation(-bounds.getCenterX(), -bounds.getCenterY(), -bounds.getCenterZ())
			.then(getPoseRotation(pose));
	}

	@Nonnull
	private static ViewTransform getPoseRotation(@Nonnull ModelPose pose)
	{
		return ViewTransform.roll(Math.sin(pose.getRoll()), Math.cos(pose.getRoll()))
			.then(ViewTransform.turn(Math.sin(pose.getTurn()), Math.cos(pose.getTurn())))
			.then(ViewTransform.tilt(Math.sin(pose.getTilt()), Math.cos(pose.getTilt())));
	}

	@Nonnull
	private static ViewTransform getOrbit(@Nonnull ModelBounds bounds, @Nonnull OrbitView view)
	{
		return view.orientation
			.then(ViewTransform.turn(Math.sin(view.getTurn()), Math.cos(view.getTurn())))
			.then(ViewTransform.tilt(Math.sin(view.getTilt()), Math.cos(view.getTilt())))
			.then(ViewTransform.translation(0, 0, getDistance(bounds, view)));
	}

	private static double getDistance(@Nonnull ModelBounds bounds, @Nonnull OrbitView view)
	{
		return bounds.getRadius() * FOCAL_LENGTH / FIT * view.getZoom();
	}
}
