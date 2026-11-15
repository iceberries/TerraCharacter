package org.confluence.character_portal.runtime;

import org.confluence.character_portal.api.model.WorldProfile;
import org.confluence.character_portal.storage.CharacterStore;

import java.nio.file.Path;
import java.util.UUID;

public record LaunchRequest(
		CharacterStore store,
		UUID character,
		Path worldDirectory,
		WorldProfile world,
		CharacterStore.IdentityLock lock) {}
