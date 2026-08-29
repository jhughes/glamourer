package io.huze.glamourer.npc;

import lombok.Value;

@Value
public class FollowerChanged
{
	int viewId;
	int index;
	int oldViewId;
	int oldIndex;
}
