package io.huze.glamourer.glam;

import io.huze.glamourer.item.ItemSpotAnimSheet;
import io.huze.glamourer.model.Lighting;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Animation;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

final class GlamouredSpotAnim extends RuneLiteObjectController
{
	private final Client client;
	private final Player player;
	private final Model model;
	private final AnimationController animationController;
	private final int height;
	private final int startCycle;
	private boolean finished;

	private GlamouredSpotAnim(Client client, Player player, Model model, Animation animation, int height, int startCycle)
	{
		this.client = client;
		this.player = player;
		this.model = model;
		this.animationController = new AnimationController(client, animation);
		this.height = height;
		this.startCycle = startCycle;
		animationController.setOnFinished(controller -> finished = true);
	}

	/// True if successfully spawned. Client thread only.
	static boolean spawn(@Nonnull Client client, @Nonnull Player player, @Nonnull ActorSpotAnim spotAnim,
						 @Nonnull ItemSpotAnimSheet.Row definition, @Nonnull Glamour glamour)
	{
		ModelData loaded = client.loadModelData(definition.getModelId());
		Animation animation = definition.getAnimationId() == -1 ? null : client.loadAnimation(definition.getAnimationId());
		if (loaded == null || animation == null)
		{
			return false;
		}
		ModelData data = glamour.getModelEdits().apply(definition.prepare(loaded));
		Model model = Lighting.forActor(definition.getAmbient(), definition.getContrast()).light(data);
		if (model == null)
		{
			return false;
		}
		var standIn = new GlamouredSpotAnim(client, player, model, animation, spotAnim.getHeight(), spotAnim.getStartCycle());
		if (!standIn.follow())
		{
			return false;
		}
		client.registerRuneLiteObject(standIn);
		return true;
	}

	@Override
	public void tick(int ticksSinceLastFrame)
	{
		if (finished || !follow())
		{
			client.removeRuneLiteObject(this);
		}
		else if (isStarted())
		{
			animationController.tick(ticksSinceLastFrame);
		}
	}

	/// Null before the start cycle and once finished, so no pose shows outside the animation.
	@Override
	@Nullable
	public Model getModel()
	{
		return finished || !isStarted() ? null : animationController.animate(model);
	}

	/// False when the player has left the scene.
	private boolean follow()
	{
		WorldView worldView = player.getWorldView();
		LocalPoint location = player.getLocalLocation();
		if (worldView == null || !worldView.contains(location))
		{
			return false;
		}
		setLocation(location, worldView.getPlane());
		setZ(Perspective.getTileHeight(client, location, worldView.getPlane()) - height);
		setOrientation(player.getCurrentOrientation());
		return true;
	}

	private boolean isStarted()
	{
		return client.getGameCycle() >= startCycle;
	}
}
