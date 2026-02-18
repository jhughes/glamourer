package io.huze.glamourer.ui;

import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;

public class VerticalScrollPane extends JScrollPane
{
	public VerticalScrollPane()
	{
		super(new ScrollableContainer());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
		setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
	}

	ScrollableContainer getContainer()
	{
		return (ScrollableContainer) getViewport().getView();
	}

	void scrollToTop()
	{
		getVerticalScrollBar().setValue(0);
	}

	void scrollToBottom()
	{
		var vertical = getVerticalScrollBar();
		vertical.setValue(vertical.getMaximum());
	}

	/// Scrollable wrapper forces content width to match the viewport, preventing clipping when the scrollbar appears
	public static class ScrollableContainer extends JPanel implements Scrollable
	{
		private ScrollableContainer()
		{
			setBackground(ColorScheme.DARK_GRAY_COLOR);
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

}
