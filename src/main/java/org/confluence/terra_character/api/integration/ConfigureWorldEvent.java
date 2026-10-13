package org.confluence.terra_character.api.integration;

import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.bus.api.Event;

import org.confluence.terra_character.api.model.WorldSettings;

import java.util.Objects;

/** Fired before vanilla world creation; optional integrations may resolve their own options. */
public final class ConfigureWorldEvent extends Event {
	private final WorldSettings settings;
	private WorldOptions options;
	private Difficulty difficulty;

	public ConfigureWorldEvent(
			WorldSettings settings, WorldOptions options, Difficulty difficulty) {
		this.settings = Objects.requireNonNull(settings, "settings");
		this.options = Objects.requireNonNull(options, "options");
		this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
	}

	public WorldSettings settings() {
		return settings;
	}

	public WorldOptions options() {
		return options;
	}

	public void options(WorldOptions value) {
		options = Objects.requireNonNull(value, "options");
	}

	public Difficulty difficulty() {
		return difficulty;
	}

	public void difficulty(Difficulty value) {
		difficulty = Objects.requireNonNull(value, "difficulty");
	}
}
