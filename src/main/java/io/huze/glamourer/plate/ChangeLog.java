package io.huze.glamourer.plate;

import io.huze.glamourer.glam.Glamour;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ChangeLog
{
	private static final int MAX_HISTORY = 100;

	private final Deque<Change> undoStack = new ArrayDeque<>();
	private final Deque<Change> redoStack = new ArrayDeque<>();

	@Setter
	@Nonnull
	private Consumer<Set<Integer>> onGlamourChanged = itemIds -> {};

	@Setter
	@Nonnull
	private Runnable onUndoRedoAvailabilityChanged = () -> {};

	void record(Change change)
	{
		undoStack.push(change);
		if (undoStack.size() > MAX_HISTORY)
		{
			undoStack.removeLast();
		}
		redoStack.clear();
		notifyChanged(change);
		onUndoRedoAvailabilityChanged.run();
	}

	public boolean canUndo()
	{
		return !undoStack.isEmpty();
	}

	public boolean canRedo()
	{
		return !redoStack.isEmpty();
	}

	@Nullable
	public String undoDescription()
	{
		return "Undo" + (canUndo() ? " " + undoStack.peek().description() : "");
	}

	@Nullable
	public String redoDescription()
	{
		return "Redo" + (canRedo() ? " " + redoStack.peek().description() : "");
	}

	public void undo()
	{
		if (!canUndo())
		{
			return;
		}
		Change change = undoStack.pop();
		change.undo();
		change.save();
		redoStack.push(change);
		notifyChanged(change);
		onUndoRedoAvailabilityChanged.run();
	}

	public void redo()
	{
		if (!canRedo())
		{
			return;
		}
		Change change = redoStack.pop();
		change.redo();
		change.save();
		undoStack.push(change);
		notifyChanged(change);
		onUndoRedoAvailabilityChanged.run();
	}

	private void notifyChanged(Change change)
	{
		onGlamourChanged.accept(change.affectedItemIds());
	}

	public void clear()
	{
		undoStack.clear();
		redoStack.clear();
		onUndoRedoAvailabilityChanged.run();
	}
}

// --- Change interface and base ---

interface Change
{
	void undo();

	void redo();

	void save();

	@Nonnull
	String description();

	@Nonnull
	default Set<Integer> affectedItemIds()
	{
		return Collections.emptySet();
	}
}

abstract class ReapplyChange implements Change
{
	protected abstract PlateManager getPlateManager();

	protected abstract void applyRedo();

	protected abstract void applyUndo();

	@Override
	public final void redo()
	{
		applyRedo();
		getPlateManager().reapplyAllPlates();
	}

	@Override
	public final void undo()
	{
		applyUndo();
		getPlateManager().reapplyAllPlates();
	}
}

// --- Color / Texture changes ---

@RequiredArgsConstructor
class ColorChange implements Change
{
	private final PlateManager plateManager;
	private final Plate plate;
	private final int glamourIndex;
	private final int colorIndex;
	private final short beforeColor;
	private final short afterColor;
	private final String itemName;

	@Override
	public void undo()
	{
		plate.applyGlamourColor(glamourIndex, colorIndex, beforeColor);
	}

	@Override
	public void redo()
	{
		plate.applyGlamourColor(glamourIndex, colorIndex, afterColor);
	}

	@Override
	public void save()
	{
		plateManager.saveSinglePlate(plate);
	}

	@Nonnull
	@Override
	public String description()
	{
		return "Recolor " + itemName;
	}

	@Nonnull
	@Override
	public Set<Integer> affectedItemIds()
	{
		return Set.copyOf(plate.getGlamours().get(glamourIndex).getItemIds());
	}
}

@RequiredArgsConstructor
class GroupColorChange implements Change
{
	private final PlateManager plateManager;
	private final Plate plate;
	private final int glamourIndex;
	private final int[] colorIndices;
	private final short[] beforeColors;
	private final short[] afterColors;
	private final String itemName;

	@Override
	public void undo()
	{
		plate.applyGlamourColors(glamourIndex, colorIndices, beforeColors);
	}

	@Override
	public void redo()
	{
		plate.applyGlamourColors(glamourIndex, colorIndices, afterColors);
	}

	@Override
	public void save()
	{
		plateManager.saveSinglePlate(plate);
	}

	@Nonnull
	@Override
	public String description()
	{
		return "Recolor " + itemName;
	}

	@Nonnull
	@Override
	public Set<Integer> affectedItemIds()
	{
		return Set.copyOf(plate.getGlamours().get(glamourIndex).getItemIds());
	}
}

@RequiredArgsConstructor
class TextureChange implements Change
{
	private final PlateManager plateManager;
	private final Plate plate;
	private final int glamourIndex;
	private final int textureIndex;
	private final short beforeTexture;
	private final short afterTexture;
	private final String itemName;

	@Override
	public void undo()
	{
		plate.applyGlamourTexture(glamourIndex, textureIndex, beforeTexture);
	}

	@Override
	public void redo()
	{
		plate.applyGlamourTexture(glamourIndex, textureIndex, afterTexture);
	}

	@Override
	public void save()
	{
		plateManager.saveSinglePlate(plate);
	}

	@Nonnull
	@Override
	public String description()
	{
		return "Retexture " + itemName;
	}

	@Nonnull
	@Override
	public Set<Integer> affectedItemIds()
	{
		return Set.copyOf(plate.getGlamours().get(glamourIndex).getItemIds());
	}
}

// --- Plate property changes ---

@RequiredArgsConstructor
class PlateRenameChange implements Change
{
	private final PlateManager plateManager;
	private final Plate plate;
	private final String beforeName;
	private final String afterName;

	@Override
	public void undo()
	{
		plate.applyName(beforeName);
	}

	@Override
	public void redo()
	{
		plate.applyName(afterName);
	}

	@Override
	public void save()
	{
		plateManager.saveSinglePlate(plate);
	}

	@Nonnull
	@Override
	public String description()
	{
		return "Rename " + beforeName;
	}
}

@RequiredArgsConstructor
class PlateEnableChange extends ReapplyChange
{
	@Getter
	private final PlateManager plateManager;
	private final Plate plate;
	private final boolean wasEnabled;

	@Override
	protected void applyRedo()
	{
		plate.setEnabled(!wasEnabled);
	}

	@Override
	protected void applyUndo()
	{
		plate.setEnabled(wasEnabled);
	}

	@Override
	public void save()
	{
		plateManager.saveSinglePlate(plate);
	}

	@Nonnull
	@Override
	public String description()
	{
		return (wasEnabled ? "Disable " : "Enable ") + plate.getName();
	}
}

@RequiredArgsConstructor
class PlateDisplayStyleChange extends ReapplyChange
{
	@Getter
	private final PlateManager plateManager;
	private final Plate plate;
	private final DisplayStyle beforeStyle;
	private final DisplayStyle afterStyle;

	@Override
	protected void applyRedo()
	{
		plate.setDisplayStyle(afterStyle);
	}

	@Override
	protected void applyUndo()
	{
		plate.setDisplayStyle(beforeStyle);
	}

	@Override
	public void save()
	{
		plateManager.saveSinglePlate(plate);
	}

	@Nonnull
	@Override
	public String description()
	{
		return "Change display style";
	}
}

@RequiredArgsConstructor
class PlateIconStyleChange extends ReapplyChange
{
	@Getter
	private final PlateManager plateManager;
	private final Plate plate;
	private final IconStyle beforeStyle;
	private final IconStyle afterStyle;

	@Override
	protected void applyRedo()
	{
		plate.setIconStyle(afterStyle);
	}

	@Override
	protected void applyUndo()
	{
		plate.setIconStyle(beforeStyle);
	}

	@Override
	public void save()
	{
		plateManager.saveSinglePlate(plate);
	}

	@Nonnull
	@Override
	public String description()
	{
		return "Change icon style";
	}
}

// --- Plate structural changes ---

@RequiredArgsConstructor
class PlateCreateChange extends ReapplyChange
{
	@Getter
	private final PlateManager plateManager;
	private final Plate plate;

	@Override
	protected void applyRedo()
	{
		plateManager.getPlates().add(plate);
	}

	@Override
	protected void applyUndo()
	{
		plateManager.getPlates().remove(plate);
	}

	@Override
	public void save()
	{
		plateManager.saveSinglePlate(plate);
		plateManager.savePlateOrder();
	}

	@Nonnull
	@Override
	public String description()
	{
		return "Create plate";
	}
}

@RequiredArgsConstructor
class PlateDeleteChange extends ReapplyChange
{
	@Getter
	private final PlateManager plateManager;
	private final Plate plate;
	private final int index;

	@Override
	protected void applyRedo()
	{
		plateManager.getPlates().remove(index);
		plateManager.tombstonePlate(plate.getId());
	}

	@Override
	protected void applyUndo()
	{
		plateManager.getPlates().add(Math.min(index, plateManager.getPlates().size()), plate);
		plateManager.saveSinglePlate(plate);
	}

	@Override
	public void save()
	{
		plateManager.savePlateOrder();
	}

	@Nonnull
	@Override
	public String description()
	{
		return "Delete " + plate.getName();
	}
}

@RequiredArgsConstructor
class PlateReorderChange extends ReapplyChange
{
	@Getter
	private final PlateManager plateManager;
	private final int fromIndex;
	private final int toIndex;

	@Override
	protected void applyRedo()
	{
		move(fromIndex, toIndex);
	}

	@Override
	protected void applyUndo()
	{
		move(toIndex, fromIndex);
	}

	private void move(int from, int to)
	{
		var plates = plateManager.getPlates();
		Plate p = plates.remove(from);
		plates.add(to, p);
	}

	@Override
	public void save()
	{
		plateManager.savePlateOrder();
	}

	@Nonnull
	@Override
	public String description()
	{
		return "Reorder plates";
	}
}

// --- Glamour structural changes ---

@RequiredArgsConstructor
class GlamourAddChange extends ReapplyChange
{
	@Getter
	private final PlateManager plateManager;
	private final Plate plate;
	private final Glamour glamour;
	private final int index;

	@Override
	protected void applyRedo()
	{
		plate.insertGlamour(index, glamour);
	}

	@Override
	protected void applyUndo()
	{
		plate.removeGlamour(index);
	}

	@Override
	public void save()
	{
		plateManager.saveSinglePlate(plate);
	}

	@Nonnull
	@Override
	public String description()
	{
		return "Add " + glamour.getItemName();
	}
}

@RequiredArgsConstructor
class GlamourRemoveChange extends ReapplyChange
{
	@Getter
	private final PlateManager plateManager;
	private final Plate plate;
	private final Glamour glamour;
	private final int index;

	@Override
	protected void applyRedo()
	{
		plate.removeGlamour(index);
	}

	@Override
	protected void applyUndo()
	{
		plate.insertGlamour(index, glamour);
	}

	@Override
	public void save()
	{
		plateManager.saveSinglePlate(plate);
	}

	@Nonnull
	@Override
	public String description()
	{
		return "Remove " + glamour.getItemName();
	}
}

@RequiredArgsConstructor
class GlamourReorderChange implements Change
{
	private final PlateManager plateManager;
	private final Plate plate;
	private final int fromIndex;
	private final int toIndex;
	private final String itemName;

	@Override
	public void undo()
	{
		plate.moveGlamour(toIndex, fromIndex);
	}

	@Override
	public void redo()
	{
		plate.moveGlamour(fromIndex, toIndex);
	}

	@Override
	public void save()
	{
		plateManager.saveSinglePlate(plate);
	}

	@Nonnull
	@Override
	public String description()
	{
		return "Reorder " + itemName;
	}
}

@RequiredArgsConstructor
class GlamourMoveChange extends ReapplyChange
{
	@Getter
	private final PlateManager plateManager;
	private final Plate sourcePlate;
	private final int sourceIndex;
	private final Plate targetPlate;
	private final int targetIndex;
	private final Glamour glamour;

	@Override
	protected void applyRedo()
	{
		sourcePlate.removeGlamour(sourceIndex);
		targetPlate.insertGlamour(targetIndex, glamour);
	}

	@Override
	protected void applyUndo()
	{
		targetPlate.removeGlamour(targetIndex);
		sourcePlate.insertGlamour(sourceIndex, glamour);
	}

	@Override
	public void save()
	{
		plateManager.saveSinglePlate(sourcePlate);
		plateManager.saveSinglePlate(targetPlate);
	}

	@Nonnull
	@Override
	public String description()
	{
		return "Move " + glamour.getItemName();
	}
}
