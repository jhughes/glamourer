package io.huze.glamourer.item;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ItemRow
{
	final int id;
	final long releaseDate;
	final long removalDate;
	final boolean isQuest;
	final short category;
	// Note: Models are only specified if their colors differ from the inventory model.
	final int maleModel0;
	final int maleModel1;
	final int maleModel2;
	final int femaleModel0;
	final int femaleModel1;
	final int femaleModel2;

	public boolean isUncommon() {
		switch (category) {
			case Category.DeadItems:
			case Category.BoardGame1:
			case Category.BoardGame2:
			case Category.Banner:
			case Category.Housing:
			case Category.HouseRoom:
			case Category.HouseFurniture:
			case Category.BarbAssault1:
			case Category.BarbAssault2:
			case Category.BarbAssault3:
			case Category.BarbAssault4:
			case Category.ItemPack:
			case Category.ReadableBook1:
			case Category.ReadableBook2:
			case Category.ReadableBook3:
			case Category.BarbAssaultIcon:
			case Category.SoulWarsIcon:
			case Category.Xp:
			case Category.ArmorSet:
			case Category.HunterRumourPart:
			case Category.CarvedPumpkin:
			case Category.SailingBottle:
			case Category.FishCrate:
			case Category.SailingSchematic:
			case Category.SailingLockboxKey:
			case Category.SailingCourierCrate:
			case Category.SailingBountyPart:
			case Category.SailingRelic:
				return true;
		}
		return false;
	}

	public static ItemRow fromCsvColumns(String[] cols)
	{
		if (cols.length != ItemSheet.CSV_HEADERS.length)
		{
			throw new IllegalArgumentException(String.join(",", cols));
		}

		int i = 0;
		return ItemRow.builder()
			.id(Integer.parseInt(cols[i++].trim()))
			.releaseDate(Long.parseLong(cols[i++].trim()))
			.removalDate(Long.parseLong(cols[i++].trim()))
			.isQuest(Boolean.parseBoolean(cols[i++].trim()))
			.category(Short.parseShort(cols[i++].trim()))
			.maleModel0(Integer.parseInt(cols[i++].trim()))
			.maleModel1(Integer.parseInt(cols[i++].trim()))
			.maleModel2(Integer.parseInt(cols[i++].trim()))
			.femaleModel0(Integer.parseInt(cols[i++].trim()))
			.femaleModel1(Integer.parseInt(cols[i++].trim()))
			.femaleModel2(Integer.parseInt(cols[i++].trim()))
			.build();
	}
}