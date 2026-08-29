package io.huze.glamourer.npc;

class NpcInstanceKey
{
	private final int viewId;
	private final int npcIndex;
	private final int npcId;

	NpcInstanceKey(int viewId, int npcIndex, int npcId)
	{
		this.viewId = viewId;
		this.npcIndex = npcIndex;
		this.npcId = npcId;
	}

	@Override
	public boolean equals(Object other)
	{
		if (!(other instanceof NpcInstanceKey))
		{
			return false;
		}
		NpcInstanceKey key = (NpcInstanceKey) other;
		return viewId == key.viewId && npcIndex == key.npcIndex && npcId == key.npcId;
	}

	@Override
	public int hashCode()
	{
		return 31 * (31 * viewId + npcIndex) + npcId;
	}
}
