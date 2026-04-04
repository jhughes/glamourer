package io.huze.glamourer.party;

import io.huze.glamourer.Config;
import io.huze.glamourer.Extensions;
import io.huze.glamourer.glam.GlamourData;
import io.huze.glamourer.glam.GlamourEngine;
import io.huze.glamourer.item.DedupeItemManager;
import io.huze.glamourer.plate.GlamourBinaryCodec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
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
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.party.PartyPlugin;

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

	private final Map<Long, PartyMember> partyMembers = new HashMap<>();
	private Set<Integer> syncedItemIds = new HashSet<>();
	private boolean isStarted;

	@Setter
	private Runnable onStateChanged;

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
		String lastKnownName = UNKNOWN_NAME;
		@Nonnull
		public final Map<Integer, GlamourData> itemIdToGlamour = new HashMap<>();

		PartyMember(long memberId)
		{
			this.id = memberId;
		}

		void handleGlamourMessage(GlamourMessage msg)
		{
			List<GlamourData> glamours;
			try
			{
				glamours = GlamourBinaryCodec.decodeList(msg.data);
			}
			catch (IOException e)
			{
				log.warn("[PartySync] failed to decode glamour message from member {}", msg.getMemberId(), e);
				return;
			}

			if (!msg.patch)
			{
				itemIdToGlamour.clear();
			}
			glamours.forEach(g -> itemIdToGlamour.put(g.getItemId(), g));
			updateGlamours();
		}

		void maybeUpdateName(@Nullable String newName)
		{
			newName = newName != null ? newName : UNKNOWN_NAME;
			if (!lastKnownName.equals(newName))
			{
				log.debug("[PartySync] member {} name changed: '{}' -> '{}'", id, lastKnownName, newName);
				removeFromParty();
				lastKnownName = newName;
				updateGlamours();
				notifyStateChanged();
			}
		}

		void removeFromParty()
		{
			if (!UNKNOWN_NAME.equals(lastKnownName))
			{
				clientThread.invoke(() -> glamourEngine.removePlayerGlamour(lastKnownName));
			}
		}

		private void updateGlamours()
		{
			if (!UNKNOWN_NAME.equals(lastKnownName) && !blacklist.contains(lastKnownName))
			{
				clientThread.invoke(() -> glamourEngine.updatePlayerGlamour(lastKnownName, itemIdToGlamour));
			}
		}
	}

	public static final class RequestUpdateMessage extends PartyMemberMessage
	{
	}

	public static final class LeaveMessage extends PartyMemberMessage
	{
	}

	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class GlamourMessage extends PartyMemberMessage
	{
		final boolean patch;
		final byte[] data;

		static GlamourMessage revokeAll() throws IOException
		{
			return update(List.of());
		}

		static GlamourMessage update(List<GlamourData> glamours) throws IOException
		{
			return new GlamourMessage(false, GlamourBinaryCodec.encodeList(glamours));
		}

		static GlamourMessage patch(List<GlamourData> glamours) throws IOException
		{
			return new GlamourMessage(true, GlamourBinaryCodec.encodeList(glamours));
		}
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
			requestUpdate();
			sendUpdate();
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
				return true;
			});
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!isStarted || !partyService.isInParty())
		{
			return;
		}

		// Clean up stale members no longer in the party
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
				if (config.partySyncReceive())
				{
					requestUpdate();
				}
				else
				{
					glamourEngine.clearPlayerGlamours();
				}
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
		if (isSelf(msg) || !config.partySyncReceive())
		{
			return;
		}

		String playerName = getMemberDisplayName(msg.getMemberId());
		log.debug("[PartySync] received {} from '{}' (member {})", msg.patch ? "patch" : "update", playerName, msg.getMemberId());

		PartyMember data = getOrCreateMemberData(msg.getMemberId());
		data.handleGlamourMessage(msg);
		notifyStateChanged();
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
		clientThread.invoke(() -> glamourEngine.clearPlayerGlamours());
		partyMembers.clear();
		syncedItemIds.clear();
	}

	private void removeMember(long memberId)
	{
		PartyMember data = partyMembers.remove(memberId);
		if (data != null)
		{
			data.removeFromParty();
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
		if (onStateChanged != null)
		{
			onStateChanged.run();
		}
	}

	// --- Sending ---

	public void sendUpdate()
	{
		if (!shouldSendUpdates())
		{
			return;
		}
		clientThread.invoke(() -> {
			var snapshot = glamourEngine.getEquippableGlamourSnapshot();
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
		if (!shouldSendUpdates())
		{
			return;
		}
		clientThread.invoke(() -> {
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

	public boolean isMemberHidden(long memberId)
	{
		PartyMember glamourMember = partyMembers.get(memberId);
		if (glamourMember == null || UNKNOWN_NAME.equals(glamourMember.lastKnownName))
		{
			return false;
		}
		return blacklist.contains(glamourMember.lastKnownName);
	}

	public void setMemberHidden(long memberId, boolean hidden)
	{
		PartyMember glamourMember = partyMembers.get(memberId);
		if (glamourMember == null || UNKNOWN_NAME.equals(glamourMember.lastKnownName))
		{
			return;
		}
		final String name = glamourMember.lastKnownName;
		if (hidden)
		{
			blacklist.add(name);
			glamourMember.removeFromParty();
		}
		else
		{
			blacklist.remove(name);
			glamourMember.updateGlamours();
		}
	}

	private boolean isSelf(PartyMemberMessage message)
	{
		return partyService.getLocalMember().getMemberId() == message.getMemberId();
	}

	private boolean shouldSendUpdates()
	{
		return isStarted && partyService.isInParty() && config.partySyncSend() && client.getGameState() == GameState.LOGGED_IN;
	}
}
