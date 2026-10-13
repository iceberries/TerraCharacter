package org.confluence.terra_character.api.model;

import java.util.Objects;
import java.util.UUID;

/** Metadata only; inventory and attachment payloads belong to a separate versioned snapshot. */
public record CharacterProfile(
		UUID id,
		String name,
		CharacterDifficulty difficulty,
		CharacterAppearance appearance,
		Status status)
		implements Profile {
	public CharacterProfile {
		Objects.requireNonNull(id, "id");
		name = ProfileValidation.name(name);
		Objects.requireNonNull(difficulty, "difficulty");
		Objects.requireNonNull(appearance, "appearance");
		Objects.requireNonNull(status, "status");
		if (status == Status.DEAD && difficulty != CharacterDifficulty.HARDCORE) {
			throw new IllegalArgumentException("Only hardcore characters can be permanently dead");
		}
	}

	public enum Status {
		ACTIVE,
		DEAD
	}
}
