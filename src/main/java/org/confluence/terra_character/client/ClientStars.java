package org.confluence.terra_character.client;

import net.minecraft.client.Minecraft;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.storage.StarQueue;

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
				TerraCharacter.LOGGER.warn("Cannot load character star preferences", ex);
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
				TerraCharacter.LOGGER.warn("Cannot load world star preferences", ex);
				throw ex;
			}
		}
		return worlds;
	}

	private static StarQueue load(String category) throws IOException {
		return new StarQueue(
				Minecraft.getInstance()
						.gameDirectory
						.toPath()
						.resolve("terra_character/stars/" + category + ".json"));
	}
}
