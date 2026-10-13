package org.confluence.terra_character.api.model;

import java.util.Objects;
import java.util.UUID;

/** Describes a world, without claiming that its Minecraft save has already been generated. */
public record WorldProfile(UUID id, String name, WorldSettings settings) implements Profile {
	public WorldProfile {
		Objects.requireNonNull(id, "id");
		name = ProfileValidation.name(name);
		Objects.requireNonNull(settings, "settings");
	}
}
