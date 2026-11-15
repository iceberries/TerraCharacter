package org.confluence.character_portal.service;

import org.confluence.character_portal.api.model.CharacterAppearance;
import org.confluence.character_portal.api.model.CharacterDifficulty;
import org.confluence.character_portal.api.model.CharacterProfile;
import org.confluence.character_portal.api.model.WorldProfile;
import org.confluence.character_portal.api.model.WorldSettings;
import org.confluence.character_portal.api.repository.ProfileRepository;
import org.confluence.character_portal.api.session.LaunchSelection;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Coordinates metadata only. Minecraft creation/loading belongs to the integration layer. */
public final class ProfileService {
	private final ProfileRepository<CharacterProfile> characters;
	private final ProfileRepository<WorldProfile> worlds;

	public ProfileService(
			ProfileRepository<CharacterProfile> characters,
			ProfileRepository<WorldProfile> worlds) {
		this.characters = Objects.requireNonNull(characters, "characters");
		this.worlds = Objects.requireNonNull(worlds, "worlds");
	}

	public CharacterProfile createCharacter(
			String name, CharacterDifficulty difficulty, CharacterAppearance appearance)
			throws IOException {
		var profile =
				new CharacterProfile(
						UUID.randomUUID(),
						name,
						difficulty,
						appearance,
						CharacterProfile.Status.ACTIVE);
		characters.create(profile);
		return profile;
	}

	/** Creates metadata, not a Minecraft world save. */
	public WorldProfile createWorldProfile(String name, WorldSettings settings) throws IOException {
		var profile = new WorldProfile(UUID.randomUUID(), name, settings);
		worlds.create(profile);
		return profile;
	}

	public LaunchSelection prepareSelection(UUID characterId, UUID worldId) throws IOException {
		var character =
				characters
						.find(Objects.requireNonNull(characterId, "characterId"))
						.orElseThrow(
								() ->
										new IllegalArgumentException(
												"Unknown character: " + characterId));
		var world =
				worlds.find(Objects.requireNonNull(worldId, "worldId"))
						.orElseThrow(
								() -> new IllegalArgumentException("Unknown world: " + worldId));
		return new LaunchSelection(character, world);
	}
}
