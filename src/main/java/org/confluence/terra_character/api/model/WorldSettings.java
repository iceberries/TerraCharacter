package org.confluence.terra_character.api.model;

import java.util.Map;
import java.util.Objects;

/**
 * Raw seed text and namespaced options allow extensions without linking their classes into the
 * core.
 */
public record WorldSettings(
		String seed, String preset, WorldDifficulty difficulty, Map<String, String> extensions) {
	public WorldSettings {
		Objects.requireNonNull(seed, "seed");
		if (seed.length() > 1024 || seed.codePoints().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Invalid seed text");
		}
		preset = ProfileValidation.resourceId(preset);
		Objects.requireNonNull(difficulty, "difficulty");
		extensions = Map.copyOf(Objects.requireNonNull(extensions, "extensions"));
		extensions.forEach(
				(key, value) -> {
					ProfileValidation.resourceId(key);
					if (value.length() > 1024
							|| value.codePoints().anyMatch(Character::isISOControl)) {
						throw new IllegalArgumentException(
								"Invalid world extension option: " + key);
					}
				});
	}

	public WorldSettings(String seed, String preset, WorldDifficulty difficulty) {
		this(seed, preset, difficulty, Map.of());
	}
}
