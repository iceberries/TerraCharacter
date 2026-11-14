package org.confluence.terra_character.integration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.confluence.terra_character.api.integration.CharacterDataAdapter;
import java.util.Optional;

/** Unclassified custom player NBT stays with this character's visit to this world. */
public final class PlayerPersistentStateAdapter implements CharacterDataAdapter {
	@Override
	public ResourceLocation id() {
		return ResourceLocation.parse("terra_character:persistent_data");
	}

	@Override
	public int schemaVersion() {
		return 1;
	}

	@Override
	public Scope scope() {
		return Scope.CHARACTER_WORLD;
	}

	@Override
	public CompoundTag capture(ServerPlayer player) {
		return player.getPersistentData().copy();
	}

	@Override
	public void initialize(ServerPlayer player) {
		var data = player.getPersistentData();
		for (String key : java.util.Set.copyOf(data.getAllKeys())) data.remove(key);
	}

	@Override
	public void restore(ServerPlayer player, CompoundTag payload, int version) {
		initialize(player);
		player.getPersistentData().merge(payload.copy());
	}

	@Override
	public Optional<CompoundTag> importLegacy(CompoundTag player) {
		return player.contains("NeoForgeData") ? Optional.of(player.getCompound("NeoForgeData").copy()) : Optional.empty();
	}
}
