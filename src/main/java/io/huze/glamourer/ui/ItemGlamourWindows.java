package io.huze.glamourer.ui;

import io.huze.glamourer.Config;
import io.huze.glamourer.glam.Glamour;
import io.huze.glamourer.glam.ModelViewService;
import io.huze.glamourer.plate.Plate;
import io.huze.glamourer.plate.PlateManager;
import io.huze.glamourer.render.ModelRenderer;
import io.huze.glamourer.texture.TextureImages;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ItemGlamourWindows
{
	private final Client client;
	private final PlateManager plateManager;
	private final ModelViewService modelViewService;
	private final TextureImages textureImages;
	private final Config config;
	private final ConfigManager configManager;
	private final ScheduledExecutorService executor;
	private final ModelRenderer modelRenderer;

	private final Map<String, ItemGlamourFrame> openFrames = new LinkedHashMap<>();

	public void open(@Nonnull Plate plate, @Nonnull Glamour glamour)
	{
		String key = keyOf(plate, glamour.getPrimaryItemId());
		var existing = openFrames.get(key);
		if (existing != null)
		{
			existing.refresh();
			existing.toFront();
			existing.requestFocus();
			return;
		}

		var clientWindow = SwingUtilities.getWindowAncestor(client.getCanvas());
		var frame = new ItemGlamourFrame(clientWindow, plateManager, plate, glamour, modelViewService, textureImages,
			config, configManager, executor, modelRenderer,
			this::rebuildColors,
			() -> openFrames.remove(key));
		openFrames.put(key, frame);
		frame.setVisible(true);
	}

	public void rebuildColors()
	{
		for (var frame : new ArrayList<>(openFrames.values()))
		{
			frame.rebuildColors();
		}
	}

	public void refreshAll()
	{
		var live = plateManager.getPlates();
		for (var frame : new ArrayList<>(openFrames.values()))
		{
			/// Close windows whose plate is no longer live.
			if (live.stream().noneMatch(plate -> plate == frame.getPlate()))
			{
				frame.dispose();
			}
			else
			{
				frame.refresh();
			}
		}
	}

	public void closeAll()
	{
		for (var frame : new ArrayList<>(openFrames.values()))
		{
			frame.dispose();
		}
		openFrames.clear();
	}

	@Nonnull
	private static String keyOf(@Nonnull Plate plate, int itemId)
	{
		return plate.getId() + ":" + itemId;
	}
}
