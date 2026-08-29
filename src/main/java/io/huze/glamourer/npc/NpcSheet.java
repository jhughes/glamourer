package io.huze.glamourer.npc;

import io.huze.glamourer.Sheet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.Getter;

@Singleton
public class NpcSheet extends Sheet<int[]>
{
	public static final class Lighting
	{
		static final Lighting DEFAULT = new Lighting(0, 0);

		@Getter
		private final int ambient;
		@Getter
		private final int contrast;

		private Lighting(int ambient, int contrast)
		{
			this.ambient = ambient;
			this.contrast = contrast;
		}
	}

	private volatile Map<Integer, Lighting> lightingByNpcId = Map.of();

	public NpcSheet()
	{
		super("npc_sheet.csv", new String[]{"id", "ambient", "contrast"},
			cols -> new int[]{Integer.parseInt(cols[0]), Integer.parseInt(cols[1]), Integer.parseInt(cols[2])});
	}

	public Lighting getLighting(int npcId)
	{
		return lightingByNpcId.getOrDefault(npcId, Lighting.DEFAULT);
	}

	@Override
	protected void load(List<int[]> rows)
	{
		Map<Integer, Lighting> lighting = new HashMap<>();
		for (int[] row : rows)
		{
			if (row[1] != 0 || row[2] != 0)
			{
				lighting.put(row[0], new Lighting(row[1], row[2]));
			}
		}
		this.lightingByNpcId = lighting;
	}
}
