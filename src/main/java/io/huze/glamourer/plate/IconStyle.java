package io.huze.glamourer.plate;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum IconStyle
{
	NORMAL("Show on icons and equipment", "Show on icons"),
	WORN_ONLY("Worn equipment only; no icons", "Show on worn equipment only");

	@Getter
	final String tooltip;
	@Getter
	final String action;
}
