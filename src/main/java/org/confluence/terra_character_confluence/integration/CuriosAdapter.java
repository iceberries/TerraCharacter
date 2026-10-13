package org.confluence.terra_character_confluence.integration;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import org.confluence.terra_character.api.integration.CharacterDataAdapter;
import org.confluence.terra_curio.common.attachment.AccessoriesAttachment;
import org.confluence.terra_curio.common.init.TCAttachments;

import top.theillusivec4.curios.api.CuriosApi;

import java.util.*;

public final class CuriosAdapter implements CharacterDataAdapter {
	@Override
	public ResourceLocation id() {
		return ResourceLocation.parse("terra_character_confluence:curios");
	}

	@Override
	public int schemaVersion() {
		return 1;
	}

	@Override
	public Scope scope() {
		return Scope.CHARACTER;
	}

	public static CompoundTag sanitize(CompoundTag data) {
		var out = data.copy();
		for (var element : out.getList("Curios", Tag.TAG_COMPOUND))
			((CompoundTag) element).getCompound("StacksHandler").remove("CachedModifiers");
		return out;
	}

	@Override
	public CompoundTag capture(ServerPlayer player) {
		return sanitize(
				(CompoundTag) CuriosApi.getCuriosInventory(player).orElseThrow().writeTag());
	}

	@Override
	public void initialize(ServerPlayer player) {
		player.setData(TCAttachments.ACCESSORIES.get(), new AccessoriesAttachment());
		var handler = CuriosApi.getCuriosInventory(player).orElseThrow();
		handler.readTag(new CompoundTag());
		handler.reset();
	}

	@Override
	public void restore(ServerPlayer player, CompoundTag data, int version) {
		var handler = CuriosApi.getCuriosInventory(player).orElseThrow();
		for (var element : data.getList("Curios", Tag.TAG_COMPOUND)) {
			var slot = (CompoundTag) element;
			if (!handler.getCurios().containsKey(slot.getString("Identifier")))
				throw new IllegalStateException(
						"Missing Curios slot: " + slot.getString("Identifier"));
		}
		handler.readTag(sanitize(data));
		handler.reset();
	}

	@Override
	public void afterRestore(ServerPlayer player) {
		AccessoriesAttachment.of(player).flushAbility(player);
	}

	@Override
	public Set<String> legacyAttachmentIds() {
		return Set.of("curios:inventory", "terra_curio:accessories");
	}

	@Override
	public Optional<CompoundTag> importLegacy(CompoundTag player) {
		var tag = player.getCompound("neoforge:attachments");
		return tag.contains("curios:inventory")
				? Optional.of(sanitize(tag.getCompound("curios:inventory")))
				: Optional.empty();
	}
}
