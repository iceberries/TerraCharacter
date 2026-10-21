package org.confluence.terra_character.service;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

import org.confluence.terra_character.api.integration.CharacterStatisticsEvent;
import org.confluence.terra_character.api.model.CharacterStatistics;
import org.confluence.terra_character.storage.CharacterStatisticsCodec;

public final class CharacterStatisticsService {
	private CharacterStatisticsService() {}

	public static CharacterStatistics summary(CompoundTag document) {
		var value = CharacterStatisticsCodec.read(document);
		if (value.hasMana()) return value;
		return NeoForge.EVENT_BUS
				.post(new CharacterStatisticsEvent(null, document.getCompound("Adapters"), value))
				.getStatistics();
	}

	public static CharacterStatistics capture(ServerPlayer player, long ticks) {
		float maximum = player.getMaxHealth();
		var value =
				new CharacterStatistics(
						Math.clamp(player.getHealth(), 0, maximum), maximum, false, 0, 0, ticks);
		return NeoForge.EVENT_BUS
				.post(new CharacterStatisticsEvent(player, new CompoundTag(), value))
				.getStatistics();
	}
}
