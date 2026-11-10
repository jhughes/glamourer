package io.huze.glamourer.item;

import io.huze.glamourer.CsvLoader;
import io.huze.glamourer.Sheet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.ModelData;

@Singleton
public class ItemSpotAnimSheet extends Sheet<ItemSpotAnimSheet.Row>
{
	private static final String[] CSV_HEADERS = {
		"item_id", "spotanim_id", "model_id", "anim_id", "ambient", "contrast", "resize_x", "resize_y", "rotation",
		"recolor_find", "recolor_replace", "retexture_find", "retexture_replace"};

	private volatile Map<Integer, List<Row>> rowsBySpotAnimId = Collections.emptyMap();
	private volatile Map<Integer, List<Row>> rowsByItemId = Collections.emptyMap();

	public ItemSpotAnimSheet()
	{
		super("item_spotanim_sheet.csv", CSV_HEADERS, Row::fromCsvColumns);
	}

	@Nonnull
	public List<Row> getRows(int spotAnimId)
	{
		return rowsBySpotAnimId.getOrDefault(spotAnimId, Collections.emptyList());
	}

	@Nonnull
	public List<Row> getRowsForItem(int itemId)
	{
		return rowsByItemId.getOrDefault(itemId, Collections.emptyList());
	}

	@Override
	protected void load(List<Row> rows)
	{
		var bySpotAnim = new HashMap<Integer, List<Row>>();
		var byItem = new HashMap<Integer, List<Row>>();
		for (var row : rows)
		{
			bySpotAnim.computeIfAbsent(row.spotAnimId, id -> new ArrayList<>()).add(row);
			byItem.computeIfAbsent(row.itemId, id -> new ArrayList<>()).add(row);
		}
		bySpotAnim.replaceAll((id, list) -> Collections.unmodifiableList(list));
		byItem.replaceAll((id, list) -> Collections.unmodifiableList(list));
		this.rowsBySpotAnimId = bySpotAnim;
		this.rowsByItemId = byItem;
	}

	@Value
	public static class Row
	{
		int itemId;
		int spotAnimId;
		int modelId;
		int animationId;
		int ambient;
		int contrast;
		int resizeX;
		int resizeY;
		int rotation;
		short[] recolorFind;
		short[] recolorReplace;
		short[] retextureFind;
		short[] retextureReplace;

		@Nonnull
		public ModelData prepare(@Nonnull ModelData loaded)
		{
			var data = loaded.shallowCopy();
			if (recolorFind.length > 0)
			{
				data = data.cloneColors();
				for (int i = 0; i < recolorFind.length; i++)
				{
					data.recolor(recolorFind[i], recolorReplace[i]);
				}
			}
			if (retextureFind.length > 0 && data.getFaceTextures() != null)
			{
				data = data.cloneTextures();
				for (int i = 0; i < retextureFind.length; i++)
				{
					data.retexture(retextureFind[i], retextureReplace[i]);
				}
			}
			boolean resized = resizeX != ItemRenderSheet.DEFAULT_RESIZE || resizeY != ItemRenderSheet.DEFAULT_RESIZE;
			if (rotation != 0 || resized)
			{
				data = data.cloneVertices();
				switch (rotation)
				{
					case 1:
						data = data.rotateY90Ccw();
						break;
					case 2:
						data = data.rotateY180Ccw();
						break;
					case 3:
						data = data.rotateY270Ccw();
						break;
					default:
						break;
				}
				if (resized)
				{
					data = data.scale(resizeX, resizeY, resizeX);
				}
			}
			return data;
		}

		private static Row fromCsvColumns(String[] cols)
		{
			int i = 0;
			return new Row(
				Integer.parseInt(cols[i++]),
				Integer.parseInt(cols[i++]),
				Integer.parseInt(cols[i++]),
				Integer.parseInt(cols[i++]),
				Integer.parseInt(cols[i++]),
				Integer.parseInt(cols[i++]),
				Integer.parseInt(cols[i++]),
				Integer.parseInt(cols[i++]),
				Integer.parseInt(cols[i++]),
				CsvLoader.parseShortArray(cols[i++]),
				CsvLoader.parseShortArray(cols[i++]),
				CsvLoader.parseShortArray(cols[i++]),
				CsvLoader.parseShortArray(cols[i]));
		}
	}
}
