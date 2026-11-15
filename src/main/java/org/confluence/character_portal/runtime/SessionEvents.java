package org.confluence.character_portal.runtime;

import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.confluence.character_portal.CharacterPortal;
import org.confluence.character_portal.compat.confluence.world.ConfluenceWorldState;
import org.confluence.character_portal.integration.EnvironmentAdapters;
import org.confluence.character_portal.integration.WorldStateAdapter;
import org.confluence.character_portal.network.CharacterHandshake;
import org.confluence.character_portal.storage.WorldStore;

import java.io.IOException;

@EventBusSubscriber(modid = CharacterPortal.MODID)
public final class SessionEvents {
	private SessionEvents() {}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void starting(ServerAboutToStartEvent event) {
		try {
			EnvironmentAdapters.ensureDiscovered();
			CharacterSession.start(event.getServer());
			if (ModList.get().isLoaded("confluence")) {
				ConfluenceWorldState.start(event.getServer());
			}
		} catch (IOException | RuntimeException ex) {
			CharacterSession.stop();
			throw new IllegalStateException("Cannot start character session", ex);
		}
	}

	@SubscribeEvent
	public static void environmentLoaded(ServerStartedEvent event) {
		try {
			WorldStore.environment(event.getServer().getWorldPath(LevelResource.ROOT),
					"Runtime", WorldStateAdapter.runtime(event.getServer()));
		} catch (IOException | RuntimeException ex) {
			CharacterPortal.LOGGER.error("Cannot record the world environment", ex);
		}
	}

	@SubscribeEvent
	public static void stopping(ServerStoppingEvent event) {
		event.getServer().getPlayerList().getPlayers().forEach(CharacterSession::saveActive);
	}

	@SubscribeEvent
	public static void stopped(ServerStoppedEvent event) {
		try {
			CharacterSession.stop();
		} finally {
			if (ModList.get().isLoaded("confluence")) {
				ConfluenceWorldState.clear();
			}
		}
	}

	@SubscribeEvent
	public static void tick$Post(ServerTickEvent.Post event) {
		CharacterHandshake.sweep(event.getServer());
	}
}
