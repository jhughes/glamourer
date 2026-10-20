package io.huze.glamourer.item;

import lombok.Value;

@Value
public class SearchResult
{
	DedupeItemComposition itemComposition;

	public int getId()
	{
		return itemComposition.getId();
	}

	public String getName()
	{
		return itemComposition.getMembersName();
	}
}
