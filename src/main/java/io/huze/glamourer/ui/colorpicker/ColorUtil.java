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
		int thirdW = width / 3;

		// Left third: dark gradient (darkest to median)
		for (int i = 0; i < mid; i++)
		{
			int idx = i;
			int xStart = i * thirdW / mid;
			int xEnd = (i + 1) * thirdW / mid;
			g.setColor(colors[idx]);
			g.fillRect(xStart, 0, xEnd - xStart, height);
		}

		// Middle third: median color
		g.setColor(colors[mid]);
		g.fillRect(thirdW, 0, width - 2 * thirdW, height);

		// Right third: light gradient (median to brightest)
		for (int i = 0; i < mid; i++)
		{
			int idx = mid + 1 + i;
			int xStart = width - thirdW + i * thirdW / mid;
			int xEnd = width - thirdW + (i + 1) * thirdW / mid;
			g.setColor(colors[idx]);
			g.fillRect(xStart, 0, xEnd - xStart, height);
		}
	}
}
