package io.huze.glamourer.glam;

import javax.annotation.Nonnull;
import net.runelite.api.ColorTextureOverride;

public class EquipHighlightGlamour extends Glamour
{
	private final HighlightMask mask;
	private volatile GlamState highlightState;

	public EquipHighlightGlamour(Glamour source, HighlightMask mask)
	{
		super(source);
		this.mask = mask;
		this.highlightState = getHighlightState(mask, 0f);
	}

	public void setLerp(float t)
	{
		this.highlightState = getHighlightState(mask, t);
	}

	@Override
	GlamState snapshotState()
	{
		return highlightState;
	}

	@Override
	protected void applyReplacement(@Nonnull ColorTextureOverride override)
	{
		highlightState.applyReplacementTo(override);
	}
}
