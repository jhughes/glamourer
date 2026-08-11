package io.huze.glamourer.glam;

import io.huze.glamourer.color.Colors;
import javax.annotation.Nonnull;
import net.runelite.api.ColorTextureOverride;

public class EquipHighlightGlamour extends Glamour
{
	private final HighlightMask mask;
	private final GlamState highlightState;

	public EquipHighlightGlamour(Glamour source, HighlightMask mask, float lerp)
	{
		super(source);
		this.mask = mask;
		this.highlightState = source.staged.deepCopy(false);
		setLerp(lerp);
	}

	public void setLerp(float t)
	{
		for (int i = 0; i < highlightState.colorLength; i++)
		{
			var color = super.staged.getColor(i);
			if (mask.getColorIndices().contains(i))
			{
				highlightState.replaceColor(i, Colors.lerpHsl(color, Colors.highlight(color), t));
			}
			else
			{
				highlightState.replaceColor(i, Colors.darken(color));
			}
		}
	}

	@Override
	GlamState snapshotState()
	{
		// Intentionally break the Glamour snapshotState immutability rule to avoid state copying every frame
		return highlightState;
	}

	@Override
	protected void applyReplacement(@Nonnull ColorTextureOverride override)
	{
		highlightState.applyTo(override);
	}
}
