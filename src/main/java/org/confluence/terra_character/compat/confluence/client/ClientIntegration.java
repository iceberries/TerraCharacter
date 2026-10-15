package org.confluence.terra_character.compat.confluence.client;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.Difficulty;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import org.confluence.terra_character.client.*;
import org.confluence.terra_character.compat.confluence.world.ConfluenceWorldOptions;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class ClientIntegration {
	private static final Set<CreateWorldScreen> BOUND =
			Collections.newSetFromMap(new WeakHashMap<>());

	@SubscribeEvent
	public static void screen(ScreenEvent.Init.Post event) {
		if (event.getScreen() instanceof CreateWorldScreen creation
				&& ClientProfiles.getSelected() != null) {
			ClientProfiles.defaultWorldOption(ConfluenceWorldOptions.DIFFICULTY_KEY, "classic");
			ClientProfiles.defaultWorldOption(ConfluenceWorldOptions.EVIL_KEY, "random");
			if (BOUND.add(creation)) {
				var state = creation.getUiState();
				state.setDifficulty(
						switch (ClientProfiles.getWorldOption(
								ConfluenceWorldOptions.DIFFICULTY_KEY, "classic")) {
							case "master" -> Difficulty.HARD;
							case "expert" -> Difficulty.NORMAL;
							default -> Difficulty.EASY;
						});
				state.addListener(
						changed ->
								ClientProfiles.setWorldOption(
										ConfluenceWorldOptions.DIFFICULTY_KEY,
										switch (changed.getDifficulty()) {
											case HARD -> "master";
											case NORMAL -> "expert";
											default -> "classic";
										}));
			}
		}
	}
}
