package io.huze.glamourer.glam;

import lombok.Value;

@Value
class IconKey
{
	int itemId;
	ModelEdits edits;
	IconScale scale;

	static IconKey unglamoured(int itemId, IconScale scale)
	{
		return new IconKey(itemId, ModelEdits.EMPTY, scale);
	}

	@Override
	public String toString()
	{
		return itemId + ":" + edits + ":" + scale.getPercent();
	}
}
