package io.huze.glamourer.ui.colorpicker;

import io.huze.glamourer.color.Colors;
import io.huze.glamourer.ui.ImageIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.border.Border;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.JagexColor;

public class HslColorPicker extends JPanel
{
	private final JSlider hueSlider;
	private final JSlider satSlider;
	private final JSlider lumSlider;
	private final JTextField hueText;
	private final JTextField satText;
	private final JTextField lumText;
	private final JLabel colorPreview = new JLabel();
	private final Color[] satColors = new Color[Colors.MAX_SAT + 1];
	private final Color[] lumColors = new Color[Colors.MAX_LUM + 1];
	private final JTextField hslField = new JTextField("0", 6);
	private static final Border INVALID_BORDER = BorderFactory.createLineBorder(Color.RED);
	private static final Border VALID_BORDER = new JTextField().getBorder();

	public HslColorPicker(final short original, final short previous)
	{
		setLayout(new BorderLayout(10, 10));

		hueSlider = createGradientSlider(Colors.MAX_HUE, getHueSpectrum());
		satSlider = createGradientSlider(Colors.MAX_SAT, satColors);
		lumSlider = createGradientSlider(Colors.MAX_LUM, lumColors);
		updateGradients();
		hueText = createColorTextField(Colors.MAX_HUE, hueSlider);
		satText = createColorTextField(Colors.MAX_SAT, satSlider);
		lumText = createColorTextField(Colors.MAX_LUM, lumSlider);

		JPanel previewPanel = new JPanel();
		previewPanel.setLayout(new BoxLayout(previewPanel, BoxLayout.Y_AXIS));

		colorPreview.setAlignmentX(Component.CENTER_ALIGNMENT);
		colorPreview.setPreferredSize(new Dimension(100, 50));
		colorPreview.setMaximumSize(new Dimension(100, 50));
		colorPreview.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		colorPreview.setOpaque(true);
		colorPreview.setToolTipText("Current color");
		previewPanel.add(colorPreview);

		previewPanel.add(Box.createVerticalStrut(2));
		previewPanel.add(createColorButton(previous, "Restore previous color"));
		previewPanel.add(Box.createVerticalStrut(2));
		previewPanel.add(createColorButton(original, "Restore original color"));
		previewPanel.add(Box.createVerticalGlue());

		JPanel controlsPanel = new JPanel();
		controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
		controlsPanel.add(createSliderRow("Hue:", "Hue (color tone)\n[0, " + Colors.MAX_HUE + "]", hueSlider, hueText));
		controlsPanel.add(Box.createVerticalStrut(2));
		controlsPanel.add(createSliderRow("Sat:", "Saturation (color intensity)\n[0, " + Colors.MAX_SAT + "]", satSlider, satText));
		controlsPanel.add(Box.createVerticalStrut(2));
		controlsPanel.add(createSliderRow("Lum:", "Luminance (lightness)\n[0, " + Colors.MAX_LUM + "]", lumSlider, lumText));
		controlsPanel.add(Box.createVerticalStrut(5));
		JPanel exportWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		exportWrapper.add(createExportRow("HSL:", "Jagex Color composed from Hue, Sat, and Lum components\n[" + Short.MIN_VALUE + ", " + Short.MAX_VALUE + "]", hslField));
		exportWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, exportWrapper.getPreferredSize().height));
		controlsPanel.add(exportWrapper);

		add(previewPanel, BorderLayout.WEST);
		add(controlsPanel, BorderLayout.CENTER);

		hslField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				hslField.setText(String.valueOf(getColor()));
				validateField(hslField, true);
			}
		});
		hslField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e) { applyHslField(); }

			@Override
			public void removeUpdate(DocumentEvent e) { applyHslField(); }

			@Override
			public void changedUpdate(DocumentEvent e) { applyHslField(); }

			private void applyHslField()
			{
				if (!hslField.isFocusOwner()) return;
				String text = hslField.getText();
				try
				{
					int val = Integer.parseInt(text);
					validateField(hslField, val >= Short.MIN_VALUE && val <= Short.MAX_VALUE);
					short clamped = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, val));
					SwingUtilities.invokeLater(() -> setColor(clamped));
				}
				catch (NumberFormatException ignored)
				{
					validateField(hslField, false);
				}
			}
		});

		setColor(previous);

		hueSlider.addChangeListener(this::onUpdate);
		satSlider.addChangeListener(this::onUpdate);
		lumSlider.addChangeListener(this::onUpdate);
		onUpdate(null);

		SwingUtilities.invokeLater(() -> {
			hslField.requestFocusInWindow();
			hslField.selectAll();
		});
	}

	public void setColor(short hsl)
	{
		hueSlider.setValue(JagexColor.unpackHue(hsl));
		satSlider.setValue(JagexColor.unpackSaturation(hsl));
		lumSlider.setValue(JagexColor.unpackLuminance(hsl));
	}

	public short getColor()
	{
		return JagexColor.packHSL(hueSlider.getValue(), satSlider.getValue(), lumSlider.getValue());
	}

	private void onUpdate(ChangeEvent e)
	{
		short hsl = getColor();
		Color c = Colors.hslToColor(hsl);
		colorPreview.setBackground(c);

		if (!hslField.isFocusOwner())
		{
			hslField.setText(String.valueOf(hsl));
		}
		if (!hueText.isFocusOwner())
		{
			hueText.setText(String.valueOf(hueSlider.getValue()));
		}
		if (!satText.isFocusOwner())
		{
			satText.setText(String.valueOf(satSlider.getValue()));
		}
		if (!lumText.isFocusOwner())
		{
			lumText.setText(String.valueOf(lumSlider.getValue()));
		}

		updateGradients();
		repaint();
	}

	private static void validateField(JTextField field, boolean valid)
	{
		field.setBorder(valid ? VALID_BORDER : INVALID_BORDER);
	}

	private static Color[] getHueSpectrum()
	{
		Color[] colors = new Color[Colors.MAX_HUE + 1];
		for (int i = 0; i <= Colors.MAX_HUE; i++)
		{
			colors[i] = Colors.hslToColor(i, Colors.MAX_SAT, Colors.MAX_LUM / 2);
		}
		return colors;
	}

	private void updateGradients()
	{
		int h = hueSlider.getValue();
		int s = satSlider.getValue();
		int l = lumSlider.getValue();
		for (int i = 0; i <= Colors.MAX_SAT; i++)
		{
			satColors[i] = Colors.hslToColor(h, i, l);
		}
		for (int i = 0; i <= Colors.MAX_LUM; i++)
		{
			lumColors[i] = Colors.hslToColor(h, s, i);
		}
		((ColorSliderUI) satSlider.getUI()).setColors(satColors);
		((ColorSliderUI) lumSlider.getUI()).setColors(lumColors);
	}

	private JTextField createColorTextField(int max, JSlider slider)
	{
		JTextField field = new JTextField("0", 3);
		field.setHorizontalAlignment(JTextField.CENTER);
		field.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				field.setText(String.valueOf(slider.getValue()));
				validateField(field, true);
			}
		});
		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e) { apply(); }

			@Override
			public void removeUpdate(DocumentEvent e) { apply(); }

			@Override
			public void changedUpdate(DocumentEvent e) { apply(); }

			private void apply()
			{
				if (!field.isFocusOwner()) return;
				String text = field.getText();
				try
				{
					int val = Integer.parseInt(text);
					validateField(field, val >= 0 && val <= max);
					SwingUtilities.invokeLater(() ->
						slider.setValue(Math.max(0, Math.min(max, Integer.parseInt(text))))
					);
				}
				catch (NumberFormatException ignored)
				{
					validateField(field, false);
				}
			}
		});
		return field;
	}

	private JSlider createGradientSlider(int max, Color[] colors)
	{
		JSlider slider = new JSlider(0, max);
		slider.setPreferredSize(new Dimension(Colors.MAX_LUM * 2, slider.getPreferredSize().height));
		slider.setUI(new ColorSliderUI(slider, colors));
		return slider;
	}

	private JPanel createSliderRow(String labelText, String tooltip, JSlider slider, JTextField field)
	{
		JPanel row = new JPanel(new BorderLayout(2, 0));
		JLabel label = new JLabel(labelText);
		label.setToolTipText(tooltip);
		label.setPreferredSize(new Dimension(25, 0));
		row.add(label, BorderLayout.WEST);
		row.add(slider, BorderLayout.CENTER);
		row.add(field, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private JPanel createExportRow(String labelText, String tooltip, JTextField field)
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		JLabel label = new JLabel(labelText);
		label.setToolTipText(tooltip);
		panel.add(label);
		panel.add(field);

		JButton copyBtn = new JButton();
		ImageIcons.setCopyIcon(copyBtn);
		copyBtn.setToolTipText("Copy to clipboard");
		copyBtn.addActionListener(e -> copyToClipboard(field.getText()));
		panel.add(copyBtn);

		return panel;
	}

	private void copyToClipboard(String text)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard()
			.setContents(new StringSelection(text), null);
	}

	private JButton createColorButton(short hsl, String tooltip)
	{
		Color color = Colors.hslToColor(hsl);
		JButton btn = new JButton()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				g.setColor(color);
				g.fillRect(0, 0, getWidth(), getHeight());
				super.paintComponent(g);
			}
		};
		btn.setAlignmentX(Component.CENTER_ALIGNMENT);
		btn.setPreferredSize(new Dimension(100, 25));
		btn.setMaximumSize(new Dimension(100, 25));
		btn.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		btn.setContentAreaFilled(false);
		btn.setToolTipText(tooltip);
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.addActionListener(e -> setColor(hsl));
		return btn;
	}
}