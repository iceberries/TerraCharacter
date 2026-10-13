package org.confluence.terra_character_confluence.integration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import org.confluence.mod.common.attachment.PlayerSpecialData;
import org.confluence.mod.common.init.ModAttachmentTypes;
import org.confluence.terra_character.api.integration.CharacterDataAdapter;
import org.confluence.terra_character.integration.VanillaDataAdapter;

import java.util.*;

public final class SpecialDataAdapter implements CharacterDataAdapter {
	private final Scope scope;

	public SpecialDataAdapter(Scope scope) {
		this.scope = scope;
	}

	@Override
	public ResourceLocation id() {
		return ResourceLocation.fromNamespaceAndPath(
				"terra_character_confluence", scope == Scope.CHARACTER ? "quests" : "world_quests");
	}

	@Override
	public int schemaVersion() {
		return 1;
	}

	@Override
	public Scope scope() {
		return scope;
	}

	private CompoundTag select(CompoundTag tag) {
		return VanillaDataAdapter.select(
				tag,
				scope == Scope.CHARACTER
						? List.of("AnglerQuestCount")
						: List.of("CompletedAnglerQuestDay"));
	}

	@Override
	public CompoundTag capture(ServerPlayer player) {
		return select(PlayerSpecialData.of(player).serializeNBT(player.registryAccess()));
	}

	@Override
	public void restore(ServerPlayer player, CompoundTag data, int version) {
		var state = PlayerSpecialData.of(player);
		var base = state.serializeNBT(player.registryAccess());
		base.merge(select(data));
		state.deserializeNBT(player.registryAccess(), base);
		state.removeCurrentQuestedFish();
	}

	@Override
	public void initialize(ServerPlayer player) {
		if (scope == Scope.CHARACTER)
			player.setData(ModAttachmentTypes.SPECIAL_DATA.get(), new PlayerSpecialData());
	}

	@Override
	public Set<String> legacyAttachmentIds() {
		return Set.of("confluence:special_data");
	}

	@Override
	public Optional<CompoundTag> importLegacy(CompoundTag player) {
		return Optional.of(
				select(
						player.getCompound("neoforge:attachments")
								.getCompound("confluence:special_data")));
	}
}
