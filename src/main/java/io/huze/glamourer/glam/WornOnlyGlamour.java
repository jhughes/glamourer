package io.huze.glamourer.glam;

import net.runelite.api.ItemComposition;

public class WornOnlyGlamour extends Glamour
{
	protected final Glamour source;

	public WornOnlyGlamour(Glamour source)
	{
		super(source);
		this.source = source;
	}

	@Override
	protected void apply(ItemComposition itemComposition)
	{
		staged.applyOriginalTo(itemComposition);
	}
}
