package io.huze.glamourer.glam;

import lombok.AllArgsConstructor;
import net.runelite.client.util.AsyncBufferedImage;

@AllArgsConstructor
final class IconPending
{
	final AsyncBufferedImage image;
	final GlamState state;
}
