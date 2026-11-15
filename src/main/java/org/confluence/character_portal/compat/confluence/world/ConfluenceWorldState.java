package org.confluence.character_portal.compat.confluence.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.confluence.character_portal.api.model.WorldSettings;
import org.confluence.character_portal.runtime.CharacterSession;
import org.confluence.character_portal.storage.WorldStore;

import java.io.IOException;

public final class ConfluenceWorldState {
	private static volatile WorldSettings runningWorld;

	private ConfluenceWorldState() {}

	public static void start(MinecraftServer server) throws IOException {
		var session = CharacterSession.current();
		runningWorld = session != null
				? session.world().settings()
				: WorldStore.read(server.getWorldPath(LevelResource.ROOT))
						.map(world -> world.settings())
						.orElse(null);
	}

	public static WorldSettings runningWorld() {
		return runningWorld;
	}

	public static void clear() {
		runningWorld = null;
	}
}
