package org.confluence.terra_character.compat.confluence.integration;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;

import org.confluence.mod.common.attachment.*;
import org.confluence.mod.common.item.common.PermanentUpgrade;
import org.confluence.mod.util.PlayerUtils;
import org.confluence.terra_character.api.integration.CharacterDataAdapter;
import org.confluence.terra_character.compat.confluence.mixin.ManaMixin;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public final class AttachmentAdapter<T extends INBTSerializable<CompoundTag>>
		implements CharacterDataAdapter {
	private final String name;
	private final Supplier<AttachmentType<T>> type;
	private final Function<ServerPlayer, T> fresh;

	public AttachmentAdapter(String name, Supplier<AttachmentType<T>> type, Supplier<T> fresh) {
		this(name, type, player -> fresh.get());
	}

	public AttachmentAdapter(
			String name, Supplier<AttachmentType<T>> type, Function<ServerPlayer, T> fresh) {
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
		return player.getData(type.get()).serializeNBT(player.registryAccess()).copy();
	}

	@Override
	public void initialize(ServerPlayer player) {
		player.setData(type.get(), fresh.apply(player));
	}

	@Override
	public void restore(ServerPlayer player, CompoundTag data, int version) {
		var value = fresh.apply(player);
		value.deserializeNBT(player.registryAccess(), data.copy());
		player.setData(type.get(), value);
		if (value instanceof ManaStorage)
			((ManaMixin) (Object) value)
					.terraCharacter$setCurrentMana(data.getFloat("currentMana"));
		if (value instanceof ExtraInventory inventory) {
			// The upstream decoder currently encodes AccessoryDye instead of decoding it.
			var dyes = data.getList("AccessoryDye", Tag.TAG_COMPOUND);
			for (int i = 0; i < dyes.size(); i++)
				inventory.setAccessoryDye(
						i,
						ItemStack.OPTIONAL_CODEC
								.parse(
										player.registryAccess()
												.createSerializationContext(NbtOps.INSTANCE),
										dyes.getCompound(i))
								.getOrThrow());
		}
	}

	@Override
	public Set<String> legacyAttachmentIds() {
		return Set.of("confluence:" + name);
	}

	@Override
	public void afterClone(ServerPlayer original, ServerPlayer replacement) {
		// NeoForge's default serialization copy uses the upstream ExtraInventory dye decoder.
		// Copy through the ownership adapter so respawn uses the same corrected decoder as world
		// entry.
		restore(replacement, capture(original), schemaVersion());
	}

	@Override
	public Optional<CompoundTag> importLegacy(CompoundTag player) {
		var attachments = player.getCompound("neoforge:attachments");
		return attachments.contains("confluence:" + name)
				? Optional.of(attachments.getCompound("confluence:" + name).copy())
				: Optional.empty();
	}

	@Override
	public void onDeath(ServerPlayer player, boolean keep) {
		if (!keep && player.getData(type.get()) instanceof ExtraInventory inventory) {
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				var stack = inventory.removeItemNoUpdate(i);
				if (!stack.isEmpty()) player.drop(stack, true);
			}
			inventory.setChanged();
		}
	}

	@Override
	public void afterRestore(ServerPlayer player) {
		if (player.getData(type.get()) instanceof EverBeneficial) {
			PermanentUpgrade.restore(player);
		}
		if (name.equals("mana")) {
			PlayerUtils.flushPrimitiveValueData(player);
			PlayerUtils.syncSavedData(player);
		}
	}
}
