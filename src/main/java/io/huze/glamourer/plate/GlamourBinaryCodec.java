package io.huze.glamourer.plate;

import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.glam.GlamourData;
import io.huze.glamourer.texture.TextureReplacement;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class GlamourBinaryCodec
{
	private static final int VERSION = 1;
	private static final int FLAG_HAS_ITEM_KEY = 1;
	private static final int FLAG_HAS_TEXTURES = 1 << 1;

	private GlamourBinaryCodec()
	{
	}

	public static byte[] encodeList(List<GlamourData> glamours) throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(baos);
		dos.writeByte(VERSION);
		dos.writeShort(glamours.size());
		for (GlamourData glamour : glamours)
		{
			encode(dos, glamour);
		}
		dos.flush();
		return baos.toByteArray();
	}

	public static List<GlamourData> decodeList(byte[] bytes) throws IOException
	{
		DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
		int version = dis.readUnsignedByte();
		if (version != VERSION)
		{
			throw new IOException("Unsupported glamour binary version: " + version);
		}
		int count = dis.readUnsignedShort();
		List<GlamourData> glamours = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			glamours.add(decode(dis));
		}
		return glamours;
	}

	private static void encode(DataOutputStream dos, GlamourData glamour) throws IOException
	{
		int flags = 0;
		if (glamour.getItemKey() != null)
		{
			flags |= FLAG_HAS_ITEM_KEY;
		}
		boolean hasTextures = glamour.getTextureReplacements() != null && !glamour.getTextureReplacements().isEmpty();
		if (hasTextures)
		{
			flags |= FLAG_HAS_TEXTURES;
		}
		dos.writeByte(flags);

		if ((flags & FLAG_HAS_ITEM_KEY) != 0)
		{
			dos.writeUTF(glamour.getItemKey());
		}
		else
		{
			dos.writeInt(glamour.getItemId() != null ? glamour.getItemId() : -1);
		}

		List<ColorReplacement> colors = glamour.getColorReplacements();
		dos.writeShort(colors.size());
		for (ColorReplacement cr : colors)
		{
			dos.writeShort(cr.getOriginal());
			dos.writeShort(cr.getReplacement());
			boolean hasModel = cr.getModel() != null;
			dos.writeBoolean(hasModel);
			if (hasModel)
			{
				dos.writeShort(cr.getModel());
			}
		}

		if (hasTextures)
		{
			List<TextureReplacement> textures = glamour.getTextureReplacements();
			dos.writeShort(textures.size());
			for (TextureReplacement tr : textures)
			{
				dos.writeShort(tr.getOriginal());
				dos.writeShort(tr.getReplacement());
			}
		}
	}

	private static GlamourData decode(DataInputStream dis) throws IOException
	{
		int flags = dis.readUnsignedByte();

		String itemKey = null;
		int itemId = -1;
		if ((flags & FLAG_HAS_ITEM_KEY) != 0)
		{
			itemKey = dis.readUTF();
		}
		else
		{
			itemId = dis.readInt();
		}

		int colorCount = dis.readUnsignedShort();
		List<ColorReplacement> colors = new ArrayList<>(colorCount);
		for (int i = 0; i < colorCount; i++)
		{
			ColorReplacement cr = new ColorReplacement(dis.readShort(), dis.readShort());
			if (dis.readBoolean())
			{
				cr.setModel(dis.readShort());
			}
			colors.add(cr);
		}

		List<TextureReplacement> textures = null;
		if ((flags & FLAG_HAS_TEXTURES) != 0)
		{
			int textureCount = dis.readUnsignedShort();
			textures = new ArrayList<>(textureCount);
			for (int i = 0; i < textureCount; i++)
			{
				textures.add(new TextureReplacement(dis.readShort(), dis.readShort()));
			}
		}

		return new GlamourData(itemKey, itemId, colors, textures);
	}
}
