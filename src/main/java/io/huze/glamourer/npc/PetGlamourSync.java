package io.huze.glamourer.npc;

import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.glam.GlamourData;
import io.huze.glamourer.glam.GlamourEngine;
import io.huze.glamourer.item.PetSheet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class PetGlamourSync
{
	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;
	private final GlamourEngine glamourEngine;
	private final NpcGlamourer npcGlamourer;
	private final PetSheet petSheet;
	private final FollowerTracker followerTracker;
	private final HouseTracker houseTracker;

	/// Member pet glamours keyed by party member id. Client thread only.
	private final Map<Long, PlayerPetGlamours> partyMembers = new HashMap<>();

	/// Is NPC ID a non-follower pet (PoH menagerie). Client thread only.
	private final Map<Integer, Boolean> nonFollowerPetByNpcId = new HashMap<>();

	/// Is end-of-tick glamour sync queued. Client thread only.
	private boolean syncQueued;

	public void startUp()
	{
		eventBus.register(houseTracker);
		eventBus.register(followerTracker);
		eventBus.register(this);
		followerTracker.start();
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		eventBus.unregister(followerTracker);
		eventBus.unregister(houseTracker);
	}

	/// Client thread only.
	public void updatePartyMember(long memberId, String standardizedName,
		Map<Integer, GlamourData> itemGlamours, int followerViewId, int followerIndex)
	{
		partyMembers.put(memberId,
			new PlayerPetGlamours(standardizedName, filterNonPetGlamours(itemGlamours), followerViewId, followerIndex));
		queueSync();
	}

	/// Client thread only.
	public void removePartyMember(long memberId)
	{
		if (partyMembers.remove(memberId) != null)
		{
			queueSync();
		}
	}

	/// Client thread only.
	public void clearPartyMembers()
	{
		if (!partyMembers.isEmpty())
		{
			partyMembers.clear();
			queueSync();
		}
	}

	/// Glamours for visited PoH owner's Menagerie pets
	/// We don't actually know what pets are in their menagerie, so we must return all pet glamours.
	private Map<Integer, NpcGlamour> getVisitedOwnerGlamours()
	{
		String houseOwner = houseTracker.getCurrentOwnerName();
		if (houseOwner != null)
		{
			for (PlayerPetGlamours member : partyMembers.values())
			{
				if (houseOwner.equals(member.standardizedName))
				{
					return member.petGlamours;
				}
			}
		}
		return Map.of();
	}

	private Map<Integer, NpcGlamour> filterNonPetGlamours(Map<Integer, GlamourData> itemGlamours)
	{
		Map<Integer, List<Integer>> pets = petSheet.getNpcsByItemId();
		Map<Integer, NpcGlamour> glamours = new HashMap<>();
		itemGlamours.forEach((itemId, data) -> {
			if (pets.containsKey(itemId))
			{
				NpcGlamour glamour = NpcGlamour.of(data.getColorReplacements(), data.getTextureReplacements());
				if (!glamour.isEmpty())
				{
					glamours.put(itemId, glamour);
				}
			}
		});
		return glamours;
	}

	public void sync()
	{
		var petItemsToNpcIds = petSheet.getNpcsByItemId();
		Map<Integer, NpcGlamour> ours = getOurGlamours(petItemsToNpcIds.keySet());
		syncKinds(petItemsToNpcIds, ours);
		syncInstances(petItemsToNpcIds, ours);
	}

	private Map<Integer, NpcGlamour> getOurGlamours(Set<Integer> itemIds)
	{
		Map<Integer, NpcGlamour> glamours = new HashMap<>();
		for (GlamourData data : glamourEngine.getGlamourSnapshotForItems(itemIds))
		{
			Integer itemId = data.getItemId();
			if (itemId != null)
			{
				glamours.put(itemId, NpcGlamour.of(data.getColorReplacements(), data.getTextureReplacements()));
			}
		}
		return glamours;
	}

	private void syncKinds(Map<Integer, List<Integer>> petItemsToNpcIds, Map<Integer, NpcGlamour> ourGlamours)
	{
		final var ownerGlamours = getVisitedOwnerGlamours();
		for (var petItemsToNpcId : petItemsToNpcIds.entrySet())
		{
			final int itemId = petItemsToNpcId.getKey();
			NpcGlamour ours = ourGlamours.getOrDefault(itemId, NpcGlamour.empty());
			NpcGlamour placed = ownerGlamours.getOrDefault(itemId, ours);
			NpcGlamour follower = glamourEngine.isGlobal(itemId) ? ours : NpcGlamour.empty();
			for (int npcId : petItemsToNpcId.getValue())
			{
				NpcGlamour kindGlamour = placed == follower
					? placed
					: isNonFollowerPet(npcId) ? placed : follower;
				npcGlamourer.setKindGlamour(npcId, kindGlamour);
			}
		}
	}

	private void syncInstances(Map<Integer, List<Integer>> petItemsToNpcIds, Map<Integer, NpcGlamour> ourGlamours)
	{
		final int ourViewId = followerTracker.getFollowerViewId();
		final int ourIndex = followerTracker.getFollowerIndex();

		Map<NpcInstanceKey, NpcGlamour> instances = new HashMap<>();
		for (PlayerPetGlamours member : partyMembers.values())
		{
			if (member.followerIndex == -1
				|| (member.followerIndex == ourIndex && member.followerViewId == ourViewId))
			{
				continue;
			}
			member.petGlamours.forEach((itemId, glamour) ->
				addInstance(instances, member.followerViewId, member.followerIndex,
					petItemsToNpcIds.getOrDefault(itemId, List.of()), glamour));
		}

		if (ourIndex != -1)
		{
			final int itemId = petSheet.getItemId(followerTracker.getFollowerId());
			NpcGlamour ours = itemId == -1 || glamourEngine.isGlobal(itemId)
				? NpcGlamour.empty()
				: ourGlamours.getOrDefault(itemId, NpcGlamour.empty());
			addInstance(instances, ourViewId, ourIndex, petItemsToNpcIds.getOrDefault(itemId, List.of()), ours);
		}

		npcGlamourer.setInstanceGlamours(instances);
	}

	private static void addInstance(Map<NpcInstanceKey, NpcGlamour> instances, int viewId,
		int npcIndex, List<Integer> npcIds, NpcGlamour glamour)
	{
		if (glamour.isEmpty())
		{
			return;
		}
		for (int npcId : npcIds)
		{
			instances.put(new NpcInstanceKey(viewId, npcIndex, npcId), glamour);
		}
	}

	private boolean isNonFollowerPet(int npcId)
	{
		return nonFollowerPetByNpcId.computeIfAbsent(npcId, id -> {
			NPCComposition composition = client.getNpcDefinition(id);
			return composition != null && !composition.isFollower();
		});
	}

	public void queueSync()
	{
		if (syncQueued)
		{
			return;
		}
		syncQueued = true;
		clientThread.invokeAtTickEnd(() -> {
			syncQueued = false;
			sync();
		});
	}

	private void queueSyncFor(NPC npc)
	{
		if (petSheet.getItemId(npc.getId()) != -1 && isNonFollowerPet(npc.getId()))
		{
			queueSync();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		queueSyncFor(event.getNpc());
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		queueSyncFor(event.getNpc());
	}

	@Subscribe
	public void onFollowerChanged(FollowerChanged event)
	{
		// Immediate sync instead of queued to prevent color pop-in
		sync();
	}

	public void setHighlight(@Nullable Glamour highlight)
	{
		clientThread.invoke(() -> applyHighlight(highlight));
	}

	/// Client thread only.
	private void applyHighlight(@Nullable Glamour highlight)
	{
		if (highlight == null || !previewOnPet(highlight))
		{
			// Ended, not a pet, or nothing to land on.
			npcGlamourer.clearHighlight();
		}
	}

	/// Client thread only.
	private boolean previewOnPet(Glamour highlight)
	{
		for (int itemId : highlight.getItemIds())
		{
			List<Integer> npcIds = petSheet.getNpcIds(itemId);
			if (npcIds.isEmpty())
			{
				continue;
			}
			NpcGlamour displayed = NpcGlamour.of(highlight.getDisplayedColorReplacements(),
				highlight.getTextureReplacements());
			if (glamourEngine.isGlobal(itemId))
			{
				npcGlamourer.setHighlight(npcIds, displayed);
				return true;
			}

			if (petSheet.getItemId(followerTracker.getFollowerId()) == itemId)
			{
				npcGlamourer.setHighlight(npcIds, followerTracker.getFollowerViewId(),
					followerTracker.getFollowerIndex(), displayed);
				return true;
			}
			if (getVisitedOwnerGlamours().containsKey(itemId))
			{
				return false;
			}
			List<Integer> placed = new ArrayList<>();
			for (int npcId : npcIds)
			{
				if (isNonFollowerPet(npcId))
				{
					placed.add(npcId);
				}
			}
			if (placed.isEmpty())
			{
				return false;
			}
			npcGlamourer.setHighlight(placed, displayed);
			return true;
		}
		return false;
	}
}
