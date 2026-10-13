package org.confluence.terra_character.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

import java.util.Objects;

/** Optional integrations register client factories without introducing a core dependency. */
public final class WorldEditors {
	private WorldEditors() {}

	public interface Factory {
		Screen create(Screen back, CreateWorldScreen creation);
	}

	private static volatile Factory integration;

	public static Factory getIntegration() {
		return integration;
	}

	public static synchronized void setIntegration(Factory factory) {
		if (integration != null)
			throw new IllegalStateException("World editor factory is already registered");
		integration = Objects.requireNonNull(factory, "factory");
	}
}
