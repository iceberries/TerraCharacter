package org.confluence.terra_character.runtime;

import org.confluence.terra_character.api.model.WorldProfile;
import org.confluence.terra_character.storage.CharacterStore;

import java.nio.file.Path;
import java.util.UUID;

public record LaunchRequest(
		CharacterStore store,
		UUID character,
		Path worldDirectory,
		WorldProfile world,
		CharacterStore.IdentityLock lock) {}
