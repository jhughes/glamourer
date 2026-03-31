package io.huze.glamourer.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

class ExceptionPanel extends JPanel implements GlamourerSubPanel
{
	ExceptionPanel(Exception ex)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel message = new JLabel("<html>Glamourer encountered a critical error.<br><br>Please report this error on Discord or GitHub and wait for it to be fixed.<br><br>Sorry :(</html>");
		message.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		message.setBorder(new EmptyBorder(10, 10, 10, 10));
		message.setAlignmentX(LEFT_ALIGNMENT);
		contentPanel.add(message);

		StringWriter sw = new StringWriter();
		ex.printStackTrace(new PrintWriter(sw));
		final var stackTrace = sw.toString();

		JTextArea textArea = new JTextArea(stackTrace);
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

		JButton copyButton = new JButton("Copy to Clipboard");
		copyButton.setAlignmentX(LEFT_ALIGNMENT);
		copyButton.addActionListener(e -> {
			Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(stackTrace), null);
			copyButton.setText("Copied!");
		});
		contentPanel.add(copyButton);

		add(contentPanel, BorderLayout.NORTH);
	}

	@Override
	public String getCardKey()
	{
		return "ERROR";
	}
}
