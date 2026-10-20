package io.huze.glamourer.item;

import io.huze.glamourer.Sheet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Singleton
public class ItemSheet extends Sheet<ItemSheet.Row>
{
	private static final String[] CSV_HEADERS = {"id", "release_date", "removal_date", "quest", "category"};

	@Getter
	private volatile Set<Integer> removedItemIds = Set.of();
	@Getter
	private volatile Set<Integer> questItemIds = Set.of();
	@Getter
	private volatile Set<Integer> uncommonItemIds = Set.of();

	@Inject
	public ItemSheet()
	{
		super("item_sheet.csv", CSV_HEADERS, Row::fromCsvColumns);
	}

	@Override
	protected void load(List<Row> items)
	{
		this.removedItemIds = items.stream()
			.filter(Row::isRemoved)
			.map(Row::getId)
			.collect(Collectors.toSet());
		this.questItemIds = items.stream()
			.filter(Row::isQuest)
			.map(Row::getId)
			.collect(Collectors.toSet());
		this.uncommonItemIds = items.stream()
			.filter(Row::isUncommon)
			.map(Row::getId)
			.collect(Collectors.toSet());
	}

	@Data
	@Builder
	@AllArgsConstructor
	public static class Row
	{
		final int id;
		final long releaseDate;
		final long removalDate;
		final boolean isQuest;
		final short category;

		public boolean isRemoved()
		{
			return removalDate < Integer.MAX_VALUE;
		}

		public boolean isUncommon()
		{
			switch (category)
			{
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

		public static Row fromCsvColumns(String[] cols)
		{
			int i = 0;
			return Row.builder()
				.id(Integer.parseInt(cols[i++]))
				.releaseDate(Long.parseLong(cols[i++]))
				.removalDate(Long.parseLong(cols[i++]))
				.isQuest(Boolean.parseBoolean(cols[i++]))
				.category(Short.parseShort(cols[i++]))
				.build();
		}
	}
}
