package io.huze.glamourer.item;

import java.awt.image.BufferedImage;
import lombok.Value;

@Value
public class SearchResult
{
	DedupeItemComposition itemComposition;
	BufferedImage icon;

	public int getId()
	{
		return itemComposition.getId();
	}

	public String getName()
	{
		return itemComposition.getMembersName();
	}
}