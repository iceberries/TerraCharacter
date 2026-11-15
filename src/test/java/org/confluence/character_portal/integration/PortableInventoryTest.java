package org.confluence.character_portal.integration;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class PortableInventoryTest {
	@Test
	void defersUnknownItemsAndInvalidComponentsWithoutDroppingOriginalData() {
		var missing = stack("removed_mod:blade", 0);
		var invalid = stack("minecraft:diamond_sword", 100);
		var components = new CompoundTag();
		components.putString("minecraft:damage", "old-format");
		invalid.put("components", components);
		var player = new CompoundTag();
		var valid = stack("minecraft:stone", 1);
		player.put("Inventory", items(missing, valid, invalid));
		player.put("EnderItems", items(missing.copy()));

		var deferred = PortableInventory.prepare(player, valid::equals);
		assertEquals(items(valid), player.getList("Inventory", Tag.TAG_COMPOUND));
		assertTrue(player.getList("EnderItems", Tag.TAG_COMPOUND).isEmpty());
		assertEquals(items(missing, invalid), deferred.getList("Inventory", Tag.TAG_COMPOUND));
		assertEquals(items(missing), deferred.getList("EnderItems", Tag.TAG_COMPOUND));
		player.put(PortableInventory.DEFERRED, deferred.copy());
		assertEquals(deferred, PortableInventory.prepare(player, valid::equals));
	}

	@Test
	void restoresACompatibleStackOnlyWhenItsOriginalSlotIsFree() {
		var player = new CompoundTag();
		var deferred = new CompoundTag();
		var recovered = stack("minecraft:diamond", 3);
		deferred.put("Inventory", items(recovered));
		player.put("Inventory", items(stack("minecraft:stone", 3)));
		player.put(PortableInventory.DEFERRED, deferred);
		assertEquals(deferred, PortableInventory.prepare(player, stack -> true));

		player.remove("Inventory");
		player.put(PortableInventory.DEFERRED, deferred);
		assertTrue(PortableInventory.prepare(player, stack -> true).isEmpty());
		assertEquals(items(recovered), player.getList("Inventory", Tag.TAG_COMPOUND));
	}

	private static CompoundTag stack(String id, int slot) {
		var stack = new CompoundTag();
		stack.putString("id", id);
		stack.putInt("count", 1);
		stack.putByte("Slot", (byte) slot);
		return stack;
	}

	private static ListTag items(CompoundTag... stacks) {
		var result = new ListTag();
		for (var stack : stacks) result.add(stack);
		return result;
	}
}
