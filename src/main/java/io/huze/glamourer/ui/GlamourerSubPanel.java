package io.huze.glamourer.ui;

import javax.annotation.Nullable;
import javax.swing.JPanel;

public interface GlamourerSubPanel
{
	String getCardKey();

	@Nullable
	default JPanel getToolbarButtons()
	{
		return null;
	}

	@Nullable
	default JPanel getSubHeaderPanel()
	{
		return null;
	}

	default void onActivate()
	{
	}
}
