package io.huze.glamourer.npc;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.TileObject;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
class InstanceDirector
{
	private final Client client;
	private final EventBus eventBus;
	private final RenderCallbackManager renderCallbackManager;
	private final NpcSheet npcSheet;
	private final Map<NPC, GlamouredInstance> instances = new IdentityHashMap<>();
	/// Actors whose draws should be suppressed
	private final Set<Actor> hidden = Collections.newSetFromMap(new IdentityHashMap<>());
	/// Actors whose draws were actually suppressed
	private final Set<Actor> drawnByClient = Collections.newSetFromMap(new IdentityHashMap<>());

	private final RenderCallback renderCallback = new RenderCallback()
	{
		@Override
		public boolean drawObject(Scene scene, TileObject object)
		{
			if (hidden.isEmpty())
			{
				return true;
			}
			Actor actor = getActorBehind(object);
			if (actor instanceof NPC && hidden.contains(actor))
			{
				drawnByClient.add(actor);
				return false;
			}
			return true;
		}
	};

	void startUp()
	{
		eventBus.register(this);
		renderCallbackManager.register(renderCallback);
	}

	void shutDown()
	{
		eventBus.unregister(this);
		renderCallbackManager.unregister(renderCallback);
	}

	void beginFrame()
	{
		hidden.clear();
		drawnByClient.clear();
	}

	void drawInstanceFor(NPC npc, NpcGlamour glamour)
	{
		GlamouredInstance instance = instances.get(npc);
		if (instance == null)
		{
			instance = new GlamouredInstance(client, npc, npcSheet, () -> drawnByClient.contains(npc));
			instances.put(npc, instance);
		}

		if (instance.update(glamour))
		{
			hidden.add(npc);
		}
	}

	void endFrame()
	{
		for (Map.Entry<NPC, GlamouredInstance> entry : instances.entrySet())
		{
			if (!hidden.contains(entry.getKey()))
			{
				entry.getValue().setActive(false);
			}
		}
	}

	void hideAllInstances()
	{
		for (GlamouredInstance instance : instances.values())
		{
			instance.setActive(false);
		}
	}

	void destroyAllInstances()
	{
		hidden.clear();
		drawnByClient.clear();
		hideAllInstances();
		instances.clear();
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		GlamouredInstance instance = instances.remove(event.getNpc());
		if (instance != null)
		{
			instance.setActive(false);
		}
		hidden.remove(event.getNpc());
	}

	@Nullable
	private Actor getActorBehind(TileObject object)
	{
		if (object instanceof GameObject)
		{
			Renderable renderable = ((GameObject) object).getRenderable();
			if (renderable instanceof Actor)
			{
				return (Actor) renderable;
			}
		}
		return null;
	}
}
