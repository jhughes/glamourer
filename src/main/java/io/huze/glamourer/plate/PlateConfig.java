package io.huze.glamourer.plate;

import io.huze.glamourer.glam.GlamourData;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
class PlateConfig
{
	private String name;
	private Boolean enabled;
	private DisplayStyle displayStyle;
	private IconStyle iconStyle;
	/// Base64 encoded glamour data
	private String glamours;

	@Nullable
	private Long deletedAt;

	static PlateConfig fromPlateData(PlateData data) throws IOException
	{
		return encodeGlamours(data.getName(), data.getEnabled(), data.getDisplayStyle(),
			data.getIconStyle(), data.getGlamours());
	}

	static PlateConfig fromPlate(Plate plate) throws IOException
	{
		return encodeGlamours(plate.getName(), plate.isEnabled(), plate.getDisplayStyle(),
			plate.getIconStyle(), plate.getGlamourData());
	}

	private static PlateConfig encodeGlamours(String name, Boolean enabled,
											  DisplayStyle displayStyle, IconStyle iconStyle,
											  List<GlamourData> glamourList) throws IOException
	{
		byte[] binary = glamourList != null && !glamourList.isEmpty()
			? GlamourBinaryCodec.encodeList(glamourList)
			: new byte[0];
		String encoded = binary.length > 0 ? Base64.getEncoder().encodeToString(binary) : null;

		return new PlateConfig(name, enabled, displayStyle, iconStyle, encoded, null);
	}

	PlateData toPlateData(String id) throws IOException
	{
		List<GlamourData> glamourList;
		if (glamours != null && !glamours.isEmpty())
		{
			byte[] binary = Base64.getDecoder().decode(glamours);
			glamourList = GlamourBinaryCodec.decodeList(binary);
		}
		else
		{
			glamourList = Collections.emptyList();
		}

		return new PlateData(id, name, enabled, displayStyle, iconStyle, glamourList);
	}

	boolean isTombstone()
	{
		return deletedAt != null;
	}

	boolean isExpiredTombstone(long now)
	{
		return deletedAt != null && (now - deletedAt) > PlateStore.TOMBSTONE_TTL_MS;
	}
}
