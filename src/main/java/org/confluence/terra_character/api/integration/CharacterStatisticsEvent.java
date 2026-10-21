package org.confluence.terra_character.api.integration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import org.confluence.terra_character.api.model.CharacterStatistics;

import java.util.Objects;
import java.util.Optional;

/** Optional integrations supply vitals; no player is present when reading an old offline bundle. */
public final class CharacterStatisticsEvent extends Event {
	private final ServerPlayer player;
	private final CompoundTag adapters;
	private CharacterStatistics statistics;

	public CharacterStatisticsEvent(
			ServerPlayer player, CompoundTag adapters, CharacterStatistics statistics) {
		this.player = player;
		this.adapters = Objects.requireNonNull(adapters, "adapters").copy();
		this.statistics = Objects.requireNonNull(statistics, "statistics");
	}

	public Optional<ServerPlayer> getPlayer() {
		return Optional.ofNullable(player);
	}

	public CompoundTag getAdapterData(String id) {
		return adapters.getCompound(id).getCompound("Data").copy();
	}

	public CharacterStatistics getStatistics() {
		return statistics;
	}

	public void setMana(float current, float maximum) {
		statistics =
				new CharacterStatistics(
						statistics.health(),
						statistics.maxHealth(),
						true,
						current,
						maximum,
						statistics.playTicks());
	}
}
