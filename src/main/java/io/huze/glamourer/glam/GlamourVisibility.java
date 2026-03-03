package io.huze.glamourer.glam;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum GlamourVisibility
{
	/// Unspecified visibility (no icon)
	UNSPECIFIED("Unspecified"),
	/// Glamour is disabled and completely non-visible. (Grey slash eye)
	DISABLED("Disabled"),
	/// Glamour is enabled, but hidden due to conflict. (Red slash eye)
	HIDDEN("Hidden by another plate"),
	/// Glamour is visible on items, local player, and maybe others. (Green eye)
	VISIBLE("Visible"),
	/// Glamour is visible only on others. (Yellow eye)
	OTHERS("Visible only on other players");

	@Getter
	final String tooltip;
}
