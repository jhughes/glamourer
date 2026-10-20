package io.huze.glamourer.item;

import io.huze.glamourer.Sheet;
import io.huze.glamourer.render.ModelPose;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import net.runelite.api.ItemComposition;

@Singleton
public class ItemRenderSheet extends Sheet<ItemRenderSheet.Row>
{
	private static final String[] CSV_HEADERS = {"id", "zoom2d", "xOffset2d", "yOffset2d", "resizeX", "resizeY", "resizeZ"};
	public static final int DEFAULT_ZOOM_2D = 2000;
	public static final int DEFAULT_RESIZE = 128;
	public static final Row DEFAULT_ROW = new Row(
		-1, DEFAULT_ZOOM_2D, 0, 0, DEFAULT_RESIZE, DEFAULT_RESIZE, DEFAULT_RESIZE, ModelPose.UPRIGHT);

	private static final int JAGEX_ANGLE_UNITS = 2048;
	private static final double RADIANS_PER_JAGEX_UNIT = 2 * Math.PI / JAGEX_ANGLE_UNITS;

	private volatile Map<Integer, Row> rowsById = Map.of();

	@Inject
	public ItemRenderSheet()
	{
		super("item_render_sheet.csv", CSV_HEADERS, Row::fromCsvColumns);
	}

	/// Item render metadata. Client thread only.
	@Nonnull
	public Row getRenderParams(@Nonnull ItemComposition itemComp)
	{
		return rowsById.getOrDefault(itemComp.getId(), DEFAULT_ROW).toBuilder()
			.pose(new ModelPose(
				toRadians(itemComp.getXan2d()),
				toRadians(itemComp.getYan2d()),
				toRadians(itemComp.getZan2d())))
			.build();
	}

	private static double toRadians(int jagexAngle)
	{
		return (jagexAngle & (JAGEX_ANGLE_UNITS - 1)) * RADIANS_PER_JAGEX_UNIT;
	}

	@Override
	protected void load(List<Row> rows)
	{
		this.rowsById = rows.stream()
			.collect(Collectors.toMap(Row::getId, Function.identity(), (first, second) -> first));
	}

	@Data
	@Builder(toBuilder = true)
	@AllArgsConstructor
	public static class Row
	{
		final int id;
		final int zoom2d;
		final int xOffset2d;
		final int yOffset2d;
		final int resizeX;
		final int resizeY;
		final int resizeZ;
		@Nonnull
		final ModelPose pose;

		public boolean isResized()
		{
			return resizeX != DEFAULT_RESIZE || resizeY != DEFAULT_RESIZE || resizeZ != DEFAULT_RESIZE;
		}

		private static Row fromCsvColumns(String[] cols)
		{
			int i = 0;
			return Row.builder()
				.id(Integer.parseInt(cols[i++]))
				.zoom2d(parseOrDefault(cols[i++], DEFAULT_ZOOM_2D))
				.xOffset2d(parseOrDefault(cols[i++], 0))
				.yOffset2d(parseOrDefault(cols[i++], 0))
				.resizeX(parseOrDefault(cols[i++], DEFAULT_RESIZE))
				.resizeY(parseOrDefault(cols[i++], DEFAULT_RESIZE))
				.resizeZ(parseOrDefault(cols[i++], DEFAULT_RESIZE))
				.pose(ModelPose.UPRIGHT)
				.build();
		}

		private static int parseOrDefault(String column, int defaultValue)
		{
			return column.isEmpty() ? defaultValue : Integer.parseInt(column);
		}
	}
}
