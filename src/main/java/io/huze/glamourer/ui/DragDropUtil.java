package io.huze.glamourer.ui;

import java.awt.Color;
import java.awt.Insets;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

public class DragDropUtil
{
	public static final Color ITEM_DRAG_COLOR = new Color(220, 138, 0);
	public static final Color PLATE_DRAG_COLOR = new Color(50, 205, 50);
	public static final Color DRAGGING_SOURCE_COLOR = new Color(60, 60, 60);
	public static final int INDICATOR_THICKNESS = 2;

	/// Creates a border that outlines the whole component as a drop zone, while preserving the original padding/insets.
	public static Border createOutlineBorder(Insets originalInsets, Color color)
	{
		return BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(color, INDICATOR_THICKNESS),
			BorderFactory.createEmptyBorder(
				Math.max(0, originalInsets.top - INDICATOR_THICKNESS),
				Math.max(0, originalInsets.left - INDICATOR_THICKNESS),
				Math.max(0, originalInsets.bottom - INDICATOR_THICKNESS),
				Math.max(0, originalInsets.right - INDICATOR_THICKNESS))
		);
	}

	/// Starts a left-button drag from the gesture component carrying the data as the one flavor,
	/// dimming the visual component until the drag ends.
	public static void installDragSource(JComponent gestureComponent, JComponent visualComponent,
										 DataFlavor flavor, Supplier<?> data)
	{
		Color originalBackground = visualComponent.getBackground();
		DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(gestureComponent, DnDConstants.ACTION_MOVE, dge -> {
			if (isLeftButtonDrag(dge))
			{
				startDrag(dge, new SingleFlavorTransferable<>(flavor, data.get()), visualComponent, originalBackground);
			}
		});
	}

	private static void startDrag(DragGestureEvent dge, Transferable transferable,
								  JComponent visualComponent, Color originalBackground)
	{
		visualComponent.setBackground(DRAGGING_SOURCE_COLOR);

		// Use transparent drag image to avoid ghost
		BufferedImage transparentImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

		dge.getDragSource().startDrag(
			dge,
			DragSource.DefaultMoveDrop,
			transparentImg,
			new Point(0, 0),
			transferable,
			new DragSourceAdapter()
			{
				@Override
				public void dragDropEnd(DragSourceDropEvent dsde)
				{
					visualComponent.setBackground(originalBackground);
				}
			}
		);
	}

	/// Checks if a drag gesture was initiated with the left mouse button.
	/// Only left-button drags should start drag operations.
	public static boolean isLeftButtonDrag(DragGestureEvent dge)
	{
		if (dge.getTriggerEvent() instanceof MouseEvent)
		{
			MouseEvent me = (MouseEvent) dge.getTriggerEvent();
			return SwingUtilities.isLeftMouseButton(me);
		}
		return true; // Default to allowing if we can't determine
	}

	/// Extracts transfer data safely, returning null on any error.
	@SuppressWarnings("unchecked")
	public static <T> T getTransferData(Transferable transferable, DataFlavor flavor, Class<T> type)
	{
		try
		{
			if (transferable.isDataFlavorSupported(flavor))
			{
				Object data = transferable.getTransferData(flavor);
				if (type.isInstance(data))
				{
					return (T) data;
				}
			}
		}
		catch (Exception ignored)
		{
		}
		return null;
	}
}
