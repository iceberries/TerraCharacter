package org.confluence.character_portal.integration;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

import org.confluence.character_portal.CharacterPortal;

/** This entry point must load without resolving any Confluence or Curios class. */
public final class ConfluenceCompatibility {
	private ConfluenceCompatibility() {}

	public static void initialize(IEventBus bus) {
		if (!ModList.get().isLoaded("confluence")) {
			CharacterPortal.LOGGER.info(
					"Confluence integration inactive: Confluence is not installed");
			return;
		}
		try {
			Class.forName("org.confluence.character_portal.compat.confluence.CharacterPortalConfluence")
					.getMethod("initialize", IEventBus.class)
					.invoke(null, bus);
			CharacterPortal.LOGGER.info("Built-in Confluence character integration activated");
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(
					"Cannot initialize built-in Confluence character integration", ex);
		}
	}
}
