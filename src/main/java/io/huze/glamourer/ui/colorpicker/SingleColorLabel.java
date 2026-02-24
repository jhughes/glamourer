package io.huze.glamourer.ui.colorpicker;

import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.color.Colors;
import java.util.Collections;
import java.util.List;

public class SingleColorLabel extends ColorLabel
{
	private final ColorReplacement colorReplacement;

	public SingleColorLabel(String name, ColorReplacement colorReplacement)
	{
		super(name, colorReplacement.getOriginal());
		this.colorReplacement = colorReplacement;
		updateColorDisplay();
	}

	@Override
	protected List<ColorReplacement> getColorReplacements()
	{
		return Collections.singletonList(colorReplacement);
	}

	@Override
	protected String getDisplayText()
	{
		return Colors.formatHSL(colorReplacement.getReplacement());
	}

	@Override
	protected void revertToOriginal()
	{
		if (colorReplacement.hasChanged())
		{
			colorReplacement.setReplacement(colorReplacement.getOriginal());
			updateColorDisplay();
			notifyColorChange(colorReplacement.getReplacement());
		}
	}

	@Override
	protected void onColorChanged(short newColor)
	{
		colorReplacement.setReplacement(newColor);
		updateColorDisplay();
		notifyColorChange(newColor);
	}

	@Override
	protected short getPickerInitialColor()
	{
		return colorReplacement.getReplacement();
	}
}
