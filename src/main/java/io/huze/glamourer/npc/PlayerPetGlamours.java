package io.huze.glamourer.npc;

import java.util.Map;
import javax.annotation.Nullable;

final class PlayerPetGlamours
{
	final String standardizedName;
	/// Pet glamours by item id
	final Map<Integer, NpcGlamour> petGlamours;
	final int followerViewId;
	final int followerIndex;

	PlayerPetGlamours(String standardizedName, Map<Integer, NpcGlamour> petGlamours,
		int followerViewId, int followerIndex)
	{
		this.standardizedName = standardizedName;
		this.petGlamours = petGlamours;
		this.followerViewId = followerViewId;
		this.followerIndex = followerIndex;
	}
}
