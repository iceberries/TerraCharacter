package org.confluence.character_portal.integration;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.resources.RegistryOps;

/** Describes effective datapack registries and codecs; vanilla remains the world-save authority. */
public final class WorldStateAdapter {
	private WorldStateAdapter() {}

	public static CompoundTag describe(RegistryAccess registries) {
		var result = EnvironmentAdapters.snapshot();
		var entries = new CompoundTag();
		registries.registries().forEach(entry -> entries.put(entry.key().location().toString(),
				EnvironmentAdapters.strings(entry.value().keySet().stream().map(Object::toString).toList())));
		result.put("Registries", entries);
		return result;
	}

	public static CompoundTag creation(RegistryAccess registries, WorldOptions options,
			WorldDimensions dimensions, WorldDataConfiguration packs, GameRules rules) {
		var result = describe(registries);
		var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
		result.put("WorldGenSettings", WorldGenSettings.encode(ops, options, dimensions).getOrThrow());
		result.put("DataConfiguration", WorldDataConfiguration.CODEC.encodeStart(NbtOps.INSTANCE, packs).getOrThrow());
		result.put("GameRules", rules.createTag());
		return result;
	}

	public static CompoundTag runtime(MinecraftServer server) {
		var result = describe(server.registryAccess());
		var world = server.getWorldData();
		result.putString("LevelName", world.getLevelName());
		result.putString("Difficulty", world.getDifficulty().getKey());
		result.putBoolean("Hardcore", world.isHardcore());
		result.putBoolean("AllowCommands", world.isAllowCommands());
		result.put("WorldOptions", WorldOptions.CODEC.codec().encodeStart(NbtOps.INSTANCE, world.worldGenOptions()).getOrThrow());
		result.put("GameRules", server.getWorldData().getGameRules().createTag());
		result.put("DataConfiguration", WorldDataConfiguration.CODEC.encodeStart(
				NbtOps.INSTANCE, server.getWorldData().getDataConfiguration()).getOrThrow());
		var dimensions = new CompoundTag();
		for (var level : server.getAllLevels()) {
			var state = new CompoundTag();
			state.putLong("DayTime", level.getDayTime());
			state.putLong("GameTime", level.getGameTime());
			state.putBoolean("Raining", level.isRaining());
			state.putBoolean("Thundering", level.isThundering());
			state.putLong("Spawn", level.getSharedSpawnPos().asLong());
			dimensions.put(level.dimension().location().toString(), state);
		}
		result.put("Dimensions", dimensions);
		return result;
	}
}
