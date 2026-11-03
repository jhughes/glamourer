package io.huze.glamourer.ui;

import java.awt.Color;
import java.awt.Insets;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import javax.swing.JComponent;
import javax.swing.border.Border;

/// A drop target that answers drags by outlining the visual component, and puts its border back
/// when the drag leaves or drops. Subclasses decide what to accept and what a drop does.
abstract class BorderDropTarget extends DropTargetAdapter
{
	protected final JComponent visual;
	private final Border originalBorder;
	private final Insets originalInsets;
	private Color shownOutline;

	BorderDropTarget(JComponent target, JComponent visual)
	{
		this.visual = visual;
		this.originalBorder = visual.getBorder();
		this.originalInsets = originalBorder != null ? originalBorder.getBorderInsets(visual) : new Insets(0, 0, 0, 0);
		new DropTarget(target, DnDConstants.ACTION_MOVE, this, true);
	}

	/// dragOver fires on every pixel of motion, so the border is only replaced when it would change.
	protected void showOutline(Color color)
	{
		if (!color.equals(shownOutline))
		{
			shownOutline = color;
			visual.setBorder(DragDropUtil.createOutlineBorder(originalInsets, color));
		}
	}

	protected void clear()
	{
		if (shownOutline != null)
		{
			shownOutline = null;
			visual.setBorder(originalBorder);
		}
	}

	@Override
	public void dragExit(DropTargetEvent dte)
	{
		clear();
	}

	@Override
	public final void drop(DropTargetDropEvent dtde)
	{
		clear();
		handleDrop(dtde);
	}

	protected abstract void handleDrop(DropTargetDropEvent dtde);
}
