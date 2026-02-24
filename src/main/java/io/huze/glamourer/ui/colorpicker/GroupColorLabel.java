package io.huze.glamourer.ui.colorpicker;

import io.huze.glamourer.color.ColorReplacement;
import java.util.List;
import lombok.Setter;

public class GroupColorLabel extends ColorLabel
{
	private final List<ColorReplacement> colorReplacements;

	@Setter
	private Runnable onRevert;

	public GroupColorLabel(String name, List<ColorReplacement> colorReplacements)
	{
		super(name, colorReplacements.get(0).getOriginal());
		this.colorReplacements = colorReplacements;
		updateColorDisplay();
	}

	@Override
	protected List<ColorReplacement> getColorReplacements()
	{
		return colorReplacements;
	}

	@Override
	protected String getDisplayText()
	{
		return colorReplacements.size() + " colors";
	}

	@Override
	protected void revertToOriginal()
	{
		if (onRevert != null)
		{
			onRevert.run();
		}
	}

	@Override
	protected void onColorChanged(short newColor)
	{
		updateColorDisplay();
		notifyColorChange(newColor);
	}

	@Override
	protected short getPickerInitialColor()
	{
		return colorReplacements.get(0).getReplacement();
	}
}
