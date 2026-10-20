package io.huze.glamourer.model;

import io.huze.glamourer.glam.ModelEdits;
import io.huze.glamourer.item.DedupeItemManager;
import io.huze.glamourer.item.ItemRenderSheet;
import io.huze.glamourer.render.ModelRenderer;
import io.huze.glamourer.render.PosedModel;
import java.awt.Dimension;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.util.AsyncBufferedImage;

/// Reads on the client thread, draws on the executor.
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ItemIconRenderer
{
	private final ScheduledExecutorService executor;
	private final DedupeItemManager ddItemManager;
	private final GlamourModels glamourModels;
	private final ItemRenderSheet itemRenderSheet;
	private final ModelRenderer renderer;

	@Nonnull
	public static Dimension getIconSize(float scale)
	{
		return ModelRenderer.getIconSize(scale);
	}

	public void drawIcon(int itemId, float scale, @Nonnull AsyncBufferedImage target, @Nonnull Runnable onUnavailable)
	{
		drawIcon(itemId, scale, target, onUnavailable, glamourModels::readIconModel);
	}

	public void drawIcon(int itemId, int inventoryModel, @Nonnull ModelEdits edits, float scale,
						 @Nonnull AsyncBufferedImage target, @Nonnull Runnable onUnavailable)
	{
		drawIcon(itemId, scale, target, onUnavailable,
			itemComp -> glamourModels.readIconModel(itemComp, inventoryModel, edits));
	}

	/// onUnavailable runs on the client thread.
	private void drawIcon(int itemId, float scale, @Nonnull AsyncBufferedImage target, @Nonnull Runnable onUnavailable,
						  @Nonnull Function<ItemComposition, PosedModel> readIconModel)
	{
		glamourModels.readWhenLoaded(() -> readIconModel.apply(ddItemManager.getItemDefinition(itemId)), model -> {
			if (model == null)
			{
				log.debug("Gave up loading the icon model for item {}", itemId);
				onUnavailable.run();
				return;
			}
			var renderParams = itemRenderSheet.getRenderParams(ddItemManager.getItemDefinition(itemId));
			executor.execute(() -> {
				renderer.drawIcon(model, renderParams, scale, target);
				target.loaded();
			});
		});
	}
}
