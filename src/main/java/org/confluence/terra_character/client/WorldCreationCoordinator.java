package org.confluence.terra_character.client;

import net.minecraft.client.gui.screens.worldselection.*;
import net.neoforged.neoforge.common.NeoForge;

import org.confluence.terra_character.api.integration.ConfigureWorldEvent;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.integration.AdapterRegistry;
import org.confluence.terra_character.runtime.CharacterSession;

import java.io.IOException;
import java.util.*;

public final class WorldCreationCoordinator {
	private static final Map<CreateWorldScreen, WorldProfile> DRAFTS = new WeakHashMap<>();

	private WorldCreationCoordinator() {}

	public static void configure(CreateWorldScreen screen) throws IOException {
		if (ClientProfiles.getSelected() == null) return;
		var profile = ClientProfiles.selectedProfile();
		var state = screen.getUiState();
		if (state.isDebug())
			throw new IOException("Debug worlds do not support character sessions");
		state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
		state.setAllowCommands(false);
		String preset =
				state.getWorldType().preset() == null
						? "minecraft:normal"
						: state.getWorldType()
								.preset()
								.unwrapKey()
								.map(k -> k.location().toString())
								.orElse("minecraft:normal");
		var settings =
				new WorldSettings(
						state.getSeed(),
						preset,
						profile.difficulty() == CharacterDifficulty.JOURNEY
								? WorldDifficulty.JOURNEY
								: WorldDifficulty.valueOf(state.getDifficulty().name()),
						ClientProfiles.getWorldOptions());
		AdapterRegistry.validateWorld(settings);
		var event =
				new ConfigureWorldEvent(
						settings, state.getSettings().options(), state.getDifficulty());
		NeoForge.EVENT_BUS.post(event);
		state.setDifficulty(event.difficulty());
		state.setSettings(state.getSettings().withOptions(old -> event.options()));
		if (settings.difficulty() != WorldDifficulty.JOURNEY)
			settings =
					new WorldSettings(
							settings.seed(),
							settings.preset(),
							WorldDifficulty.valueOf(event.difficulty().name()),
							settings.extensions());
		DRAFTS.put(screen, new WorldProfile(UUID.randomUUID(), state.getName(), settings));
	}

	public static void arm(CreateWorldScreen screen) throws IOException {
		if (ClientProfiles.getSelected() == null) return;
		var world = DRAFTS.get(screen);
		if (world == null) throw new IOException("Missing character world configuration");
		CharacterSession.arm(
				ClientProfiles.store(),
				ClientProfiles.getSelected(),
				ClientProfiles.saves().resolve(screen.getUiState().getTargetFolder()),
				world);
	}
}
