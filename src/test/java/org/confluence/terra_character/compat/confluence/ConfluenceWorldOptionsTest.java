package org.confluence.terra_character.compat.confluence;

import static org.junit.jupiter.api.Assertions.*;

import org.confluence.terra_character.api.model.WorldDifficulty;
import org.confluence.terra_character.api.model.WorldProfile;
import org.confluence.terra_character.api.model.WorldSettings;
import org.confluence.terra_character.compat.confluence.world.ConfluenceWorldOptions;
import org.confluence.terra_character.storage.WorldStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

class ConfluenceWorldOptionsTest {
	@Test
	void legacyWorldKeysRemainReadableAfterPackageMigration(@TempDir Path directory)
			throws Exception {
		assertEquals(
				"terra_character_confluence:world_difficulty",
				ConfluenceWorldOptions.DIFFICULTY_KEY);
		assertEquals("terra_character_confluence:evil", ConfluenceWorldOptions.EVIL_KEY);
		var legacy =
				new WorldProfile(
						UUID.randomUUID(),
						"Legacy world",
						new WorldSettings(
								"123",
								"minecraft:normal",
								WorldDifficulty.HARD,
								Map.of(
										"terra_character_confluence:world_difficulty",
										"master",
										"terra_character_confluence:evil",
										"crimson")));
		WorldStore.write(directory, legacy);
		var loaded = WorldStore.read(directory).orElseThrow();
		assertEquals(legacy, loaded);
		assertEquals(
				new ConfluenceWorldOptions(
						ConfluenceWorldOptions.Difficulty.MASTER,
						ConfluenceWorldOptions.Evil.CRIMSON),
				ConfluenceWorldOptions.readFrom(loaded.settings()).orElseThrow());
	}

	@Test
	void compatibilityOptionsRoundTripWithoutChangingVanillaOrUnrelatedSettings() {
		var base =
				new WorldSettings(
						"for the worthy | 123",
						"minecraft:normal",
						WorldDifficulty.HARD,
						Map.of("other_mod:option", "keep"));
		var options =
				new ConfluenceWorldOptions(
						ConfluenceWorldOptions.Difficulty.MASTER,
						ConfluenceWorldOptions.Evil.CRIMSON);
		var extended = options.attachTo(base);
		assertEquals(options, ConfluenceWorldOptions.readFrom(extended).orElseThrow());
		assertEquals(base.seed(), extended.seed());
		assertEquals(base.preset(), extended.preset());
		assertEquals(base.difficulty(), extended.difficulty());
		assertEquals("keep", extended.extensions().get("other_mod:option"));
		assertFalse(base.extensions().containsKey(ConfluenceWorldOptions.DIFFICULTY_KEY));
	}

	@Test
	void vanillaWorldDoesNotAcquireConfluenceOptions() {
		var vanilla = new WorldSettings("123", "minecraft:normal", WorldDifficulty.NORMAL);
		assertTrue(ConfluenceWorldOptions.readFrom(vanilla).isEmpty());
		assertTrue(vanilla.extensions().isEmpty());
	}

	@Test
	void incompleteOrUnknownOptionsDoNotSilentlyBecomeDefaults() {
		var partial =
				new WorldSettings(
						"123",
						"minecraft:normal",
						WorldDifficulty.NORMAL,
						Map.of(ConfluenceWorldOptions.DIFFICULTY_KEY, "expert"));
		var unknown =
				new WorldSettings(
						"123",
						"minecraft:normal",
						WorldDifficulty.NORMAL,
						Map.of(
								ConfluenceWorldOptions.DIFFICULTY_KEY,
								"future_mode",
								ConfluenceWorldOptions.EVIL_KEY,
								"random"));
		assertThrows(
				IllegalArgumentException.class, () -> ConfluenceWorldOptions.readFrom(partial));
		assertThrows(
				IllegalArgumentException.class, () -> ConfluenceWorldOptions.readFrom(unknown));
	}
}
