package org.confluence.character_portal.compat.better_experience;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import org.confluence.character_portal.api.integration.CharacterDataAdapter;

import java.util.Optional;
import java.util.Set;

/** Explicitly registered BetterExperience attachment ownership; no hard mod dependency. */
public final class AttachmentAdapter implements CharacterDataAdapter {
	private final ResourceLocation attachment;
	private final Scope scope;

	public AttachmentAdapter(String name, Scope scope) {
		this.attachment = ResourceLocation.fromNamespaceAndPath("better_experience", name);
		this.scope = scope;
	}

	private AttachmentType<?> type() {
		var type = NeoForgeRegistries.ATTACHMENT_TYPES.get(attachment);
		if (type == null) throw new IllegalStateException("Missing BetterExperience attachment: " + attachment);
		return type;
	}

	@SuppressWarnings("unchecked")
	private INBTSerializable<CompoundTag> value(ServerPlayer player) {
		return (INBTSerializable<CompoundTag>) player.getData(type());
	}

	public CompoundTag state(ServerPlayer player) {
		return value(player).serializeNBT(player.registryAccess()).copy();
	}

	@Override
	public ResourceLocation id() {
		return attachment;
	}

	@Override
	public int schemaVersion() {
		return 1;
	}

	@Override
	public Scope scope() {
		return scope;
	}

	@Override
	public Set<String> legacyAttachmentIds() {
		return Set.of(attachment.toString());
	}

	@Override
	public CompoundTag capture(ServerPlayer player) {
		return state(player);
	}

	@Override
	public void initialize(ServerPlayer player) {
		player.removeData(type());
	}

	@Override
	public void restore(ServerPlayer player, CompoundTag data, int version) {
		initialize(player);
		value(player).deserializeNBT(player.registryAccess(), data.copy());
	}

	@Override
	public void afterClone(ServerPlayer original, ServerPlayer replacement) {
		restore(replacement, capture(original), schemaVersion());
	}

	@Override
	public Optional<CompoundTag> importLegacy(CompoundTag player) {
		var attachments = player.getCompound("neoforge:attachments");
		return attachments.contains(attachment.toString())
				? Optional.of(attachments.getCompound(attachment.toString()).copy()) : Optional.empty();
	}
}
