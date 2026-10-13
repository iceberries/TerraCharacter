package org.confluence.terra_character;

import static org.junit.jupiter.api.Assertions.*;

import org.confluence.terra_character.api.model.CharacterAppearance;
import org.confluence.terra_character.api.model.CharacterDifficulty;
import org.confluence.terra_character.api.model.CharacterProfile;
import org.confluence.terra_character.api.model.Profile;
import org.confluence.terra_character.api.model.WorldDifficulty;
import org.confluence.terra_character.api.model.WorldProfile;
import org.confluence.terra_character.api.model.WorldSettings;
import org.confluence.terra_character.api.repository.ProfileRepository;
import org.confluence.terra_character.api.session.LaunchSelection;
import org.confluence.terra_character.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

class ProfileFrameworkTest {
	private static CharacterAppearance appearance() {
		return new CharacterAppearance(
				"terra_character:default", "terra_character:default", Map.of());
	}

	private static WorldSettings settings(WorldDifficulty difficulty) {
		return new WorldSettings("123", "minecraft:normal", difficulty);
	}

	static Stream<Object[]> difficultyPairs() {
		return Stream.of(CharacterDifficulty.values())
				.flatMap(
						character ->
								Stream.of(WorldDifficulty.values())
										.map(world -> new Object[] {character, world}));
	}

	@ParameterizedTest
	@MethodSource("difficultyPairs")
	void journeyPairingIsIndependentOfDeathAndCombatDifficulty(
			CharacterDifficulty characterMode, WorldDifficulty worldMode) {
		var character =
				new CharacterProfile(
						UUID.randomUUID(),
						"Player",
						characterMode,
						appearance(),
						CharacterProfile.Status.ACTIVE);
		var world = new WorldProfile(UUID.randomUUID(), "World", settings(worldMode));
		if ((characterMode == CharacterDifficulty.JOURNEY)
				== (worldMode == WorldDifficulty.JOURNEY)) {
			assertDoesNotThrow(() -> new LaunchSelection(character, world));
		} else {
			assertThrows(
					IllegalArgumentException.class, () -> new LaunchSelection(character, world));
		}
	}

	@Test
	void deadHardcoreCharacterCannotEnterAnyWorld() {
		var character =
				new CharacterProfile(
						UUID.randomUUID(),
						"Player",
						CharacterDifficulty.HARDCORE,
						appearance(),
						CharacterProfile.Status.DEAD);
		for (WorldDifficulty difficulty : WorldDifficulty.values()) {
			assertThrows(
					IllegalArgumentException.class,
					() ->
							new LaunchSelection(
									character,
									new WorldProfile(
											UUID.randomUUID(), "World", settings(difficulty))));
		}
	}

	@Test
	void sameNamedCharactersAndWorldsRemainIndependent() throws IOException {
		var characters = new MemoryRepository<CharacterProfile>();
		var worlds = new MemoryRepository<WorldProfile>();
		var service = new ProfileService(characters, worlds);
		var a = service.createCharacter("Same", CharacterDifficulty.SOFTCORE, appearance());
		var b = service.createCharacter("Same", CharacterDifficulty.MEDIUMCORE, appearance());
		var x = service.createWorldProfile("Same", settings(WorldDifficulty.NORMAL));
		var y = service.createWorldProfile("Same", settings(WorldDifficulty.HARD));
		assertNotEquals(a.id(), b.id());
		assertNotEquals(x.id(), y.id());
		assertEquals(a, service.prepareSelection(a.id(), x.id()).character());
		assertEquals(a, service.prepareSelection(a.id(), y.id()).character());
		assertEquals(b, service.prepareSelection(b.id(), x.id()).character());
		assertEquals(x, service.prepareSelection(a.id(), x.id()).world());
		assertEquals(x, service.prepareSelection(b.id(), x.id()).world());
		assertEquals(2, characters.list().size());
		assertEquals(2, worlds.list().size());
		assertEquals("123", y.settings().seed());
	}

	@Test
	void unknownIdentityDoesNotCreateAFallbackProfile() throws IOException {
		var characters = new MemoryRepository<CharacterProfile>();
		var worlds = new MemoryRepository<WorldProfile>();
		var service = new ProfileService(characters, worlds);
		var character =
				service.createCharacter("Player", CharacterDifficulty.SOFTCORE, appearance());
		var world = service.createWorldProfile("World", settings(WorldDifficulty.NORMAL));
		assertThrows(
				IllegalArgumentException.class,
				() -> service.prepareSelection(UUID.randomUUID(), world.id()));
		assertThrows(
				IllegalArgumentException.class,
				() -> service.prepareSelection(character.id(), UUID.randomUUID()));
		assertEquals(1, characters.list().size());
		assertEquals(1, worlds.list().size());
	}

	@Test
	void creationFailureIsReturnedToCaller() {
		var failure = new IOException("Disk full");
		var characters =
				new MemoryRepository<CharacterProfile>() {
					@Override
					public void create(CharacterProfile profile) throws IOException {
						throw failure;
					}
				};
		var service = new ProfileService(characters, new MemoryRepository<WorldProfile>());
		assertSame(
				failure,
				assertThrows(
						IOException.class,
						() ->
								service.createCharacter(
										"Player", CharacterDifficulty.SOFTCORE, appearance())));
		assertTrue(characters.list().isEmpty());
	}

	@Test
	void appearanceOwnsAnImmutableSnapshotOfColors() {
		var colors = new HashMap<>(Map.of("terra_character:hair", 0x123456));
		var appearance =
				new CharacterAppearance(
						"terra_character:default", "terra_character:default", colors);
		colors.put("terra_character:hair", 0);
		assertEquals(0x123456, appearance.colors().get("terra_character:hair"));
		assertThrows(UnsupportedOperationException.class, () -> appearance.colors().clear());
		assertThrows(
				IllegalArgumentException.class,
				() ->
						new CharacterAppearance(
								"terra_character:default",
								"terra_character:default",
								Map.of("terra_character:hair", 0x1000000)));
	}

	@Test
	void invalidCreationDoesNotReachStorage() {
		var characters = new MemoryRepository<CharacterProfile>();
		var worlds = new MemoryRepository<WorldProfile>();
		var service = new ProfileService(characters, worlds);
		assertThrows(
				IllegalArgumentException.class,
				() -> service.createCharacter("  ", CharacterDifficulty.SOFTCORE, appearance()));
		assertThrows(
				IllegalArgumentException.class,
				() -> service.createWorldProfile("line\nbreak", settings(WorldDifficulty.NORMAL)));
		assertTrue(characters.list().isEmpty());
		assertTrue(worlds.list().isEmpty());
	}

	@Test
	void unknownWorldOptionsArePreservedWithoutLoadingTheirProvider() {
		var options = new HashMap<>(Map.of("other_mod:option", "original"));
		var settings =
				new WorldSettings("123", "minecraft:normal", WorldDifficulty.NORMAL, options);
		options.put("other_mod:option", "changed");
		assertEquals("original", settings.extensions().get("other_mod:option"));
		assertThrows(UnsupportedOperationException.class, () -> settings.extensions().clear());
	}

	@Test
	void malformedExtensionOptionsAreRejected() {
		assertThrows(
				IllegalArgumentException.class,
				() ->
						new WorldSettings(
								"123",
								"minecraft:normal",
								WorldDifficulty.NORMAL,
								Map.of("no namespace", "value")));
		assertThrows(
				IllegalArgumentException.class,
				() ->
						new WorldSettings(
								"123",
								"minecraft:normal",
								WorldDifficulty.NORMAL,
								Map.of("other_mod:option", "line\nbreak")));
	}

	/** Test fixture only; production file storage is phase 2.1. */
	private static class MemoryRepository<P extends Profile> implements ProfileRepository<P> {
		private final Map<UUID, P> profiles = new HashMap<>();

		@Override
		public List<P> list() {
			return List.copyOf(profiles.values());
		}

		@Override
		public Optional<P> find(UUID id) {
			return Optional.ofNullable(profiles.get(id));
		}

		@Override
		public void create(P profile) throws IOException {
			if (profiles.putIfAbsent(profile.id(), profile) != null) {
				throw new IOException("Profile already exists: " + profile.id());
			}
		}
	}
}
