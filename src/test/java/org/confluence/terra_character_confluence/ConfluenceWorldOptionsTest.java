package org.confluence.terra_character_confluence;

import static org.junit.jupiter.api.Assertions.*;

import org.confluence.terra_character.api.model.WorldDifficulty;
import org.confluence.terra_character.api.model.WorldSettings;
import org.confluence.terra_character_confluence.world.ConfluenceWorldOptions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class ConfluenceWorldOptionsTest {
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
