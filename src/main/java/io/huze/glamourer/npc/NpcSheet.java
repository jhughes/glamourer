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
	private volatile Map<Integer, Integer> renderPriorityByNpcId = Map.of();

	public NpcSheet()
	{
		super("npc_sheet.csv", new String[]{"id", "ambient", "contrast", "render_priority"},
			cols -> new int[]{Integer.parseInt(cols[0]), Integer.parseInt(cols[1]), Integer.parseInt(cols[2]), Integer.parseInt(cols[3])});
	}

	public Lighting getLighting(int npcId)
	{
		return lightingByNpcId.getOrDefault(npcId, Lighting.DEFAULT);
	}

	/// Matches NpcDefinition.renderPriority from RuneLite cache:
	/// 0 - default
	/// 1 - high priority (above 0)
	/// 2 - low priority (below 0)
	public int getRenderPriority(int npcId)
	{
		return renderPriorityByNpcId.getOrDefault(npcId, 0);
	}

	@Override
	protected void load(List<int[]> rows)
	{
		Map<Integer, Lighting> lighting = new HashMap<>();
		Map<Integer, Integer> renderPriority = new HashMap<>();
		for (int[] row : rows)
		{
			if (row[1] != 0 || row[2] != 0)
			{
				lighting.put(row[0], new Lighting(row[1], row[2]));
			}
			if (row[3] != 0)
			{
				renderPriority.put(row[0], row[3]);
			}
		}
		this.lightingByNpcId = lighting;
		this.renderPriorityByNpcId = renderPriority;
	}
}
