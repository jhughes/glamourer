package io.huze.glamourer.render;

import java.util.Arrays;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;

@UtilityClass
final class FaceOrder
{
	private static final int PRIORITIES = 12;
	private static final int FIRST_DYNAMIC = 10;
	private static final int NO_THRESHOLD = Integer.MAX_VALUE;

	@Nonnull
	static int[] order(@Nonnull int[] depths, @Nullable byte[] priorities, int count)
	{
		var farToNear = sortFarToNear(depths, count);
		return priorities == null ? farToNear : interleave(farToNear, depths, priorities, count);
	}

	@Nonnull
	private static int[] sortFarToNear(@Nonnull int[] depths, int count)
	{
		var keys = new long[count];
		for (int face = 0; face < count; face++)
		{
			keys[face] = (((long) Integer.MAX_VALUE - depths[face]) << 32) | face;
		}
		Arrays.sort(keys);
		var order = new int[count];
		for (int i = 0; i < count; i++)
		{
			order[i] = (int) keys[i];
		}
		return order;
	}

	@Nonnull
	private static int[] interleave(@Nonnull int[] farToNear, @Nonnull int[] depths, @Nonnull byte[] priorities, int count)
	{
		var counts = new int[PRIORITIES];
		for (int face = 0; face < count; face++)
		{
			counts[priorityOf(priorities, face)]++;
		}
		var byPriority = new int[PRIORITIES][];
		for (int priority = 0; priority < PRIORITIES; priority++)
		{
			byPriority[priority] = new int[counts[priority]];
		}
		var filled = new int[PRIORITIES];
		var depthSums = new long[PRIORITIES];
		for (int face : farToNear)
		{
			int priority = priorityOf(priorities, face);
			byPriority[priority][filled[priority]++] = face;
			depthSums[priority] += depths[face];
		}

		int average12 = average(depthSums, counts, 1, 2);
		int average34 = average(depthSums, counts, 3, 4);
		int average68 = average(depthSums, counts, 6, 8);

		var dynamic = new DynamicFaces(byPriority[FIRST_DYNAMIC], byPriority[FIRST_DYNAMIC + 1], depths);
		var order = new int[count];
		int next = 0;
		for (int priority = 0; priority < FIRST_DYNAMIC; priority++)
		{
			if (priority == 0)
			{
				next = dynamic.drainDeeperThan(average12, order, next);
			}
			else if (priority == 3)
			{
				next = dynamic.drainDeeperThan(average34, order, next);
			}
			else if (priority == 5)
			{
				next = dynamic.drainDeeperThan(average68, order, next);
			}
			for (int face : byPriority[priority])
			{
				order[next++] = face;
			}
		}
		return dynamic.drainAll(order, next);
	}

	private static int priorityOf(@Nonnull byte[] priorities, int face)
	{
		return Math.max(0, Math.min(PRIORITIES - 1, priorities[face]));
	}

	private static int average(@Nonnull long[] depthSums, @Nonnull int[] counts, int priorityA, int priorityB)
	{
		int count = counts[priorityA] + counts[priorityB];
		return count == 0 ? NO_THRESHOLD : (int) ((depthSums[priorityA] + depthSums[priorityB]) / count);
	}

	private static final class DynamicFaces
	{
		private final int[] low;
		private final int[] high;
		private final int[] depths;
		private int drawn;

		DynamicFaces(@Nonnull int[] low, @Nonnull int[] high, @Nonnull int[] depths)
		{
			this.low = low;
			this.high = high;
			this.depths = depths;
		}

		int drainDeeperThan(int depth, @Nonnull int[] order, int next)
		{
			while (drawn < low.length + high.length && depths[peek()] > depth)
			{
				order[next++] = take();
			}
			return next;
		}

		@Nonnull
		int[] drainAll(@Nonnull int[] order, int next)
		{
			while (drawn < low.length + high.length)
			{
				order[next++] = take();
			}
			return order;
		}

		private int peek()
		{
			return drawn < low.length ? low[drawn] : high[drawn - low.length];
		}

		private int take()
		{
			int face = peek();
			drawn++;
			return face;
		}
	}
}
