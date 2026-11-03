package io.huze.glamourer.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/// One drop point per gap in a vertical stack of rows: slot k is the gap above row k, and the row
/// count is the gap below the last. The owner registers the rows as it lays them out and calls
/// paint after its children, so the bar sits centred on the chosen gap, kept inside the container.
class DropGapIndicator
{
	static final int NO_SLOT = -1;
	static final int THICKNESS = 3;

	private final JComponent container;
	private final Color color;
	private final List<Component> rows = new ArrayList<>();
	private int slot = NO_SLOT;

	DropGapIndicator(JComponent container, Color color)
	{
		this.container = container;
		this.color = color;
	}

	void clearRows()
	{
		rows.clear();
	}

	void addRow(Component row)
	{
		rows.add(row);
	}

	/// The gap nearest a point given in another component's coordinates. The two gaps beside the
	/// row at ownIndex, where dropping that row would change nothing, come back as NO_SLOT.
	int slotAt(Point point, Component in, int ownIndex)
	{
		int y = SwingUtilities.convertPoint(in, point, container).y;
		int slot = 0;
		for (Component row : rows)
		{
			if (y < row.getY() + row.getHeight() / 2)
			{
				break;
			}
			slot++;
		}
		if (ownIndex >= 0 && (slot == ownIndex || slot == ownIndex + 1))
		{
			return NO_SLOT;
		}
		return slot;
	}

	void setSlot(int slot)
	{
		if (this.slot != slot)
		{
			this.slot = slot;
			container.repaint();
		}
	}

	void clear()
	{
		setSlot(NO_SLOT);
	}

	void paint(Graphics g)
	{
		if (slot < 0 || slot > rows.size() || rows.isEmpty())
		{
			return;
		}
		int gapTop;
		int gapBottom;
		if (slot == 0)
		{
			gapBottom = rows.get(0).getY();
			gapTop = gapBottom - THICKNESS;
		}
		else
		{
			Component above = rows.get(slot - 1);
			gapTop = above.getY() + above.getHeight();
			gapBottom = slot < rows.size() ? rows.get(slot).getY() : gapTop + THICKNESS;
		}
		int y = (gapTop + gapBottom - THICKNESS) / 2;
		y = Math.max(0, Math.min(y, container.getHeight() - THICKNESS));
		Component reference = rows.get(Math.min(slot, rows.size() - 1));
		g.setColor(color);
		g.fillRect(reference.getX(), y, reference.getWidth(), THICKNESS);
	}
}
