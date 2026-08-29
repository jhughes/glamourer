package io.huze.glamourer.party;

import io.huze.glamourer.Config;
import io.huze.glamourer.Extensions;
import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.glam.GlamourData;
import io.huze.glamourer.glam.GlamourEngine;
import io.huze.glamourer.item.DedupeItemManager;
import io.huze.glamourer.item.PetSheet;
import io.huze.glamourer.npc.FollowerChanged;
import io.huze.glamourer.npc.FollowerTracker;
import io.huze.glamourer.npc.PetGlamourSync;
import io.huze.glamourer.plate.GlamourBinaryCodec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.PartyMemberMessage;
import net.runelite.client.party.messages.UserSync;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.party.PartyPlugin;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
@ExtensionMethod({Extensions.class})
public class PartyInterface
{
	private static final String UNKNOWN_NAME = "<unknown>";
	@Inject
	Client client;
	@Inject
	PartyService partyService;
	@Inject
	WSClient wsClient;
	@Inject
	ClientThread clientThread;
	@Inject
	GlamourEngine glamourEngine;
	@Inject
	DedupeItemManager ddItemManager;
	@Inject
	Config config;
	@Inject
	PlayerBlacklist blacklist;
	@Inject
	PluginManager pluginManager;
	@Inject
	PetSheet petSheet;
	@Inject
	PetGlamourSync petGlamourSync;
	@Inject
	FollowerTracker followerTracker;

	private final Map<Long, PartyMember> partyMembers = new ConcurrentHashMap<>();
	/// Client thread only.
	private int lastSentFollowerIndex = -1;
	private Set<Integer> syncedItemIds = new HashSet<>();
	private volatile boolean isStarted;
	private volatile boolean updateQueued;

	@Setter
	@Nonnull
	private Runnable onStateChanged = () -> {};

	// --- Data classes ---

	@RequiredArgsConstructor
	public static final class PartyMemberInfo
	{
		public final long memberId;
		@Nonnull
		public final String displayName;
		public final boolean hasGlamourer;

		public boolean hasName()
		{
			return !UNKNOWN_NAME.equals(displayName);
		}
	}

	final class PartyMember
	{
		public final long id;
		@Nonnull
		volatile String lastKnownName = UNKNOWN_NAME;
		/// Client thread only
		@Nonnull
		String standardizedName = UNKNOWN_NAME;
		/// Synced worn and pet item glamours. Client thread only.
		@Nonnull
		public final Map<Integer, GlamourData> itemIdToGlamour = new LinkedHashMap<>();
		/// Last broadcast follower. Client thread only.
		private int followerWorld = -1;
		private int followerViewId = -1;
		private int followerIndex = -1;

		PartyMember(long memberId)
		{
			this.id = memberId;
		}

		private boolean hasName()
		{
			return !UNKNOWN_NAME.equals(standardizedName);
		}

		void handleGlamourMessage(GlamourMessage msg)
		{
			List<GlamourData> glamourData;
			try
			{
				glamourData = GlamourBinaryCodec.decodeList(msg.data);
			}
			catch (IOException e)
			{
				log.warn("[PartySync] failed to decode glamour message from member {}", msg.getMemberId(), e);
				return;
			}

			clientThread.invoke(() -> {
				if (!msg.patch)
				{
					itemIdToGlamour.clear();
				}
				glamourData.forEach(g -> itemIdToGlamour.put(g.getItemId(), g));
				reapply();
				notifyStateChanged();
			});
		}

		void handleFollowerMessage(FollowerMessage msg)
		{
			clientThread.invoke(() -> {
				followerWorld = msg.world;
				followerViewId = msg.viewId;
				followerIndex = msg.index;
				reapply();
			});
		}

		void maybeUpdateName(@Nullable String newName)
		{
			newName = newName != null ? newName : UNKNOWN_NAME;
			if (!lastKnownName.equals(newName))
			{
				log.debug("[PartySync] member {} name changed: '{}' -> '{}'", id, lastKnownName, newName);
				removeFromParty();
				lastKnownName = newName;
				standardizedName = UNKNOWN_NAME.equals(newName) ? UNKNOWN_NAME : Text.standardize(newName);
				reapply();
				notifyStateChanged();
			}
		}

		void removeFromParty()
		{
			petGlamourSync.removePartyMember(id);
			if (hasName())
			{
				glamourEngine.removePlayerGlamour(standardizedName);
			}
		}

		private void reapply()
		{
			if (hasName())
			{
				if (isEligible())
				{
					glamourEngine.updatePlayerGlamour(standardizedName, itemIdToGlamour);
				}
				else
				{
					glamourEngine.removePlayerGlamour(standardizedName);
				}
			}
			if (isEligible() && !itemIdToGlamour.isEmpty())
			{
				final boolean sameWorld = followerWorld == client.getWorld();
				petGlamourSync.updatePartyMember(id,
					standardizedName,
					itemIdToGlamour,
					sameWorld ? followerViewId : -1,
					sameWorld ? followerIndex : -1);
			}
			else
			{
				petGlamourSync.removePartyMember(id);
			}
		}

		private boolean isEligible()
		{
			return config.partySyncReceive() && !blacklist.contains(lastKnownName);
		}
	}

	public static final class RequestUpdateMessage extends PartyMemberMessage
	{
	}

	public static final class LeaveMessage extends PartyMemberMessage
	{
	}

	// --- Lifecycle ---

	public boolean isPartyPluginActive()
	{
		try
		{
			var plugin = pluginManager.getPlugins().stream()
				.filter(p -> p.getClass() == PartyPlugin.class)
				.findFirst()
				.orElse(null);
			return pluginManager.isPluginActive(plugin);
		}
		catch (Exception | AssertionError e)
		{
			return false;
		}
	}

	public void startUp()
	{
		log.debug("[PartySync] startUp");
		isStarted = true;
		wsClient.registerMessage(RequestUpdateMessage.class);
		wsClient.registerMessage(LeaveMessage.class);
		wsClient.registerMessage(GlamourMessage.class);
		wsClient.registerMessage(FollowerMessage.class);

		if (partyService.isInParty())
		{
			requestUpdate();
			sendUpdate();
		}
	}

	public void shutDown()
	{
		log.debug("[PartySync] shutDown");
		isStarted = false;
		if (partyService.isInParty())
		{
			partyService.send(new LeaveMessage());
		}
		partyLeave();

		wsClient.unregisterMessage(RequestUpdateMessage.class);
		wsClient.unregisterMessage(LeaveMessage.class);
		wsClient.unregisterMessage(GlamourMessage.class);
		wsClient.unregisterMessage(FollowerMessage.class);
	}

	// --- Event handlers ---

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		if (event.getPartyId() != null)
		{
			log.debug("[PartySync] joined party {}", event.getPartyId());
			if (!config.partySyncPrompted())
			{
				promptEnableSync();
				config.setPartySyncPrompted(true);
			}
		}
		else
		{
			log.debug("[PartySync] left party");
			partyLeave();
		}
		notifyStateChanged();
	}

	private void promptEnableSync()
	{
		boolean fullyDisabled = !config.partySyncReceive() && !config.partySyncPrompted();
		if (fullyDisabled)
		{
			SwingUtilities.invokeLater(() -> {
				int result = JOptionPane.showConfirmDialog(
					null,
					"Would you like to enable Glamourer Party Sync?\n"
						+ "Your party members will see your glamours and you will see theirs.\n"
						+ "This can be changed at any time in the settings.",
					"Glamourer Party Sync",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.QUESTION_MESSAGE
				);
				if (result == JOptionPane.YES_OPTION)
				{
					config.setPartySyncSend(true);
					config.setPartySyncReceive(true);
				}
			});
		}
	}

	@Subscribe
	public void onUserSync(UserSync event)
	{
		log.debug("[PartySync] user sync from member {}", event.getMemberId());
		updateQueued = true;
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		log.debug("[PartySync] user parted: member {}", event.getMemberId());
		removeMember(event.getMemberId());
		notifyStateChanged();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() -> {
				if (client.getLocalPlayer() == null)
				{
					return false;
				}
				sendUpdate();
				partyMembers.values().forEach(PartyMember::reapply);
				return true;
			});
		}
	}

	@Subscribe
	public void onFollowerChanged(FollowerChanged event)
	{
		sendFollower(event.getViewId(), event.getIndex());
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!isStarted || !partyService.isInParty())
		{
			return;
		}

		if (updateQueued)
		{
			updateQueued = false;
			sendUpdate();
		}

		Set<Long> currentMemberIds = new HashSet<>();
		for (var member : partyService.getMembers())
		{
			currentMemberIds.add(member.getMemberId());
		}
		var it = partyMembers.entrySet().iterator();
		while (it.hasNext())
		{
			var entry = it.next();
			var member = entry.getValue();
			if (currentMemberIds.contains(entry.getKey()))
			{
				member.maybeUpdateName(getMemberDisplayName(member.id));
			}
			else
			{
				log.debug("[PartySync] cleaning up stale member {}", entry.getKey());
				it.remove();
				member.removeFromParty();
				notifyStateChanged();
			}
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV || !shouldSendUpdates())
		{
			return;
		}

		Set<Integer> currentIds = client.getLocalPlayerEquippableItemIds(ddItemManager);
		Set<Integer> newIds = new HashSet<>(currentIds);
		newIds.removeAll(syncedItemIds);
		syncedItemIds = currentIds;

		if (newIds.isEmpty())
		{
			return;
		}

		List<GlamourData> patch = glamourEngine.getGlamourSnapshotForItems(newIds);
		if (!patch.isEmpty())
		{
			try
			{
				log.debug("[PartySync] sending patch: {} new glamours for {} new equippable items", patch.size(), newIds.size());
				partyService.send(GlamourMessage.patch(patch));
			}
			catch (IOException e)
			{
				log.warn("[PartySync] failed to encode inventory patch", e);
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(Config.GROUP))
		{
			return;
		}
		switch (event.getKey())
		{
			case Config.KEY_PARTY_SYNC_SEND:
				if (config.partySyncSend())
				{
					sendUpdate();
				}
				else
				{
					sendRevoke();
				}
				break;
			case Config.KEY_PARTY_SYNC_RECV:
				clientThread.invoke(() -> partyMembers.values().forEach(PartyMember::reapply));
				break;
		}
	}

	@Subscribe
	public void onRequestUpdateMessage(RequestUpdateMessage msg)
	{
		if (isSelf(msg))
		{
			return;
		}
		log.debug("[PartySync] received RequestUpdateMessage from member {}", msg.getMemberId());
		sendUpdate();
	}

	@Subscribe
	public void onLeaveMessage(LeaveMessage msg)
	{
		if (isSelf(msg))
		{
			return;
		}
		log.debug("[PartySync] received Leave from member {}", msg.getMemberId());
		removeMember(msg.getMemberId());
		notifyStateChanged();
	}

	@Subscribe
	public void onGlamourMessage(GlamourMessage msg)
	{
		if (isSelf(msg))
		{
			return;
		}

		String playerName = getMemberDisplayName(msg.getMemberId());
		log.debug("[PartySync] received {} from '{}' (member {})", msg.patch ? "patch" : "update", playerName, msg.getMemberId());

		PartyMember data = getOrCreateMemberData(msg.getMemberId());
		data.handleGlamourMessage(msg);
	}

	@Subscribe
	public void onFollowerMessage(FollowerMessage msg)
	{
		if (isSelf(msg))
		{
			return;
		}
		log.debug("[PartySync] received follower {} from member {}", msg.index, msg.getMemberId());
		PartyMember data = getOrCreateMemberData(msg.getMemberId());
		data.handleFollowerMessage(msg);
	}

	// --- Party state ---

	private void requestUpdate()
	{
		log.debug("[PartySync] request update");
		partyService.send(new RequestUpdateMessage());
	}

	public void resync()
	{
		if (!isStarted || !partyService.isInParty())
		{
			return;
		}
		requestUpdate();
		sendUpdate();
	}

	private void partyLeave()
	{
		clientThread.invoke(() -> {
			glamourEngine.clearPlayerGlamours();
			petGlamourSync.clearPartyMembers();
			partyMembers.clear();
			syncedItemIds.clear();
			lastSentFollowerIndex = -1;
		});
		updateQueued = false;
	}

	private void removeMember(long memberId)
	{
		PartyMember data = partyMembers.remove(memberId);
		if (data != null)
		{
			clientThread.invoke(data::removeFromParty);
		}
	}

	private PartyMember getOrCreateMemberData(long memberId)
	{
		return partyMembers.computeIfAbsent(memberId, PartyMember::new);
	}

	@Nullable
	private String getMemberDisplayName(long memberId)
	{
		var member = partyService.getMemberById(memberId);
		return member != null ? member.getDisplayName() : null;
	}

	private void notifyStateChanged()
	{
		onStateChanged.run();
	}

	// --- Sending ---

	private void sendFollower(int followerViewId, int followerIndex)
	{
		if (!shouldSendUpdates())
		{
			return;
		}
		if (followerIndex == -1 && lastSentFollowerIndex == -1)
		{
			return;
		}
		lastSentFollowerIndex = followerIndex;
		log.debug("[PartySync] sending follower {} in view {}", followerIndex, followerViewId);
		partyService.send(new FollowerMessage(client.getWorld(), followerViewId, followerIndex));
	}

	public void sendUpdate()
	{
		clientThread.invoke(() -> {
			if (!shouldSendUpdates())
			{
				return;
			}
			sendFollower(followerTracker.getFollowerViewId(), followerTracker.getFollowerIndex());
			var itemIds = new LinkedHashSet<>(client.getLocalPlayerEquippableItemIds(ddItemManager));
			itemIds.addAll(petSheet.getNpcsByItemId().keySet());
			var snapshot = glamourEngine.getGlamourSnapshotForItems(itemIds);
			try
			{
				log.debug("[PartySync] sending update: {} glamours", snapshot.size());
				syncedItemIds = snapshot.stream().map(GlamourData::getItemId).collect(Collectors.toSet());
				partyService.send(GlamourMessage.update(snapshot));
			}
			catch (IOException e)
			{
				log.warn("[PartySync] failed to encode update", e);
			}
		});
	}

	public void sendPatch(Set<Integer> itemIds)
	{
		clientThread.invoke(() -> {
			if (!shouldSendUpdates())
			{
				return;
			}
			List<GlamourData> patch = glamourEngine.getGlamourSnapshotForItems(itemIds);
			if (!patch.isEmpty())
			{
				try
				{
					log.debug("[PartySync] sending glamour patch: {} glamours", patch.size());
					syncedItemIds.addAll(itemIds);
					partyService.send(GlamourMessage.patch(patch));
				}
				catch (IOException e)
				{
					log.warn("[PartySync] failed to encode patch", e);
				}
			}
		});
	}

	public void sendRevoke()
	{
		if (!isStarted || !partyService.isInParty())
		{
			return;
		}
		try
		{
			log.debug("[PartySync] revoking glamour data");
			partyService.send(GlamourMessage.revokeAll());
		}
		catch (IOException e)
		{
			log.warn("[PartySync] failed to encode revoke", e);
		}
	}

	// --- Queries ---

	public boolean isInParty()
	{
		return partyService.isInParty();
	}

	public List<PartyMemberInfo> getMembers()
	{
		if (!partyService.isInParty() || partyService.getLocalMember() == null)
		{
			return List.of();
		}
		long localId = partyService.getLocalMember().getMemberId();
		List<PartyMemberInfo> result = new ArrayList<>();
		for (var member : partyService.getMembers())
		{
			if (member.getMemberId() == localId)
			{
				continue;
			}
			var glamourMember = partyMembers.get(member.getMemberId());
			boolean hasGlamour = glamourMember != null;
			String name = member.getDisplayName() != null ? member.getDisplayName() : UNKNOWN_NAME;
			result.add(new PartyMemberInfo(member.getMemberId(), name, hasGlamour));
		}
		return result;
	}

	public Collection<Glamour> getMemberGlamours(long memberId)
	{
		PartyMember member = partyMembers.get(memberId);
		if (member != null && member.hasName())
		{
			return glamourEngine.getPlayerGlamours(member.standardizedName);
		}
		return Collections.emptySet();
	}

	public boolean isMemberHidden(long memberId)
	{
		String name = getMemberDisplayName(memberId);
		return name != null && !UNKNOWN_NAME.equals(name) && blacklist.contains(name);
	}

	public void setMemberHidden(long memberId, boolean hidden)
	{
		String name = getMemberDisplayName(memberId);
		if (name == null || UNKNOWN_NAME.equals(name))
		{
			return;
		}
		if (hidden)
		{
			blacklist.add(name);
		}
		else
		{
			blacklist.remove(name);
		}
		PartyMember glamourMember = partyMembers.get(memberId);
		if (glamourMember != null)
		{
			clientThread.invoke(glamourMember::reapply);
		}
	}

	private boolean isSelf(PartyMemberMessage message)
	{
		var localMember = partyService.getLocalMember();
		return localMember == null || localMember.getMemberId() == message.getMemberId();
	}

	private boolean shouldSendUpdates()
	{
		return isStarted && partyService.isInParty() && config.partySyncSend() && client.getGameState() == GameState.LOGGED_IN;
	}
}
