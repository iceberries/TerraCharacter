package org.confluence.terra_character.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import org.confluence.terra_character.api.model.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public final class ProfileCodec {
	public static final int VERSION = 1;

	private ProfileCodec() {}

	public static CompoundTag character(CharacterProfile profile) {
		var tag = new CompoundTag();
		tag.putUUID("Id", profile.id());
		tag.putString("Name", profile.name());
		tag.putString("Difficulty", profile.difficulty().name());
		tag.putString("Status", profile.status().name());
		var appearance = new CompoundTag();
		appearance.putString("Body", profile.appearance().bodyStyle());
		appearance.putString("Hair", profile.appearance().hairStyle());
		var colors = new CompoundTag();
		profile.appearance().colors().forEach(colors::putInt);
		appearance.put("Colors", colors);
		if (!profile.appearance().paint().isEmpty())
			appearance.putIntArray("Paint", profile.appearance().paint().pixels());
		var parts = new CompoundTag();
		profile.appearance()
				.wardrobe()
				.parts()
				.forEach((slot, id) -> parts.putString(slot.name(), id));
		if (!parts.isEmpty()) appearance.put("Parts", parts);
		var masks = new CompoundTag();
		profile.appearance()
				.dyeMasks()
				.regions()
				.forEach((slot, pixels) -> masks.putIntArray(slot.name(), pixels));
		if (!masks.isEmpty()) appearance.put("DyeMasks", masks);
		if (!profile.appearance().baseSkin().isEmpty()) {
			var base = new CompoundTag();
			base.putBoolean("Slim", profile.appearance().baseSkin().slim());
			base.putIntArray("Pixels", profile.appearance().baseSkin().pixels());
			appearance.put("BaseSkin", base);
		}
		tag.put("Appearance", appearance);
		return tag;
	}

	public static CharacterProfile character(CompoundTag tag) throws IOException {
		require(tag, "Appearance", Tag.TAG_COMPOUND);
		for (String key : List.of("Name", "Difficulty", "Status"))
			require(tag, key, Tag.TAG_STRING);
		require(tag.getCompound("Appearance"), "Colors", Tag.TAG_COMPOUND);
		for (String key : List.of("Body", "Hair"))
			require(tag.getCompound("Appearance"), key, Tag.TAG_STRING);
		for (String key : tag.getCompound("Appearance").getCompound("Colors").getAllKeys())
			require(tag.getCompound("Appearance").getCompound("Colors"), key, Tag.TAG_INT);
		try {
			var a = tag.getCompound("Appearance");
			var colors = new HashMap<String, Integer>();
			a.getCompound("Colors")
					.getAllKeys()
					.forEach(key -> colors.put(key, a.getCompound("Colors").getInt(key)));
			SkinPaint paint = SkinPaint.EMPTY;
			if (a.contains("Paint")) {
				require(a, "Paint", Tag.TAG_INT_ARRAY);
				paint = new SkinPaint(a.getIntArray("Paint"));
			}
			var parts = new EnumMap<WardrobeSlot, String>(WardrobeSlot.class);
			if (a.contains("Parts")) {
				require(a, "Parts", Tag.TAG_COMPOUND);
				for (String key : a.getCompound("Parts").getAllKeys()) {
					require(a.getCompound("Parts"), key, Tag.TAG_STRING);
					parts.put(WardrobeSlot.valueOf(key), a.getCompound("Parts").getString(key));
				}
			}
			var masks = new EnumMap<WardrobeSlot, List<Integer>>(WardrobeSlot.class);
			if (a.contains("DyeMasks")) {
				require(a, "DyeMasks", Tag.TAG_COMPOUND);
				for (String key : a.getCompound("DyeMasks").getAllKeys()) {
					require(a.getCompound("DyeMasks"), key, Tag.TAG_INT_ARRAY);
					masks.put(
							WardrobeSlot.valueOf(key),
							Arrays.stream(a.getCompound("DyeMasks").getIntArray(key))
									.boxed()
									.toList());
				}
			}
			SkinBase base = SkinBase.EMPTY;
			if (a.contains("BaseSkin")) {
				require(a, "BaseSkin", Tag.TAG_COMPOUND);
				var skin = a.getCompound("BaseSkin");
				require(skin, "Slim", Tag.TAG_BYTE);
				require(skin, "Pixels", Tag.TAG_INT_ARRAY);
				if (!Set.of("Slim", "Pixels").containsAll(skin.getAllKeys())
						|| skin.getIntArray("Pixels").length != 4096)
					throw new IllegalArgumentException("Invalid base skin");
				base = new SkinBase(skin.getBoolean("Slim"), skin.getIntArray("Pixels"));
			}
			return new CharacterProfile(
					tag.getUUID("Id"),
					tag.getString("Name"),
					CharacterDifficulty.valueOf(tag.getString("Difficulty")),
					new CharacterAppearance(
							a.getString("Body"),
							a.getString("Hair"),
							colors,
							paint,
							new Wardrobe(parts),
							new DyeMasks(masks),
							base),
					CharacterProfile.Status.valueOf(tag.getString("Status")));
		} catch (RuntimeException ex) {
			throw new IOException("Invalid character metadata", ex);
		}
	}

	public static CompoundTag world(WorldProfile profile) {
		var tag = new CompoundTag();
		tag.putUUID("Id", profile.id());
		tag.putString("Name", profile.name());
		tag.putString("Seed", profile.settings().seed());
		tag.putString("Preset", profile.settings().preset());
		tag.putString("Difficulty", profile.settings().difficulty().name());
		var options = new CompoundTag();
		profile.settings().extensions().forEach(options::putString);
		tag.put("Extensions", options);
		return tag;
	}

	public static WorldProfile world(CompoundTag tag) throws IOException {
		require(tag, "Extensions", Tag.TAG_COMPOUND);
		for (String key : List.of("Name", "Seed", "Preset", "Difficulty"))
			require(tag, key, Tag.TAG_STRING);
		for (String key : tag.getCompound("Extensions").getAllKeys())
			require(tag.getCompound("Extensions"), key, Tag.TAG_STRING);
		try {
			var options = new HashMap<String, String>();
			tag.getCompound("Extensions")
					.getAllKeys()
					.forEach(key -> options.put(key, tag.getCompound("Extensions").getString(key)));
			return new WorldProfile(
					tag.getUUID("Id"),
					tag.getString("Name"),
					new WorldSettings(
							tag.getString("Seed"),
							tag.getString("Preset"),
							WorldDifficulty.valueOf(tag.getString("Difficulty")),
							options));
		} catch (RuntimeException ex) {
			throw new IOException("Invalid world metadata", ex);
		}
	}

	public static void version(CompoundTag tag) throws IOException {
		if (!tag.contains("FormatVersion", 3) || tag.getInt("FormatVersion") != VERSION) {
			throw new IOException(
					"Unsupported or missing save version: " + tag.getInt("FormatVersion"));
		}
	}

	private static void require(CompoundTag tag, String key, int type) throws IOException {
		if (!tag.contains(key, type))
			throw new IOException("Missing or invalid metadata field: " + key);
	}
}
