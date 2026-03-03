package io.huze.glamourer.plate;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum DisplayStyle
{
	/// Items + local player equipment
	LOCAL("Visible on Self only", "Show on self only"),
	/// Items + all player equipment
	GLOBAL("Visible on Everyone", "Show on everyone");

	@Getter
	final String tooltip;
	@Getter
	final String action;
}
