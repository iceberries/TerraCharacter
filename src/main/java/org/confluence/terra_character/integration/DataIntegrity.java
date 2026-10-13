package org.confluence.terra_character.integration;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.List;

public final class DataIntegrity {
	private DataIntegrity() {}

	public static void validate(Tag tag, HolderLookup.Provider registries) throws IOException {
		if (tag instanceof CompoundTag compound) {
			for (String effects : List.of("active_effects", "ActiveEffects")) {
				for (Tag value : compound.getList(effects, Tag.TAG_COMPOUND)) {
					var effect = (CompoundTag) value;
					if (effect.contains("id", Tag.TAG_STRING)) {
						var id = ResourceLocation.tryParse(effect.getString("id"));
						if (id == null || !BuiltInRegistries.MOB_EFFECT.containsKey(id))
							throw new IOException("Missing effect: " + effect.getString("id"));
					}
				}
			}
			if (compound.contains("id", 8)
					&& (compound.contains("count") || compound.contains("Count"))) {
				var id = ResourceLocation.tryParse(compound.getString("id"));
				if (id == null || !BuiltInRegistries.ITEM.containsKey(id))
					throw new IOException("Missing item: " + compound.getString("id"));
				if (registries != null && compound.contains("count")) {
					var parsed =
							ItemStack.CODEC.parse(
									registries.createSerializationContext(NbtOps.INSTANCE),
									compound);
					if (parsed.error().isPresent())
						throw new IOException(
								"Unresolved item data: " + parsed.error().get().message());
				}
			}
			for (String key : compound.getAllKeys()) validate(compound.get(key), registries);
		} else if (tag instanceof ListTag list) {
			for (Tag entry : list) validate(entry, registries);
		}
	}
}
