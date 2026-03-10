package io.huze.glamourer.ui.colorpicker;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;
import lombok.Setter;

public class ColorSliderUI extends BasicSliderUI
{
	private static final int TRACK_HEIGHT = 10;
	private static final int THUMB_WIDTH = 3;

	@Setter
	private Color[] colors;

	public ColorSliderUI(JSlider b, Color[] colors)
	{
		super(b);
		this.colors = colors;
	}

	@Override
	public int xPositionForValue(int value)
	{
		double stepWidth = (double) trackRect.width / colors.length;
		return trackRect.x + (int) ((value + 0.5) * stepWidth);
	}

	@Override
	public int valueForXPosition(int xPos)
	{
		double stepWidth = (double) trackRect.width / colors.length;
		int value = (int) ((xPos - trackRect.x) / stepWidth);

		if (value < 0)
		{
			return 0;
		}
		if (value >= colors.length)
		{
			return colors.length - 1;
		}
		return value;
	}

	@Override
	public void paintThumb(Graphics g)
	{
		g.setColor(Color.WHITE);
		int x = thumbRect.x + (thumbRect.width / 2);
		g.fillRect(x - 1, thumbRect.y, THUMB_WIDTH, thumbRect.height);
		g.setColor(Color.BLACK);
		g.drawRect(x - 1, thumbRect.y, THUMB_WIDTH, thumbRect.height);
	}

	@Override
	public void paintTrack(Graphics g)
	{
		Rectangle t = trackRect;
		int trackY = t.y + (t.height / 2) - (TRACK_HEIGHT / 2);

		ColorUtil.paintColorSpread(g, t.x, trackY, t.width, TRACK_HEIGHT, colors);

		g.setColor(Color.DARK_GRAY);
		g.drawRect(t.x, trackY, t.width, TRACK_HEIGHT);
	}
}
