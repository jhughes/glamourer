package io.huze.glamourer.ui;

import io.huze.glamourer.Config;
import io.huze.glamourer.color.ColorGroupSettings;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

public class ThresholdSlidersPanel extends JPanel
{
	private static final int SLIDER_MIN = 0;
	private static final int SLIDER_MAX = 100;

	private final Config config;
	private final Runnable onSettingsChanged;

	private final JSlider hueSlider;
	private final JSlider satSlider;
	private final JSlider lumSlider;
	private final JLabel hueValue;
	private final JLabel satValue;
	private final JLabel lumValue;
	private final JCheckBox separateGrayscaleCheck;

	private boolean suppressEvents;

	public ThresholdSlidersPanel(Config config, Runnable onSettingsChanged)
	{
		this.config = config;
		this.onSettingsChanged = onSettingsChanged;

		setLayout(new GridBagLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(4, 6, 4, 6));

		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(1, 2, 1, 2);
		c.fill = GridBagConstraints.HORIZONTAL;

		double hueDist = config.colorGroupHueDist();
		double satDist = config.colorGroupSatDist();
		double lumDist = config.colorGroupLumDist();
		boolean separateGrayscale = config.colorGroupSeparateGrayscale();

		// Row 0: Hue
		hueSlider = createSlider(hueDist);
		hueValue = new JLabel();
		addSliderRow(c, 0, "Hue", hueSlider, hueValue);

		// Row 1: Saturation
		satSlider = createSlider(satDist);
		satValue = new JLabel();
		addSliderRow(c, 1, "Saturation", satSlider, satValue);

		// Row 2: Luminance
		lumSlider = createSlider(lumDist);
		lumValue = new JLabel();
		addSliderRow(c, 2, "Luminance", lumSlider, lumValue);

		// Row 3: Separate grayscale checkbox + reset button
		separateGrayscaleCheck = new JCheckBox("Separate grayscale");
		separateGrayscaleCheck.setSelected(separateGrayscale);
		separateGrayscaleCheck.setOpaque(false);
		separateGrayscaleCheck.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		separateGrayscaleCheck.setToolTipText("Keep black, white, and gray colors in their own groups, separate from colored entries.");
		separateGrayscaleCheck.addActionListener(e -> onSliderChanged());

		c.gridy = 3;
		c.gridx = 0;
		c.gridwidth = 2;
		c.weightx = 1.0;
		c.fill = GridBagConstraints.HORIZONTAL;
		add(separateGrayscaleCheck, c);
		c.gridwidth = 1;

		JPanel resetPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		resetPanel.setOpaque(false);
		JButton resetButton = new JButton();
		ImageIcons.setResetIcon(resetButton, ColorScheme.DARK_GRAY_COLOR);
		resetButton.setToolTipText("Reset to defaults");
		resetButton.addActionListener(e -> resetToDefaults());
		resetPanel.add(resetButton);

		c.gridx = 2;
		c.weightx = 0;
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.NONE;
		add(resetPanel, c);

		updateValueLabels();
	}

	private JSlider createSlider(double value)
	{
		JSlider slider = new JSlider(SLIDER_MIN, SLIDER_MAX, toSlider(value));
		slider.setOpaque(false);
		slider.addChangeListener(e -> onSliderChanged());
		return slider;
	}

	private void addSliderRow(GridBagConstraints c, int row, String name, JSlider slider, JLabel valueLabel)
	{
		JLabel nameLabel = new JLabel(name);
		nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		valueLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		c.gridy = row;

		c.gridx = 0;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		add(nameLabel, c);

		c.gridx = 1;
		c.weightx = 1.0;
		c.fill = GridBagConstraints.HORIZONTAL;
		add(slider, c);

		c.gridx = 2;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		add(valueLabel, c);
	}

	private void onSliderChanged()
	{
		if (suppressEvents)
		{
			return;
		}
		updateValueLabels();
		saveToConfig();
		onSettingsChanged.run();
	}

	private void updateValueLabels()
	{
		hueValue.setText(String.format("%.2f", fromSlider(hueSlider.getValue())));
		satValue.setText(String.format("%.2f", fromSlider(satSlider.getValue())));
		lumValue.setText(String.format("%.2f", fromSlider(lumSlider.getValue())));
	}

	private void saveToConfig()
	{
		config.setColorGroupHueDist(fromSlider(hueSlider.getValue()));
		config.setColorGroupSatDist(fromSlider(satSlider.getValue()));
		config.setColorGroupLumDist(fromSlider(lumSlider.getValue()));
		config.setColorGroupSeparateGrayscale(separateGrayscaleCheck.isSelected());
	}

	private void resetToDefaults()
	{
		suppressEvents = true;
		hueSlider.setValue(toSlider(ColorGroupSettings.DEFAULT.getMaxHueDist()));
		satSlider.setValue(toSlider(ColorGroupSettings.DEFAULT.getMaxSatDist()));
		lumSlider.setValue(toSlider(ColorGroupSettings.DEFAULT.getMaxLumDist()));
		separateGrayscaleCheck.setSelected(ColorGroupSettings.DEFAULT.isSeparateGrayscale());
		suppressEvents = false;
		updateValueLabels();
		saveToConfig();
		onSettingsChanged.run();
	}

	private static double fromSlider(int v)
	{
		return (Math.pow(10, v / 100.0) - 1) / 9.0;
	}

	private static int toSlider(double v)
	{
		return (int) Math.round(Math.log10(v * 9.0 + 1) * 100);
	}
}
