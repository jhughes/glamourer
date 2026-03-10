package io.huze.glamourer.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public class DialogUtil
{

	public static JDialog showModelessDialogNearCursor(Component parent, JPanel content,
													   String title, Consumer<JDialog> onOk)
	{
		return showModelessDialogNearCursor(parent, content, title, null, onOk);
	}

	public static JDialog showModelessDialogNearCursor(Component parent, JPanel content,
													   String title, Component bottomLeft,
													   Consumer<JDialog> onOk)
	{
		JDialog dialog = new JDialog(
			SwingUtilities.windowForComponent(parent),
			title
		);
		dialog.setModalityType(Dialog.ModalityType.MODELESS);

		JPanel contentPanel = new JPanel(new BorderLayout(0, 5));
		contentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		contentPanel.add(content, BorderLayout.CENTER);

		JPanel bottomPanel = new JPanel(new BorderLayout());
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		JButton okButton = new JButton("OK");
		JButton cancelButton = new JButton("Cancel");
		okButton.addActionListener(e -> {
			onOk.accept(dialog);
			dialog.dispose();
		});
		cancelButton.addActionListener(e -> dialog.dispose());
		buttonPanel.add(okButton);
		buttonPanel.add(cancelButton);
		bottomPanel.add(buttonPanel, BorderLayout.EAST);
		if (bottomLeft != null)
		{
			bottomPanel.add(bottomLeft, BorderLayout.WEST);
		}
		contentPanel.add(bottomPanel, BorderLayout.SOUTH);

		contentPanel.setFocusable(true);
		installDefocusListeners(contentPanel, contentPanel);

		dialog.setContentPane(contentPanel);
		dialog.getRootPane().setDefaultButton(okButton);
		dialog.setResizable(false);
		dialog.pack();
		positionNearCursor(dialog);
		dialog.setVisible(true);
		return dialog;
	}

	private static void installDefocusListeners(Container container, Component focusTarget)
	{
		for (Component comp : container.getComponents())
		{
			if (!(comp instanceof JTextField) && !(comp instanceof JComboBox))
			{
				comp.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mousePressed(MouseEvent e)
					{
						focusTarget.requestFocusInWindow();
					}
				});
			}
			if (comp instanceof Container && !(comp instanceof JComboBox))
			{
				installDefocusListeners((Container) comp, focusTarget);
			}
		}
	}

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
