package io.huze.glamourer.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class ExceptionPanel extends PluginPanel
{
	public ExceptionPanel(Exception ex)
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel message = new JLabel("<html>Glamourer has encountered an error during startup.<br><br>Please report this error on GitHub or Discord and wait for it to be fixed.<br><br>Sorry :(</html>");
		message.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		message.setBorder(new EmptyBorder(10, 10, 10, 10));
		message.setAlignmentX(LEFT_ALIGNMENT);
		contentPanel.add(message);

		StringWriter sw = new StringWriter();
		ex.printStackTrace(new PrintWriter(sw));

		JTextArea textArea = new JTextArea(sw.toString());
		textArea.setEditable(false);
		textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
		textArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		textArea.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		textArea.setCaretPosition(0);
		textArea.setBorder(new EmptyBorder(5, 5, 5, 5));

		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
		scrollPane.setAlignmentX(LEFT_ALIGNMENT);
		Dimension preferred = textArea.getPreferredSize();
		int scrollBarHeight = scrollPane.getHorizontalScrollBar().getPreferredSize().height;
		scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height + scrollBarHeight));
		contentPanel.add(scrollPane);

		add(contentPanel, BorderLayout.NORTH);
	}
}
