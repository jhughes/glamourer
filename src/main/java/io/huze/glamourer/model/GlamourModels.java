package io.huze.glamourer.model;

import io.huze.glamourer.glam.ModelEdits;
import io.huze.glamourer.item.ItemEquipSheet;
import io.huze.glamourer.item.ItemRenderSheet;
import io.huze.glamourer.item.ItemSpotAnimSheet;
import io.huze.glamourer.item.PetSheet;
import io.huze.glamourer.npc.NpcSheet;
import io.huze.glamourer.render.ModelPose;
import io.huze.glamourer.render.ModelSnapshot;
import io.huze.glamourer.render.PosedModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.client.callback.ClientThread;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class GlamourModels
{
	private static final double FULL_INTENSITY = 1;
	private static final int MAX_LOAD_ATTEMPTS = 10;

	private final Client client;
	private final ClientThread clientThread;
	private final ModelReader modelReader;
	private final ItemRenderSheet itemRenderSheet;
	private final ItemEquipSheet itemEquipSheet;
	private final PetSheet petSheet;
	private final NpcSheet npcSheet;
	private final ItemSpotAnimSheet itemSpotAnimSheet;

	@Nonnull
	public List<ModelChoice> getChoices(int primaryItemId, @Nonnull Collection<Integer> memberItemIds)
	{
		// Primary first, so its models are numbered 0.
		var orderedItemIds = new LinkedHashSet<Integer>();
		orderedItemIds.add(primaryItemId);
		orderedItemIds.addAll(new TreeSet<>(memberItemIds));
		List<Integer> maleItems = getItemsWithDistinctWornSets(orderedItemIds, itemEquipSheet::getMaleModelIds);
		List<Integer> femaleItems = getItemsWithDistinctWornSets(orderedItemIds, itemEquipSheet::getFemaleModelIds);

		var choices = new ArrayList<ModelChoice>();
		choices.add(ModelChoice.ITEM);
		if (maleItems.equals(femaleItems) && wornSetsMatch(maleItems))
		{
			addWornChoices(choices, ModelChoice.Kind.WORN_MALE, "Equipped", maleItems);
		}
		else
		{
			addWornChoices(choices, ModelChoice.Kind.WORN_MALE, "Male", maleItems);
			addWornChoices(choices, ModelChoice.Kind.WORN_FEMALE, "Female", femaleItems);
		}
		var npcIds = petSheet.getNpcIds(primaryItemId);
		if (!npcIds.isEmpty())
		{
			choices.add(ModelChoice.pet(npcIds.get(0)));
		}
		addSpotAnimChoices(choices, orderedItemIds);
		return choices;
	}

	private void addSpotAnimChoices(@Nonnull List<ModelChoice> choices, @Nonnull Collection<Integer> itemIds)
	{
		var distinct = new ArrayList<ItemSpotAnimSheet.Row>();
		for (int itemId : itemIds)
		{
			for (var row : itemSpotAnimSheet.getRowsForItem(itemId))
			{
				if (distinct.stream().noneMatch(seen -> seen.getModelId() == row.getModelId()))
				{
					distinct.add(row);
				}
			}
		}
		for (int index = 0; index < distinct.size(); index++)
		{
			choices.add(ModelChoice.spotAnim(distinct.get(index).getSpotAnimId(), numbered("SpotAnim", index, distinct.size())));
		}
	}

	@Nonnull
	private static String numbered(@Nonnull String name, int index, int count)
	{
		return count > 1 ? name + " " + index : name;
	}

	@Nonnull
	private static List<Integer> getItemsWithDistinctWornSets(@Nonnull Collection<Integer> itemIds,
															  @Nonnull IntFunction<int[]> wornModelIdsOf)
	{
		var sets = new ArrayList<int[]>();
		var items = new ArrayList<Integer>();
		for (int itemId : itemIds)
		{
			var modelIds = wornModelIdsOf.apply(itemId);
			if (modelIds.length > 0 && sets.stream().noneMatch(set -> Arrays.equals(set, modelIds)))
			{
				sets.add(modelIds);
				items.add(itemId);
			}
		}
		return items;
	}

	private boolean wornSetsMatch(@Nonnull List<Integer> itemIds)
	{
		for (int itemId : itemIds)
		{
			if (!Arrays.equals(itemEquipSheet.getMaleModelIds(itemId), itemEquipSheet.getFemaleModelIds(itemId)))
			{
				return false;
			}
		}
		return true;
	}

	private static void addWornChoices(@Nonnull List<ModelChoice> choices, @Nonnull ModelChoice.Kind gender,
									   @Nonnull String name, @Nonnull List<Integer> itemIds)
	{
		for (int index = 0; index < itemIds.size(); index++)
		{
			choices.add(ModelChoice.worn(gender, itemIds.get(index), numbered(name, index, itemIds.size())));
		}
	}

	/// Retries each tick while read returns null, then gives onRead null. Both run on the client thread.
	public void readWhenLoaded(@Nonnull Supplier<PosedModel> read, @Nonnull Consumer<PosedModel> onRead)
	{
		final var attempts = new AtomicInteger();
		clientThread.invokeLater(() -> {
			var model = read.get();
			if (model == null && attempts.incrementAndGet() < MAX_LOAD_ATTEMPTS)
			{
				return false;
			}
			onRead.accept(model);
			return true;
		});
	}

	/// Client thread only. Null while loading, or when the item has no such model.
	@Nullable
	public PosedModel readModel(@Nonnull ModelChoice choice,
								@Nonnull ItemComposition itemComp,
								int inventoryModel,
								@Nonnull ModelEdits edits,
								double intensity)
	{
		switch (choice.getKind())
		{
			case ITEM:
				return readIconModel(itemComp, inventoryModel, edits, intensity);
			case WORN_MALE:
				return pose(readWornSnapshot(itemEquipSheet.getMaleModelIds(choice.getId()), edits, intensity), ModelPose.UPRIGHT);
			case WORN_FEMALE:
				return pose(readWornSnapshot(itemEquipSheet.getFemaleModelIds(choice.getId()), edits, intensity), ModelPose.UPRIGHT);
			case PET:
				return pose(readNpcSnapshot(choice.getId(), edits, intensity), ModelPose.UPRIGHT);
			case SPOT_ANIM:
				var rows = itemSpotAnimSheet.getRows(choice.getId());
				return rows.isEmpty()
					? null
					: pose(readSpotAnimSnapshot(rows.get(0), edits, intensity), ModelPose.UPRIGHT);
			default:
				return null;
		}
	}

	@Nullable
	private ModelSnapshot readSpotAnimSnapshot(@Nonnull ItemSpotAnimSheet.Row spotAnim, @Nonnull ModelEdits edits, double intensity)
	{
		var loaded = client.loadModelData(spotAnim.getModelId());
		if (loaded == null)
		{
			return null;
		}
		var modelData = spotAnim.prepare(loaded);
		var indices = FaceIndices.of(modelData, edits.getColorFind(), edits.getTextureFind());
		var lighting = Lighting.forActor(spotAnim.getAmbient(), spotAnim.getContrast()).withIntensity(intensity);
		return capture(edits.apply(modelData), lighting, indices);
	}

	/// Client thread only. Null while loading.
	@Nullable
	public PosedModel readIconModel(@Nonnull ItemComposition itemComp)
	{
		return readIconModel(itemComp, itemComp.getInventoryModel(), ModelEdits.of(itemComp));
	}

	/// A glamour keeps the item's own inventory model, so the composition's is not trusted. Client thread only.
	@Nullable
	public PosedModel readIconModel(@Nonnull ItemComposition itemComp, int inventoryModel,
									@Nonnull ModelEdits edits)
	{
		return readIconModel(itemComp, inventoryModel, edits, FULL_INTENSITY);
	}

	@Nullable
	private PosedModel readIconModel(@Nonnull ItemComposition itemComp, int inventoryModel,
									 @Nonnull ModelEdits edits, double intensity)
	{
		var renderParams = itemRenderSheet.getRenderParams(itemComp);
		return pose(readInventorySnapshot(itemComp, renderParams, inventoryModel, edits, intensity),
			renderParams.getPose());
	}

	@Nullable
	private static PosedModel pose(@Nullable ModelSnapshot snapshot, @Nonnull ModelPose pose)
	{
		return snapshot == null ? null : new PosedModel(snapshot, pose);
	}

	@Nullable
	private ModelSnapshot readInventorySnapshot(@Nonnull ItemComposition itemComp,
												@Nonnull ItemRenderSheet.Row renderParams,
												int inventoryModel,
												@Nonnull ModelEdits edits,
												double intensity)
	{
		var modelData = ModelLoader.load(client, inventoryModel);
		if (modelData == null)
		{
			return null;
		}

		if (renderParams.isResized())
		{
			modelData = modelData.cloneVertices()
				.scale(renderParams.getResizeX(), renderParams.getResizeY(), renderParams.getResizeZ());
		}
		var indices = FaceIndices.of(modelData, edits.getColorFind(), edits.getTextureFind());
		modelData = edits.apply(modelData);

		var lighting = Lighting.forItem(itemComp.getAmbient(), itemComp.getContrast()).withIntensity(intensity);
		return capture(modelData, lighting, indices);
	}

	@Nullable
	private ModelSnapshot readWornSnapshot(@Nonnull int[] modelIds, @Nonnull ModelEdits edits, double intensity)
	{
		var modelData = ModelLoader.merge(client, modelIds);
		if (modelData == null)
		{
			return null;
		}
		var indices = FaceIndices.of(modelData, edits.getColorFind(), edits.getTextureFind());
		return capture(edits.apply(modelData), Lighting.forActor(0, 0).withIntensity(intensity), indices);
	}

	/// The composition recolors before the glamour, so the glamour's find colors are mapped through it.
	@Nullable
	private ModelSnapshot readNpcSnapshot(int npcId, @Nonnull ModelEdits edits, double intensity)
	{
		var npcModel = NpcModel.load(client, npcSheet, npcId);
		if (npcModel == null)
		{
			return null;
		}
		var mapped = edits.withColorFind(npcModel.getColorsAfterComposition(edits.getColorFind()));
		var indices = FaceIndices.of(npcModel.getData(), mapped.getColorFind(), mapped.getTextureFind());
		mapped.applyInPlace(npcModel.getData());
		return capture(npcModel.getData(), npcModel.getLighting().withIntensity(intensity), indices);
	}

	@Nullable
	private ModelSnapshot capture(@Nonnull ModelData modelData, @Nonnull Lighting lighting, @Nonnull FaceIndices indices)
	{
		Model lit = lighting.light(modelData);
		return lit == null ? null : modelReader.captureSnapshot(lit, indices);
	}
}
