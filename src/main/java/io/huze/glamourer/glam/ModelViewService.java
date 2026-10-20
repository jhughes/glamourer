package io.huze.glamourer.glam;

import io.huze.glamourer.item.DedupeItemManager;
import io.huze.glamourer.model.GlamourModels;
import io.huze.glamourer.model.ModelChoice;
import io.huze.glamourer.render.PosedModel;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ModelViewService
{
	private final DedupeItemManager ddItemManager;
	private final GlamourModels glamourModels;

	@Nonnull
	public List<ModelChoice> getChoices(@Nonnull Glamour glamour)
	{
		final int itemId = glamour.getPrimaryItemId();
		return glamourModels.getChoices(itemId, ddItemManager.getGroupItemIds(itemId));
	}

	public void readModel(@Nonnull ModelChoice choice,
						  @Nonnull Glamour glamour,
						  double intensity,
						  @Nonnull Consumer<PosedModel> onRead)
	{
		final int itemId = glamour.getPrimaryItemId();
		final var state = glamour.snapshotState();
		final int inventoryModel = state.getModel();
		final var edits = state.getModelEdits();
		glamourModels.readWhenLoaded(
			() -> glamourModels.readModel(choice, ddItemManager.getItemDefinition(itemId), inventoryModel, edits, intensity),
			model -> SwingUtilities.invokeLater(() -> onRead.accept(model)));
	}
}
