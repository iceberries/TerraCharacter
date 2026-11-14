package org.confluence.terra_character.integration;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/** Keeps undecodable stacks out of the live inventory without losing their serialized data. */
public final class PortableInventory {
	public static final String DEFERRED = "UnresolvedItems";
	private static final List<String> CONTAINERS = List.of("Inventory", "EnderItems");

	private PortableInventory() {}

	public static CompoundTag prepare(CompoundTag player, HolderLookup.Provider registries) {
		return prepare(player, stack -> decodes(stack, registries));
	}

	static CompoundTag prepare(CompoundTag player, Predicate<CompoundTag> decodes) {
		var previous = player.getCompound(DEFERRED);
		var deferred = new CompoundTag();
		player.remove(DEFERRED);
		for (String container : CONTAINERS) {
			var usable = new ListTag();
			var unresolved = new ListTag();
			var occupied = new HashSet<Integer>();
			for (Tag element : player.getList(container, Tag.TAG_COMPOUND)) {
				var stack = (CompoundTag) element;
				if (usableSlot(container, stack) && !occupied.contains(slot(stack))
						&& decodes.test(stack)) {
					usable.add(stack);
					occupied.add(slot(stack));
				} else {
					unresolved.add(stack.copy());
				}
			}
			for (Tag element : previous.getList(container, Tag.TAG_COMPOUND)) {
				var stack = (CompoundTag) element;
				if (usableSlot(container, stack) && !occupied.contains(slot(stack))
						&& decodes.test(stack)) {
					usable.add(stack.copy());
					occupied.add(slot(stack));
				} else {
					unresolved.add(stack.copy());
				}
			}
			if (player.contains(container) || !usable.isEmpty()) player.put(container, usable);
			if (!unresolved.isEmpty()) deferred.put(container, unresolved);
		}
		return deferred;
	}

	private static int slot(CompoundTag stack) {
		return stack.getByte("Slot") & 255;
	}

	private static boolean usableSlot(String container, CompoundTag stack) {
		if (!stack.contains("Slot", Tag.TAG_BYTE)) return false;
		int slot = slot(stack);
		return container.equals("Inventory")
				? slot < 36 || (slot >= 100 && slot < 104) || slot == 150
				: slot < 27;
	}

	private static boolean decodes(CompoundTag stack, HolderLookup.Provider registries) {
		try {
			return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), stack)
					.result().filter(item -> !item.isEmpty()).isPresent();
		} catch (RuntimeException | LinkageError ex) {
			return false;
		}
	}
}
