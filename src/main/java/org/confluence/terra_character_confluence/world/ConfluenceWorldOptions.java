package org.confluence.terra_character_confluence.world;

import org.confluence.terra_character.api.model.WorldSettings;

import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Confluence-only options; WorldEvents applies generation flags and DifficultyMixin applies combat
 * difficulty.
 */
public record ConfluenceWorldOptions(Difficulty difficulty, Evil evil) {
	public static final String DIFFICULTY_KEY = "terra_character_confluence:world_difficulty";
	public static final String EVIL_KEY = "terra_character_confluence:evil";

	public ConfluenceWorldOptions {
		Objects.requireNonNull(difficulty, "difficulty");
		Objects.requireNonNull(evil, "evil");
	}

	/** Adds compatibility metadata while preserving vanilla settings and unrelated options. */
	public WorldSettings attachTo(WorldSettings base) {
		var options = new HashMap<>(base.extensions());
		options.put(DIFFICULTY_KEY, difficulty.name().toLowerCase(Locale.ROOT));
		options.put(EVIL_KEY, evil.name().toLowerCase(Locale.ROOT));
		return new WorldSettings(base.seed(), base.preset(), base.difficulty(), options);
	}

	public static Optional<ConfluenceWorldOptions> readFrom(WorldSettings settings) {
		var options = settings.extensions();
		if (!options.containsKey(DIFFICULTY_KEY) && !options.containsKey(EVIL_KEY)) {
			return Optional.empty();
		}
		if (!options.containsKey(DIFFICULTY_KEY) || !options.containsKey(EVIL_KEY)) {
			throw new IllegalArgumentException("Incomplete Confluence world options");
		}
		return Optional.of(
				new ConfluenceWorldOptions(
						Difficulty.valueOf(options.get(DIFFICULTY_KEY).toUpperCase(Locale.ROOT)),
						Evil.valueOf(options.get(EVIL_KEY).toUpperCase(Locale.ROOT))));
	}

	/** Legendary is derived from special seed rules, not independently selected. */
	public enum Difficulty {
		CLASSIC,
		EXPERT,
		MASTER
	}

	public enum Evil {
		RANDOM,
		CORRUPTION,
		CRIMSON
	}
}
