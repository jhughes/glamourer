package io.huze.glamourer.glam;

import io.huze.glamourer.color.ColorReplacement;
import io.huze.glamourer.item.DedupeItemComposition;
import io.huze.glamourer.item.ItemSheet;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.ItemComposition;
import net.runelite.api.SpritePixels;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
public class Glamour
{
	private final DedupeItemComposition itemComposition;
	private final GlamState original;
	private final GlamState staged;
	@Getter
	private AsyncBufferedImage image;

	public GlamourData getData()
	{
		// item composition might be edited at this point, must use the original glamstate as the dedupekey
		return new GlamourData(
			original.toDedupeKey(itemComposition.getMembersName()),
			getColorReplacements().toArray(new ColorReplacement[0]),
			getReplacementModelId());
	}

	public static Glamour load(ItemSheet sheet, DedupeItemComposition itemComposition, GlamourData data,
							   Client client, ClientThread clientThread, BooleanSupplier isCacheResetPending)
	{
		var glamour = new Glamour(sheet, itemComposition);
		// Restore saved colors by matching on original color
		var savedPairs = data.getColorReplacements();
		if (savedPairs != null)
		{
			for (ColorReplacement saved : savedPairs)
			{
				int i = 0;
				for (var pair : glamour.staged.getColorReplacements())
				{
					if (pair.getOriginal() == saved.getOriginal())
					{
						glamour.replaceIndex(i, saved.getReplacement());
					}
					i++;
				}
			}
		}
		// TODO this looks like a job for the icon service
		glamour.image = glamour.loadImage(client, clientThread, isCacheResetPending);
		return glamour;
	}

	Glamour(ItemSheet sheet, DedupeItemComposition itemComposition)
	{
		this.itemComposition = itemComposition;
		original = GlamState.backup(itemComposition);
		staged = GlamState.initialize(itemComposition, sheet.getModels(itemComposition.getId()));
	}

	private AsyncBufferedImage loadImage(Client client, ClientThread clientThread, BooleanSupplier isCacheResetPending)
	{
		AsyncBufferedImage img = new AsyncBufferedImage(clientThread, Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		clientThread.invoke(() ->
		{
			// Wait for cache reset to complete before creating sprite
			if (isCacheResetPending.getAsBoolean())
			{
				return false;
			}
			SpritePixels sprite = createItemSprite(client);
			if (sprite == null)
			{
				return false;
			}
			sprite.toBufferedImage(img);
			img.loaded();
			return true;
		});
		return img;
	}

	private SpritePixels createItemSprite(Client client)
	{
		return client.createItemSprite(
			itemComposition.getId(),
			10000,
			1,
			SpritePixels.DEFAULT_SHADOW_COLOR,
			ItemQuantityMode.NEVER,
			false,
			Constants.CLIENT_DEFAULT_ZOOM
		);
	}

	public Collection<Integer> getItemIds()
	{
		return itemComposition.getIds();
	}

	public String getItemName()
	{
		return itemComposition.getMembersName();
	}

	protected void apply(ItemComposition itemComposition)
	{
		staged.applyTo(itemComposition);
	}

	protected void apply(Client client, ClientThread clientThread, BooleanSupplier isCacheResetPending)
	{
		staged.applyTo(itemComposition);
		image = loadImage(client, clientThread, isCacheResetPending);
	}

	protected void revert()
	{
		original.applyTo(itemComposition);
	}

	public Integer getReplacementModelId()
	{
		if (original.getModel() != staged.getModel())
		{
			return staged.getModel();
		}
		return null;
	}

	public void replaceIndex(int index, short after)
	{
		staged.replace(index, after);
	}

	public List<ColorReplacement> getColorReplacements()
	{
		return staged.getColorReplacements();
	}

	public List<ColorReplacement> getColorReplacementsForUI()
	{
		List<ColorReplacement> colorReplacements = new ArrayList<>();
		for (var stagedReplacement : staged.getColorReplacements())
		{
			var originalHsl = stagedReplacement.getOriginal();
			for (var originalReplacement : original.getColorReplacements())
			{
				if (originalReplacement.getOriginal() == originalHsl)
				{
					originalHsl = originalReplacement.getReplacement();
				}
			}
			colorReplacements.add(new ColorReplacement(originalHsl, stagedReplacement.getReplacement()));
		}
		return colorReplacements;
	}
}
