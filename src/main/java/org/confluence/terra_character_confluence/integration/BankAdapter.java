package org.confluence.terra_character_confluence.integration;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;

import org.confluence.lib.common.PlayerContainer;
import org.confluence.mod.common.attachment.PlayerPiggyBankContainer;
import org.confluence.terra_character.api.integration.CharacterDataAdapter;

import java.util.*;
import java.util.function.Supplier;

public final class BankAdapter<T extends PlayerContainer<?>> implements CharacterDataAdapter {
	private final String name;
	private final Supplier<AttachmentType<T>> type;
	private final Supplier<T> fresh;

	public BankAdapter(String name, Supplier<AttachmentType<T>> type, Supplier<T> fresh) {
		this.name = name;
		this.type = type;
		this.fresh = fresh;
	}

	@Override
	public ResourceLocation id() {
		return ResourceLocation.fromNamespaceAndPath("terra_character_confluence", name);
	}

	@Override
	public int schemaVersion() {
		return 1;
	}

	@Override
	public Scope scope() {
		return Scope.CHARACTER;
	}

	@Override
	public CompoundTag capture(ServerPlayer player) {
		var tag = new CompoundTag();
		tag.put("Items", player.getData(type.get()).serializeNBT(player.registryAccess()));
		return tag;
	}

	@Override
	public void initialize(ServerPlayer player) {
		player.setData(type.get(), fresh.get());
	}

	@Override
	public void restore(ServerPlayer player, CompoundTag data, int version) {
		var bank = fresh.get();
		var items = data.getList("Items", Tag.TAG_COMPOUND);
		for (var element : items)
			if ((((CompoundTag) element).getByte("Slot") & 255) >= bank.getContainerSize())
				throw new IllegalStateException("Unsupported private container slot");
		bank.deserializeNBT(player.registryAccess(), items);
		player.setData(type.get(), bank);
	}

	@Override
	public void afterRestore(ServerPlayer player) {
		if (player.getData(type.get()) instanceof PlayerPiggyBankContainer bank) {
			bank.setOwner(player);
			bank.setChanged();
		}
	}

	@Override
	public Set<String> legacyAttachmentIds() {
		return Set.of("confluence:" + name);
	}

	@Override
	public Optional<CompoundTag> importLegacy(CompoundTag player) {
		var attachments = player.getCompound("neoforge:attachments");
		if (!attachments.contains("confluence:" + name)) return Optional.empty();
		var tag = new CompoundTag();
		tag.put("Items", attachments.getList("confluence:" + name, Tag.TAG_COMPOUND).copy());
		return Optional.of(tag);
	}
}
