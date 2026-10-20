package io.huze.glamourer.ui;

import io.huze.glamourer.glam.IconService;
import io.huze.glamourer.item.SearchResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

class SearchResultPanel extends JPanel
{
	private final IconService iconService;
	private final int itemId;
	private final JLabel iconLabel = new JLabel();
	private boolean iconRequested;

	public SearchResultPanel(SearchResult result, IconService iconService,
							 Consumer<Integer> onSelect, boolean disabled)
	{
		this.iconService = iconService;
		this.itemId = result.getId();

		setLayout(new BorderLayout(5, 0));
		setBorder(new EmptyBorder(5, 5, 5, 5));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		var iconSize = iconService.getIconSize();
		iconLabel.setPreferredSize(iconSize);
		iconLabel.setMinimumSize(iconSize);
		add(iconLabel, BorderLayout.WEST);

		var nameLabel = new JLabel(result.getName());
		nameLabel.setForeground(disabled ? Color.GRAY : Color.WHITE);
		add(nameLabel, BorderLayout.CENTER);

		if (disabled)
		{
			setToolTipText("Already present in plate");
			return;
		}

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setCursor(Cursor.getDefaultCursor());
				setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					onSelect.accept(itemId);
				}
			}
		});
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (!iconRequested)
		{
			iconRequested = true;
			ImageIcons.setIcon(iconLabel, iconService.getItemIcon(itemId));
		}
	}
}
