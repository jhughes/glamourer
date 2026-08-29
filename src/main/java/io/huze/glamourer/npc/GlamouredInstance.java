package io.huze.glamourer.npc;

import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

@Slf4j
class GlamouredInstance extends RuneLiteObjectController
{
	private final Client client;
	private final NPC npc;
	private final NpcSheet npcSheet;
	private final BooleanSupplier realNpcDrawn;

	@Nullable
	private GlamouredModelData modelData;

	/// The glamour the model was built with
	@Nullable
	private NpcGlamour appliedGlamour;

	/// The last reason a frame failed to draw, logged once per change rather than per frame.
	@Nullable
	private String bailReason;
	private boolean isCenteredOnTile;

	GlamouredInstance(Client client, NPC npc, NpcSheet npcSheet, BooleanSupplier realNpcDrawn)
	{
		this.client = client;
		this.npc = npc;
		this.npcSheet = npcSheet;
		this.realNpcDrawn = realNpcDrawn;
		setRadius(0);
	}

	@Override
	@Nullable
	public Model getModel()
	{
		if (isCenteredOnTile && !realNpcDrawn.getAsBoolean())
		{
			return null;
		}
		if (modelData == null)
		{
			bail("no model");
			return null;
		}

		Model npcModel = npc.getModel();
		if (npcModel == null)
		{
			bail("actor model null");
			return null;
		}
		if (npcModel.getVerticesCount() < modelData.getVerticesCount())
		{
			bail("actor model vertex count mismatch");
			return null;
		}

		return modelData.copyPose(npcModel);
	}

	/// Return true if the director should suppress the actor
	boolean update(NpcGlamour glamour)
	{
		WorldView worldView = npc.getWorldView();
		LocalPoint location = npc.getLocalLocation();
		if (worldView == null || !worldView.contains(location))
		{
			setActive(false);
			return false;
		}

		if (!refreshModel(glamour))
		{
			return false;
		}

		LocalPoint tileCentre = LocalPoint.fromWorld(worldView, npc.getWorldLocation());
		isCenteredOnTile = tileCentre != null
			&& tileCentre.getX() == location.getX() && tileCentre.getY() == location.getY();
		setLocation(location, worldView.getPlane());
		setOrientation(npc.getCurrentOrientation());
		setZ(groundHeight(location, worldView));
		setActive(true);
		return true;
	}

	private boolean refreshModel(NpcGlamour glamour)
	{
		if (modelData != null && glamour.equals(appliedGlamour))
		{
			return true;
		}
		if (modelData == null)
		{
			modelData = GlamouredModelData.load(client, npcSheet, npc.getId());
			if (modelData == null)
			{
				return bail("nothing to build from");
			}
		}

		Model posed = npc.getModel();
		if (posed == null)
		{
			return bail("the actor has no model");
		}
		if (posed.getVerticesCount() < modelData.getVerticesCount())
		{
			return bail("the actor's model has " + posed.getVerticesCount()
				+ " vertices, the rebuild " + modelData.getVerticesCount());
		}

		modelData.applyGlamour(glamour);
		appliedGlamour = glamour;
		bailReason = null;
		return true;
	}

	private boolean bail(String reason)
	{
		if (!reason.equals(bailReason))
		{
			bailReason = reason;
			log.debug("Instance for \"{}\" not drawing: {}", npc.getName(), reason);
		}
		return false;
	}

	void setActive(boolean active)
	{
		if (active)
		{
			client.registerRuneLiteObject(this);
		}
		else
		{
			client.removeRuneLiteObject(this);
		}
	}

	private int groundHeight(LocalPoint location, WorldView worldView)
	{
		int height = Perspective.getFootprintTileHeight(client, location, worldView.getPlane(), npc.getFootprintSize());
		return height - npc.getAnimationHeightOffset();
	}
}
