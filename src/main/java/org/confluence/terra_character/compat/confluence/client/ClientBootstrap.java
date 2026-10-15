package org.confluence.terra_character.compat.confluence.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

import org.confluence.terra_character.client.WorldEditors;

/** Loaded only by the optional integration, and only on a physical client. */
public final class ClientBootstrap {
	private ClientBootstrap() {}

	public static void initialize(IEventBus bus) {
		bus.addListener(PortraitCapabilities::register);
		NeoForge.EVENT_BUS.register(ClientIntegration.class);
		NeoForge.EVENT_BUS.register(PortraitIntegration.class);
		WorldEditors.setIntegration(ConfluenceOptionsScreen::new);
	}
}
