package io.huze.glamourer.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

public class CollapsiblePanel extends JPanel
{
	@Nonnull
	private final JComponent content;
	@Nonnull
	private final JButton toggleButton;
	private boolean expanded;

	public CollapsiblePanel(@Nonnull String title, @Nullable Icon icon, @Nonnull JComponent content, boolean expanded)
	{
		this.content = content;
		this.expanded = expanded;
		this.toggleButton = new JButton();

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		var header = new JPanel(new BorderLayout(2, 0));
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(1, 1, 1, 1));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		// The label has no listeners, so its clicks fall through to the header.
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				if (SwingUtilities.isLeftMouseButton(event))
				{
					setExpanded(!expanded);
				}
			}
		});

		toggleButton.addActionListener(e -> setExpanded(!this.expanded));
		header.add(toggleButton, BorderLayout.WEST);

		var titleLabel = new JLabel(title, icon, SwingConstants.LEFT);
		titleLabel.setIconTextGap(4);
		titleLabel.setFont(FontManager.getRunescapeSmallFont());
		titleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(titleLabel, BorderLayout.CENTER);

		add(header, BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);
		applyExpanded();
	}

	public void setExpanded(boolean expanded)
	{
		this.expanded = expanded;
		applyExpanded();
		revalidate();
		repaint();
	}

	private void applyExpanded()
	{
		ImageIcons.setExpandIcon(toggleButton, expanded);
		content.setVisible(expanded);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
