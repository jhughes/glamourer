package io.huze.glamourer.ui;

import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.Icon;
import javax.swing.JButton;
import net.runelite.client.ui.ColorScheme;

/// This class is purely visual; it's a regular button with internal state that shows whether it's active or not.
/// State should be maintained somewhere else.
public class ToggleButton extends JButton
{
	@Override
	protected void paintComponent(Graphics g)
	{
		if (Boolean.TRUE.equals(getClientProperty("active")))
		{
			g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
			g.fillRect(0, 0, getWidth(), getHeight());
		}
		Icon icon = getModel().isRollover() && getRolloverIcon() != null ? getRolloverIcon() : getIcon();
		if (icon != null)
		{
			Insets insets = getInsets();
			icon.paintIcon(this, g,
				insets.left + (getWidth() - insets.left - insets.right - icon.getIconWidth()) / 2,
				insets.top + (getHeight() - insets.top - insets.bottom - icon.getIconHeight()) / 2);
		}
	}

	public void setActive(boolean active)
	{
		putClientProperty("active", active);
		repaint();
	}
}
