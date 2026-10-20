package io.huze.glamourer.render;

import io.huze.glamourer.item.ItemRenderSheet;
import javax.annotation.Nonnull;

/// The client's item icon camera (net.runelite.cache.item.ItemSpriteFactory).
final class IconCamera extends ModelCamera
{
	private static final int FOCAL_LENGTH = 512;
	private static final int ORIGIN_X = 16;
	private static final int ORIGIN_Y = 16;

	IconCamera(@Nonnull ModelPose pose, @Nonnull ItemRenderSheet.Row renderParams, int modelHeight, float scale)
	{
		super(getView(pose, renderParams, modelHeight), FOCAL_LENGTH * scale, ORIGIN_X * scale, ORIGIN_Y * scale);
	}

	@Nonnull
	private static ViewTransform getView(@Nonnull ModelPose pose, @Nonnull ItemRenderSheet.Row renderParams,
										 int modelHeight)
	{
		double sinTilt = Math.sin(pose.getTilt());
		double cosTilt = Math.cos(pose.getTilt());
		int zoom2d = renderParams.getZoom2d();
		// The client adds yOffset2d to the depth as well as the height.
		var offset = ViewTransform.translation(
			renderParams.getXOffset2d(),
			modelHeight / 2.0 + zoom2d * sinTilt + renderParams.getYOffset2d(),
			zoom2d * cosTilt + renderParams.getYOffset2d());

		return ViewTransform.roll(Math.sin(pose.getRoll()), Math.cos(pose.getRoll()))
			.then(ViewTransform.turn(Math.sin(pose.getTurn()), Math.cos(pose.getTurn())))
			.then(offset)
			.then(ViewTransform.tilt(sinTilt, cosTilt));
	}
}
