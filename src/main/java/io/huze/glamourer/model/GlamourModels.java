package io.huze.glamourer.model;

import io.huze.glamourer.glam.ModelEdits;
import io.huze.glamourer.item.ItemEquipSheet;
import io.huze.glamourer.item.ItemRenderSheet;
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

	@Nonnull
	public List<ModelChoice> getChoices(int primaryItemId, @Nonnull Collection<Integer> memberItemIds)
	{
		// Primary first, so its models are numbered 0.
		var orderedItemIds = new LinkedHashSet<Integer>();
		orderedItemIds.add(primaryItemId);
		orderedItemIds.addAll(new TreeSet<>(memberItemIds));
		List<int[]> maleSets = getDistinctWornSets(orderedItemIds, itemEquipSheet::getMaleModelIds);
		List<int[]> femaleSets = getDistinctWornSets(orderedItemIds, itemEquipSheet::getFemaleModelIds);

		var choices = new ArrayList<ModelChoice>();
		choices.add(ModelChoice.ITEM);
		if (Arrays.deepEquals(maleSets.toArray(), femaleSets.toArray()))
		{
			addWornChoices(choices, "Equipped", maleSets);
		}
		else
		{
			addWornChoices(choices, "Male", maleSets);
			addWornChoices(choices, "Female", femaleSets);
		}
		if (!petSheet.getNpcIds(primaryItemId).isEmpty())
		{
			choices.add(ModelChoice.PET);
		}
		return choices;
	}

	@Nonnull
	private static List<int[]> getDistinctWornSets(@Nonnull Collection<Integer> itemIds,
												   @Nonnull IntFunction<int[]> wornModelIdsOf)
	{
		var sets = new ArrayList<int[]>();
		for (int itemId : itemIds)
		{
			var modelIds = wornModelIdsOf.apply(itemId);
			if (modelIds.length > 0 && sets.stream().noneMatch(set -> Arrays.equals(set, modelIds)))
			{
				sets.add(modelIds);
			}
		}
		return sets;
	}

	private static void addWornChoices(@Nonnull List<ModelChoice> choices, @Nonnull String name, @Nonnull List<int[]> sets)
	{
		for (int index = 0; index < sets.size(); index++)
		{
			choices.add(ModelChoice.worn(name, sets.get(index), index, sets.size()));
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
			case WORN:
				return pose(readWornSnapshot(choice.getWornModelIds(), edits, intensity), ModelPose.UPRIGHT);
			case PET:
				var npcIds = petSheet.getNpcIds(itemComp.getId());
				return npcIds.isEmpty()
					? null
					: pose(readNpcSnapshot(npcIds.get(0), edits, intensity), ModelPose.UPRIGHT);
			default:
				return null;
		}
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
