package io.huze.glamourer.texture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Texture;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextureAnimation
{
	public static final TextureAnimation STATIC = new TextureAnimation(0, 0);
	public static final int REDRAW_INTERVAL_MS = 40;

	private static final int MILLIS_PER_CLIENT_CYCLE = 20;
	/// GPU plugin's TEXTURE_ANIM_UNIT, 128 cycles, as a shift.
	private static final int CYCLES_PER_TEXTURE_AT_UNIT_SPEED_SHIFT = 7;

	private final int columnStep;
	private final int rowStep;

	@Nonnull
	public static TextureAnimation of(@Nullable Texture texture)
	{
		return texture == null ? STATIC : of(texture.getAnimationDirection(), texture.getAnimationSpeed());
	}

	@Nonnull
	public static TextureAnimation of(int direction, int speed)
	{
		if (speed == 0)
		{
			return STATIC;
		}
		switch (direction)
		{
			case 1: // up
				return new TextureAnimation(0, -speed);
			case 2: // left
				return new TextureAnimation(-speed, 0);
			case 3: // down
				return new TextureAnimation(0, speed);
			case 4: // right
				return new TextureAnimation(speed, 0);
			default:
				return STATIC;
		}
	}

	public static long currentCycle()
	{
		return System.currentTimeMillis() / MILLIS_PER_CLIENT_CYCLE;
	}

	public boolean isAnimated()
	{
		return columnStep != 0 || rowStep != 0;
	}

	public int getColumnOffset(int size, long cycle)
	{
		return getTexelOffset(columnStep, size, cycle);
	}

	public int getRowOffset(int size, long cycle)
	{
		return getTexelOffset(rowStep, size, cycle);
	}

	public void drawFrame(@Nonnull int[] texels, int size, long cycle, @Nonnull int[] frame)
	{
		int columnOffset = getColumnOffset(size, cycle);
		int rowOffset = getRowOffset(size, cycle);
		for (int row = 0; row < size; row++)
		{
			int sourceRow = (row + rowOffset) % size * size;
			int firstRun = size - columnOffset;
			System.arraycopy(texels, sourceRow + columnOffset, frame, row * size, firstRun);
			System.arraycopy(texels, sourceRow, frame, row * size + firstRun, columnOffset);
		}
	}

	/// Per sample. Size must be a power of two.
	private static int getTexelOffset(int step, int size, long cycle)
	{
		return (int) ((cycle * step * size) >> CYCLES_PER_TEXTURE_AT_UNIT_SPEED_SHIFT) & (size - 1);
	}
}
