package io.huze.glamourer.ui;

import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.plate.Plate;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import lombok.Value;

/// Item rows are only drag sources; the plate row they sit in is the drop target, so one bar can
/// mark the gap between rows rather than an edge of whichever row is under the pointer.
public class ItemDragDropHandler
{
	static final DataFlavor ITEM_FLAVOR = new DataFlavor(ItemData.class, "GlamourItem");

	public interface ItemMoveCallback
	{
		void moveItem(Plate sourcePlate, int sourceIndex, Plate targetPlate, int targetIndex);
	}

	/// The item row being dragged.
	@Value
	public static class ItemData
	{
		Plate plate;
		int index;

		public Glamour getGlamour()
		{
			return plate.getGlamours().get(index);
		}
	}

	/// The dragged item, or null if the transferable holds something else.
	@Nullable
	static ItemData getItemData(Transferable transferable)
	{
		return DragDropUtil.getTransferData(transferable, ITEM_FLAVOR, ItemData.class);
	}

	public static void setupItemDragSource(JComponent row, Plate plate, int index)
	{
		DragDropUtil.installDragSource(row, row, ITEM_FLAVOR, () -> new ItemData(plate, index));
	}
}
