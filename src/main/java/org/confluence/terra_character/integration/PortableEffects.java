package org.confluence.terra_character.integration;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/** Keeps unavailable status effects until the owning mod can decode them again. */
public final class PortableEffects {
	public static final String DEFERRED = "UnresolvedEffects";
	private static final List<String> KEYS = List.of("active_effects", "ActiveEffects");

	private PortableEffects() {}

	public static CompoundTag prepare(CompoundTag player, HolderLookup.Provider registries) {
		var ops = registries.createSerializationContext(NbtOps.INSTANCE);
		return prepare(player, effect -> MobEffectInstance.CODEC.parse(ops, effect).result().isPresent());
	}

	static CompoundTag prepare(CompoundTag player, Predicate<CompoundTag> decodes) {
		var previous = player.getCompound(DEFERRED);
		var deferred = new CompoundTag();
		player.remove(DEFERRED);
		for (String key : KEYS) {
			var entries = new LinkedHashSet<Tag>(player.getList(key, Tag.TAG_COMPOUND));
			entries.addAll(previous.getList(key, Tag.TAG_COMPOUND));
			var applied = new LinkedHashSet<String>();
			var usable = new ListTag();
			var unresolved = new ListTag();
			for (var tag : entries) {
				var effect = (CompoundTag) tag;
				String id = effect.getString("id");
				if (applied.contains(id)) continue;
				try {
					if (decodes.test(effect)) {
						applied.add(id);
						usable.add(effect.copy());
						continue;
					}
				} catch (RuntimeException | LinkageError ex) {
					// Third-party codecs can fail before returning a DataResult.
				}
				unresolved.add(effect.copy());
			}
			unresolved.removeIf(tag -> applied.contains(((CompoundTag) tag).getString("id")));
			if (player.contains(key) || !usable.isEmpty()) player.put(key, usable);
			if (!unresolved.isEmpty()) deferred.put(key, unresolved);
		}
		return deferred;
	}
}
