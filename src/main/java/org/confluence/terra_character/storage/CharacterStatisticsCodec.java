package org.confluence.terra_character.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import org.confluence.terra_character.api.model.CharacterStatistics;
import org.confluence.terra_character.integration.VanillaDataAdapter;

import java.io.IOException;
import java.util.Set;

public final class CharacterStatisticsCodec {
	private static final Set<String> KEYS =
			Set.of("Health", "MaxHealth", "HasMana", "Mana", "MaxMana", "PlayTicks");

	private CharacterStatisticsCodec() {}

	public static CompoundTag encode(CharacterStatistics value) {
		var tag = new CompoundTag();
		tag.putFloat("Health", value.health());
		tag.putFloat("MaxHealth", value.maxHealth());
		tag.putBoolean("HasMana", value.hasMana());
		tag.putFloat("Mana", value.mana());
		tag.putFloat("MaxMana", value.maxMana());
		tag.putLong("PlayTicks", value.playTicks());
		return tag;
	}

	public static CharacterStatistics read(CompoundTag document) {
		if (document.contains("Statistics", Tag.TAG_COMPOUND)) {
			var value = document.getCompound("Statistics");
			return new CharacterStatistics(
					value.getFloat("Health"),
					value.getFloat("MaxHealth"),
					value.getBoolean("HasMana"),
					value.getFloat("Mana"),
					value.getFloat("MaxMana"),
					value.getLong("PlayTicks"));
		}
		var vanilla =
				document.getCompound("Adapters")
						.getCompound(VanillaDataAdapter.PORTABLE)
						.getCompound("Data");
		float health =
				vanilla.contains("Health", Tag.TAG_ANY_NUMERIC) ? vanilla.getFloat("Health") : 20;
		if (!Float.isFinite(health) || health < 0) health = 20;
		float maximum = Math.max(20, health);
		if (document.getCompound("Profile").getString("Status").equals("DEAD")) health = 0;
		return new CharacterStatistics(health, maximum, false, 0, 0, 0);
	}

	public static void validate(CompoundTag document) throws IOException {
		if (!document.contains("Statistics")) return; // Existing bundles remain readable.
		if (!document.contains("Statistics", Tag.TAG_COMPOUND))
			throw new IOException("Invalid statistics section");
		var value = document.getCompound("Statistics");
		if (!value.getAllKeys().equals(KEYS)
				|| !value.contains("PlayTicks", Tag.TAG_LONG)
				|| !value.contains("HasMana", Tag.TAG_BYTE))
			throw new IOException("Incomplete character statistics");
		for (String key : Set.of("Health", "MaxHealth", "Mana", "MaxMana"))
			if (!value.contains(key, Tag.TAG_FLOAT))
				throw new IOException("Invalid character vital: " + key);
		try {
			read(document);
		} catch (IllegalArgumentException ex) {
			throw new IOException("Invalid character statistics", ex);
		}
	}
}
