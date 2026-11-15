package org.confluence.character_portal.client;

import net.minecraft.client.Minecraft;

import org.confluence.character_portal.CharacterPortal;
import org.confluence.character_portal.storage.LegacyNamespace;
import org.confluence.character_portal.storage.StarQueue;

import java.io.IOException;

public final class ClientStars {
	private static StarQueue characters;
	private static StarQueue worlds;
	private static IOException characterFailure;
	private static IOException worldFailure;

	private ClientStars() {}

	public static synchronized StarQueue characters() throws IOException {
		if (characterFailure != null) throw characterFailure;
		if (characters == null) {
			try {
				characters = load("characters");
			} catch (IOException ex) {
				characterFailure = ex;
				CharacterPortal.LOGGER.warn("Cannot load character star preferences", ex);
				throw ex;
			}
		}
		return characters;
	}

	public static synchronized StarQueue worlds() throws IOException {
		if (worldFailure != null) throw worldFailure;
		if (worlds == null) {
			try {
				worlds = load("worlds");
			} catch (IOException ex) {
				worldFailure = ex;
				CharacterPortal.LOGGER.warn("Cannot load world star preferences", ex);
				throw ex;
			}
		}
		return worlds;
	}

	private static StarQueue load(String category) throws IOException {
		return new StarQueue(
				LegacyNamespace.gameData(Minecraft.getInstance().gameDirectory.toPath())
						.resolve("stars/" + category + ".json"));
	}
}
