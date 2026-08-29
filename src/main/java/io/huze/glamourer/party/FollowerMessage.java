package io.huze.glamourer.party;

import lombok.AllArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

@AllArgsConstructor
public class FollowerMessage extends PartyMemberMessage
{
	final int world;
	/// Follower's world view ID
	final int viewId;
	/// Follower NPC's index within the world view; -1 with no follower.
	final int index;
}
