package io.huze.glamourer.party;

import io.huze.glamourer.glam.GlamourData;
import io.huze.glamourer.plate.GlamourBinaryCodec;
import java.io.IOException;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GlamourMessage extends PartyMemberMessage
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
