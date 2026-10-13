package org.confluence.terra_character.api.session;

import org.confluence.terra_character.api.model.CharacterDifficulty;
import org.confluence.terra_character.api.model.CharacterProfile;
import org.confluence.terra_character.api.model.WorldDifficulty;
import org.confluence.terra_character.api.model.WorldProfile;

import java.util.Objects;

/** A validated selection, not a running session or authorization to load player data. */
public record LaunchSelection(CharacterProfile character, WorldProfile world) {
	public LaunchSelection {
		Objects.requireNonNull(character, "character");
		Objects.requireNonNull(world, "world");
		if (character.status() == CharacterProfile.Status.DEAD) {
			throw new IllegalArgumentException("Permanently dead characters cannot enter worlds");
		}
		boolean journeyCharacter = character.difficulty() == CharacterDifficulty.JOURNEY;
		boolean journeyWorld = world.settings().difficulty() == WorldDifficulty.JOURNEY;
		if (journeyCharacter != journeyWorld) {
			throw new IllegalArgumentException("Journey characters and worlds must be paired");
		}
	}
}
