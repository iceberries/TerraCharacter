package org.confluence.character_portal.client;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.neoforged.neoforge.common.NeoForge;

import org.confluence.character_portal.api.integration.ConfigureWorldEvent;
import org.confluence.character_portal.api.model.CharacterDifficulty;
import org.confluence.character_portal.api.model.WorldDifficulty;
import org.confluence.character_portal.api.model.WorldProfile;
import org.confluence.character_portal.api.model.WorldSettings;
import org.confluence.character_portal.integration.AdapterRegistry;
import org.confluence.character_portal.integration.WorldStateAdapter;
import org.confluence.character_portal.runtime.CharacterSession;
import org.confluence.character_portal.storage.WorldStore;

import java.io.IOException;
import java.util.UUID;

public final class WorldCreationCoordinator {
	private WorldCreationCoordinator() {}

	public interface Access {
		boolean characterPortal$bindWorldOptions();
	}

	public record Draft(UUID characterId, WorldProfile world, CompoundTag environment) {
		public Draft {
			environment = environment.copy();
		}

		@Override
		public CompoundTag environment() {
			return environment.copy();
		}
	}

	private static CompoundTag captureEnvironment(CreateWorldScreen screen) {
		var state = screen.getUiState();
		var context = state.getSettings();
		var effectiveDimensions = new WorldDimensions(
				context.selectedDimensions().bake(context.datapackDimensions()).dimensions());
		return WorldStateAdapter.creation(context.worldgenLoadContext(), context.options(),
				effectiveDimensions, context.dataConfiguration(), state.getGameRules());
	}

	public static Draft configure(CreateWorldScreen screen) throws IOException {
		if (ClientProfiles.getSelected() == null) {
			return null;
		}
		var profile = ClientProfiles.selectedProfile();
		var state = screen.getUiState();
		if (state.isDebug()) {
			throw new IOException("Debug worlds do not support character sessions");
		}
		// Resolve vanilla's nullable default before changing mode (creative defaults to cheats on).
		boolean allowCommands = state.isAllowCommands();
		state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
		state.setAllowCommands(allowCommands);
		String preset =
				state.getWorldType().preset() == null
						? "character_portal:custom"
						: state.getWorldType()
								.preset()
								.unwrapKey()
								.map(k -> k.location().toString())
								.orElse("character_portal:custom");
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
		if (settings.difficulty() != WorldDifficulty.JOURNEY) {
			settings =
					new WorldSettings(
							settings.seed(),
							settings.preset(),
							WorldDifficulty.valueOf(event.difficulty().name()),
							settings.extensions());
		}
		var world = new WorldProfile(UUID.randomUUID(), state.getName(), settings);
		return new Draft(profile.id(), world, captureEnvironment(screen));
	}

	public static void arm(CreateWorldScreen screen, Draft draft) throws IOException {
		var selected = ClientProfiles.getSelected();
		if (selected == null && draft == null) {
			return;
		}
		if (draft == null || !draft.characterId().equals(selected)) {
			throw new IOException("Character selection changed during world creation");
		}
		var directory = ClientProfiles.saves().resolve(screen.getUiState().getTargetFolder());
		CharacterSession.arm(
				ClientProfiles.store(),
				draft.characterId(),
				directory,
				draft.world());
		try {
			WorldStore.environment(directory, "Creation", draft.environment());
		} catch (Exception ex) {
			CharacterSession.cancelPending();
			throw new IOException("Cannot save world creation environment", ex);
		}
	}
}
