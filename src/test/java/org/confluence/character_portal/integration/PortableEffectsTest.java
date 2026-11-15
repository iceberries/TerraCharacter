package org.confluence.character_portal.integration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableEffectsTest {
	@Test
	void preservesUnavailableEffectsAndRestoresThemAlongsideCurrentEffects() {
		var player = new CompoundTag();
		var speed = effect("minecraft:speed", 40);
		var sunscreen = effect("vampirism:sunscreen", 100);
		player.put("active_effects", effects(speed, sunscreen));

		var deferred = PortableEffects.prepare(player, speed::equals);
		assertEquals(effects(speed), player.getList("active_effects", Tag.TAG_COMPOUND));
		assertEquals(effects(sunscreen), deferred.getList("active_effects", Tag.TAG_COMPOUND));

		player.put(PortableEffects.DEFERRED, deferred);
		assertTrue(PortableEffects.prepare(player, effect -> true).isEmpty());
		assertEquals(effects(speed, sunscreen), player.getList("active_effects", Tag.TAG_COMPOUND));
		assertFalse(player.contains(PortableEffects.DEFERRED));
	}

	@Test
	void usesTheCurrentInstanceWhenAnEffectAlreadyExists() {
		var player = new CompoundTag();
		var deferred = new CompoundTag();
		var current = effect("vampirism:sunscreen", 60);
		player.put("active_effects", effects(current));
		deferred.put("active_effects", effects(effect("vampirism:sunscreen", 100)));
		player.put(PortableEffects.DEFERRED, deferred);

		assertTrue(PortableEffects.prepare(player, effect -> true).isEmpty());
		assertEquals(effects(current), player.getList("active_effects", Tag.TAG_COMPOUND));
	}

	private static CompoundTag effect(String id, int duration) {
		var effect = new CompoundTag();
		effect.putString("id", id);
		effect.putInt("duration", duration);
		return effect;
	}

	private static ListTag effects(CompoundTag... entries) {
		var list = new ListTag();
		for (var entry : entries) list.add(entry);
		return list;
	}
}
