package org.confluence.character_portal.compat.confluence.client;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.Difficulty;

import org.confluence.character_portal.client.ClientProfiles;
import org.confluence.character_portal.compat.confluence.world.ConfluenceWorldOptions;

public final class ClientIntegration {
	private ClientIntegration() {}

	public static void configureWorld(CreateWorldScreen creation, boolean bind) {
		ClientProfiles.defaultWorldOption(ConfluenceWorldOptions.DIFFICULTY_KEY, "classic");
		ClientProfiles.defaultWorldOption(ConfluenceWorldOptions.EVIL_KEY, "random");
		if (!bind) {
			return;
		}
		var state = creation.getUiState();
		state.setDifficulty(
				switch (ClientProfiles.getWorldOption(
						ConfluenceWorldOptions.DIFFICULTY_KEY, "classic")) {
					case "master" -> Difficulty.HARD;
					case "expert" -> Difficulty.NORMAL;
					default -> Difficulty.EASY;
				});
		state.addListener(changed -> ClientProfiles.setWorldOption(
				ConfluenceWorldOptions.DIFFICULTY_KEY,
				switch (changed.getDifficulty()) {
					case HARD -> "master";
					case NORMAL -> "expert";
					default -> "classic";
				}));
	}
}
