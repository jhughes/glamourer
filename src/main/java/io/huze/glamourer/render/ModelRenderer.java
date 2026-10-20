package io.huze.glamourer.render;

import io.huze.glamourer.item.ItemRenderSheet;
import io.huze.glamourer.texture.SamplableTextureProvider;
import io.huze.glamourer.texture.TextureAnimation;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Constants;

/// Executor thread only.
@Singleton
public final class ModelRenderer
{
	private static final int ICON_SUPERSAMPLE = 3;

	private final ModelProjector projector = new ModelProjector();
	private final ModelRasterizer rasterizer;

	@Inject
	public ModelRenderer(@Nonnull SamplableTextureProvider textureProvider)
	{
		this.rasterizer = new ModelRasterizer(textureProvider);
	}

	/// Scale of 1 is 36 x 32
	@Nonnull
	public static Dimension getIconSize(float scale)
	{
		return new Dimension(Math.round(Constants.ITEM_SPRITE_WIDTH * scale), Math.round(Constants.ITEM_SPRITE_HEIGHT * scale));
	}

	@Nonnull
	public ModelRegions drawOrbit(@Nonnull PosedModel model, @Nonnull OrbitView view, int supersample,
								  @Nonnull BufferedImage target)
	{
		var rasterSize = new RasterSize(target.getWidth(), target.getHeight(), supersample);
		var snapshot = model.getSnapshot();
		var camera = new OrbitCamera(snapshot.getBounds(), model.pose, view, target.getWidth(), target.getHeight());
		return rasterizer.draw(projector.project(snapshot, camera, rasterSize), TextureAnimation.currentCycle(), target);
	}

	public void drawIcon(@Nonnull PosedModel model, @Nonnull ItemRenderSheet.Row renderParams, float scale,
						 @Nonnull BufferedImage target)
	{
		var rasterSize = new RasterSize(target.getWidth(), target.getHeight(), ICON_SUPERSAMPLE);
		var snapshot = model.getSnapshot();
		var camera = new IconCamera(model.pose, renderParams, snapshot.getBounds().getHeight(), scale);
		rasterizer.drawOutlined(projector.project(snapshot, camera, rasterSize), TextureAnimation.currentCycle(), target);
	}
}
