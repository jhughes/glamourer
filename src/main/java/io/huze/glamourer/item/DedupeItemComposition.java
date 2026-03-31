package io.huze.glamourer.item;

import java.util.Collection;

public interface DedupeItemComposition
{
	int getId();

	Collection<Integer> getIds();

	String getMembersName();
}
