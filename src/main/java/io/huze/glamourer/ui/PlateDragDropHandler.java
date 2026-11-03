package io.huze.glamourer.ui;

import io.huze.glamourer.ui.ItemDragDropHandler.ItemData;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

public class PlateDragDropHandler
{
	static final DataFlavor PLATE_FLAVOR = new DataFlavor(Integer.class, "PlateIndex");

	public static void setupDragAndDrop(PlateRowPanel rowPanel, int index, DropGapIndicator plateDropGaps,
										BiConsumer<Integer, Integer> onReorder, ItemDragDropHandler.ItemMoveCallback onItemMoved)
	{
		DragDropUtil.installDragSource(rowPanel.getHeaderPanel(), rowPanel, PLATE_FLAVOR, () -> index);
		new PlateRowDropTarget(rowPanel, plateDropGaps, onReorder, onItemMoved);
	}

	/// Takes other plates (a bar in the nearest gap between plates) and items (an outline to append
	/// to a closed or empty plate, otherwise a bar in the nearest gap between its rows).
	private static class PlateRowDropTarget extends BorderDropTarget
	{
		private final PlateRowPanel rowPanel;
		private final DropGapIndicator plateDropGaps;
		private final BiConsumer<Integer, Integer> onReorder;
		private final ItemDragDropHandler.ItemMoveCallback onItemMoved;

		PlateRowDropTarget(PlateRowPanel rowPanel, DropGapIndicator plateDropGaps,
						   BiConsumer<Integer, Integer> onReorder, ItemDragDropHandler.ItemMoveCallback onItemMoved)
		{
			super(rowPanel, rowPanel);
			this.rowPanel = rowPanel;
			this.plateDropGaps = plateDropGaps;
			this.onReorder = onReorder;
			this.onItemMoved = onItemMoved;
		}

		/// The index of the plate being dragged, if one is.
		@Nullable
		private Integer draggedPlate(Transferable transferable)
		{
			return DragDropUtil.getTransferData(transferable, PLATE_FLAVOR, Integer.class);
		}

		/// The dragged item, if this plate can take it: its own items can only move between rows.
		@Nullable
		private ItemData acceptableItem(Transferable transferable)
		{
			var plate = rowPanel.getPlate();
			ItemData item = ItemDragDropHandler.getItemData(transferable);
			if (item == null)
			{
				return null;
			}
			if (item.getPlate() == plate)
			{
				return showsItemRows() ? item : null;
			}
			return plate.containsItem(item.getGlamour().getPrimaryItemId()) ? null : item;
		}

		private boolean showsItemRows()
		{
			return rowPanel.isExpanded() && !rowPanel.getPlate().getGlamours().isEmpty();
		}

		private int plateSlot(int source, Point location)
		{
			return plateDropGaps.slotAt(location, rowPanel, source);
		}

		/// The gap the item would drop into; a closed or empty plate takes it at the end.
		private int itemSlot(ItemData item, Point location)
		{
			if (!showsItemRows())
			{
				return rowPanel.getPlate().getGlamours().size();
			}
			int ownIndex = item.getPlate() == rowPanel.getPlate() ? item.getIndex() : DropGapIndicator.NO_SLOT;
			return rowPanel.getItemDropGaps().slotAt(location, rowPanel, ownIndex);
		}

		/// Rows are removed before reinsertion, so a slot below the dragged row shifts up by one.
		private static int insertIndex(int ownIndex, int slot)
		{
			return ownIndex >= 0 && ownIndex < slot ? slot - 1 : slot;
		}

		@Override
		protected void clear()
		{
			super.clear();
			plateDropGaps.clear();
			rowPanel.getItemDropGaps().clear();
		}

		@Override
		public void dragOver(DropTargetDragEvent dtde)
		{
			Integer source = draggedPlate(dtde.getTransferable());
			if (source != null)
			{
				int slot = plateSlot(source, dtde.getLocation());
				if (slot == DropGapIndicator.NO_SLOT)
				{
					dtde.rejectDrag();
					clear();
					return;
				}
				dtde.acceptDrag(DnDConstants.ACTION_MOVE);
				plateDropGaps.setSlot(slot);
				return;
			}
			ItemData item = acceptableItem(dtde.getTransferable());
			if (item == null)
			{
				dtde.rejectDrag();
				clear();
				return;
			}
			if (!showsItemRows())
			{
				dtde.acceptDrag(DnDConstants.ACTION_MOVE);
				showOutline(DragDropUtil.ITEM_DRAG_COLOR);
				return;
			}
			int slot = itemSlot(item, dtde.getLocation());
			if (slot == DropGapIndicator.NO_SLOT)
			{
				dtde.rejectDrag();
				clear();
				return;
			}
			dtde.acceptDrag(DnDConstants.ACTION_MOVE);
			rowPanel.getItemDropGaps().setSlot(slot);
		}

		@Override
		protected void handleDrop(DropTargetDropEvent dtde)
		{
			Integer source = draggedPlate(dtde.getTransferable());
			if (source != null)
			{
				int slot = plateSlot(source, dtde.getLocation());
				if (slot == DropGapIndicator.NO_SLOT)
				{
					dtde.rejectDrop();
					return;
				}
				dtde.acceptDrop(DnDConstants.ACTION_MOVE);
				onReorder.accept(source, insertIndex(source, slot));
				dtde.dropComplete(true);
				return;
			}
			ItemData item = acceptableItem(dtde.getTransferable());
			int slot = item == null ? DropGapIndicator.NO_SLOT : itemSlot(item, dtde.getLocation());
			if (slot == DropGapIndicator.NO_SLOT)
			{
				dtde.rejectDrop();
				return;
			}
			dtde.acceptDrop(DnDConstants.ACTION_MOVE);
			var plate = rowPanel.getPlate();
			int ownIndex = item.getPlate() == plate ? item.getIndex() : DropGapIndicator.NO_SLOT;
			onItemMoved.moveItem(item.getPlate(), item.getIndex(), plate, insertIndex(ownIndex, slot));
			dtde.dropComplete(true);
		}
	}
}
