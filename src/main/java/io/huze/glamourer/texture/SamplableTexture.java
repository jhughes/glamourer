package io.huze.glamourer.texture;

import javax.annotation.Nonnull;
import lombok.Getter;

public final class SamplableTexture
{
	private static final short WHITE = 127;
	public static final SamplableTexture NONE = unavailable(WHITE);

	@Getter
	private final int size;
	@Getter
	@Nonnull
	private final int[] pixels;
	@Getter
	@Nonnull
	private final TextureAnimation animation;
	@Getter
	private final short averageColor;

	public SamplableTexture(int size, @Nonnull int[] pixels, @Nonnull TextureAnimation animation, short averageColor)
	{
		this.size = size;
		this.pixels = pixels;
		this.animation = animation;
		this.averageColor = averageColor;
	}

	@Nonnull
	public static SamplableTexture unavailable(short averageColor)
	{
		return new SamplableTexture(0, new int[0], TextureAnimation.STATIC, averageColor);
	}

	public boolean isUsable()
	{
		return size > 0;
	}

	public int sample(int column, int row, long cycle)
	{
		int mask = size - 1;
		column = (Math.max(0, Math.min(mask, column)) + animation.getColumnOffset(size, cycle)) & mask;
		row = (row + animation.getRowOffset(size, cycle)) & mask;
		return pixels[row * size + column];
	}
}
