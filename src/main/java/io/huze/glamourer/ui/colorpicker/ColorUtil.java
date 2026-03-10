package io.huze.glamourer.ui.colorpicker;

import java.awt.Color;
import java.awt.Graphics;

class ColorUtil
{
	static void paintColorSpread(Graphics g, int x, int y, int width, int height, Color[] colors)
	{
		int count = colors.length;
		for (int i = 0; i < count; i++)
		{
			int xStart = i * width / count;
			int xEnd = (i + 1) * width / count;
			g.setColor(colors[i]);
			g.fillRect(x + xStart, y, xEnd - xStart, height);
		}
	}

	static void paintPreview(Graphics g, int width, int height, Color[] colors)
	{
		int steps = colors.length;
		if (steps == 1)
		{
			g.setColor(colors[0]);
			g.fillRect(0, 0, width, height);
			return;
		}

		int mid = steps / 2;
		int thirdH = height / 3;

		// Top third: light gradient (brightest to median)
		for (int i = 0; i < mid; i++)
		{
			int idx = steps - 1 - i;
			int yStart = i * thirdH / mid;
			int yEnd = (i + 1) * thirdH / mid;
			g.setColor(colors[idx]);
			g.fillRect(0, yStart, width, yEnd - yStart);
		}

		// Middle third: median color
		g.setColor(colors[mid]);
		g.fillRect(0, thirdH, width, height - 2 * thirdH);

		// Bottom third: dark gradient (median to darkest)
		for (int i = 0; i < mid; i++)
		{
			int idx = mid - 1 - i;
			int yStart = height - thirdH + i * thirdH / mid;
			int yEnd = height - thirdH + (i + 1) * thirdH / mid;
			g.setColor(colors[idx]);
			g.fillRect(0, yStart, width, yEnd - yStart);
		}
	}
}
