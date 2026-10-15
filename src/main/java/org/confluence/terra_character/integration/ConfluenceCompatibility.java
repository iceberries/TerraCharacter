package org.confluence.terra_character.integration;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

import org.confluence.terra_character.TerraCharacter;

/** This entry point must load without resolving any Confluence or Curios class. */
public final class ConfluenceCompatibility {
	private ConfluenceCompatibility() {}

	public static void initialize(IEventBus bus) {
		if (!ModList.get().isLoaded("confluence")) {
			TerraCharacter.LOGGER.info(
					"Confluence integration inactive: Confluence is not installed");
			return;
		}
		try {
			Class.forName("org.confluence.terra_character.compat.confluence.TerraCharacterConfluence")
					.getMethod("initialize", IEventBus.class)
					.invoke(null, bus);
			TerraCharacter.LOGGER.info("Built-in Confluence character integration activated");
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(
					"Cannot initialize built-in Confluence character integration", ex);
		}
	}
}
