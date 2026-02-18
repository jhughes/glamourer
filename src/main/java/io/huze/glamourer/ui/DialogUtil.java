package io.huze.glamourer.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

public class DialogUtil
{

	public static int showConfirmDialogNearCursor(Component parent, Object message, String title,
												  int optionType, int messageType)
	{
		JOptionPane pane = new JOptionPane(message, messageType, optionType);
		JDialog dialog = pane.createDialog(parent, title);
		positionNearCursor(dialog);
		dialog.setVisible(true);

		Object selectedValue = pane.getValue();
		if (selectedValue == null)
		{
			return JOptionPane.CLOSED_OPTION;
		}
		if (selectedValue instanceof Integer)
		{
			return (Integer) selectedValue;
		}
		return JOptionPane.CLOSED_OPTION;
	}

	public static void showErrorDialogNearCursor(Component parent, Object message, String title)
	{
		JOptionPane pane = new JOptionPane(message, JOptionPane.ERROR_MESSAGE, JOptionPane.DEFAULT_OPTION);
		JDialog dialog = pane.createDialog(parent, title);
		positionNearCursor(dialog);
		dialog.setVisible(true);
	}


	public static void positionNearCursor(Window dialog)
	{
		Point mousePos = MouseInfo.getPointerInfo().getLocation();
		Dimension dialogSize = dialog.getSize();

		// Get screen bounds
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		Rectangle screenBounds = ge.getDefaultScreenDevice().getDefaultConfiguration().getBounds();

		// Try to find the screen the mouse is on
		for (GraphicsDevice gd : ge.getScreenDevices())
		{
			Rectangle bounds = gd.getDefaultConfiguration().getBounds();
			if (bounds.contains(mousePos))
			{
				screenBounds = bounds;
				break;
			}
		}

		// Position dialog with small offset from cursor
		int x = mousePos.x + 10;
		int y = mousePos.y + 10;

		// Ensure dialog stays within screen bounds
		if (x + dialogSize.width > screenBounds.x + screenBounds.width)
		{
			x = mousePos.x - dialogSize.width - 10;
		}
		if (y + dialogSize.height > screenBounds.y + screenBounds.height)
		{
			y = mousePos.y - dialogSize.height - 10;
		}
		if (x < screenBounds.x)
		{
			x = screenBounds.x;
		}
		if (y < screenBounds.y)
		{
			y = screenBounds.y;
		}

		dialog.setLocation(x, y);
	}
}
