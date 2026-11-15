package org.confluence.character_portal;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundTag;

import org.confluence.character_portal.api.model.*;
import org.confluence.character_portal.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

class LegacyNamespaceTest {
	@TempDir Path root;

	private static CompoundTag snapshot() {
		var snapshot = new CompoundTag();
		snapshot.putInt("Version", 1);
		snapshot.put("Data", new CompoundTag());
		return snapshot;
	}

	@Test
	void legacyIdsAndAdapterKeysAreRenamedWhenRead() throws Exception {
		var store = new CharacterStore(root.resolve("characters"));
		var profile =
				new CharacterProfile(
						UUID.randomUUID(),
						"terra_character_kid",
						CharacterDifficulty.SOFTCORE,
						new CharacterAppearance(
								"character_portal:wide",
								"character_portal:short",
								Map.of("character_portal:skin", 0x102030)),
						CharacterProfile.Status.ACTIVE);
		store.create(profile);
		var legacy = store.read(profile.id());
		legacy.putInt("FormatVersion", 1);
		var appearance = legacy.getCompound("Profile").getCompound("Appearance");
		appearance.putString("Body", "terra_character:wide");
		appearance.putString("Hair", "terra_character:short");
		var colors = new CompoundTag();
		colors.putInt("terra_character:skin", 0x102030);
		appearance.put("Colors", colors);
		var adapters = new CompoundTag();
		adapters.put("terra_character:vanilla", snapshot());
		legacy.put("Adapters", adapters);
		AtomicNbt.write(store.file(profile.id()), legacy, false);

		var upgraded = store.read(profile.id());
		assertEquals(ProfileCodec.VERSION, upgraded.getInt("FormatVersion"));
		assertTrue(upgraded.getCompound("Adapters").contains("character_portal:vanilla"));
		var decoded = ProfileCodec.character(upgraded.getCompound("Profile"));
		assertEquals("character_portal:wide", decoded.appearance().bodyStyle());
		assertEquals("character_portal:short", decoded.appearance().hairStyle());
		assertEquals(0x102030, decoded.appearance().colors().get("character_portal:skin"));
		assertEquals("terra_character_kid", decoded.name());
	}

	@Test
	void legacyWorldFolderIsTakenOverAndItsSettingsAreRenamed() throws Exception {
		var world = root.resolve("world");
		var legacy = world.resolve("data/" + LegacyNamespace.LEGACY_MODID);
		Files.createDirectories(legacy);
		var document = new CompoundTag();
		document.putInt("FormatVersion", 1);
		document.put(
				"Profile",
				ProfileCodec.world(
						new WorldProfile(
								UUID.randomUUID(),
								"Old world",
								new WorldSettings(
										"123",
										"terra_character:custom",
										WorldDifficulty.NORMAL,
										Map.of(
												"terra_character_confluence:world_difficulty",
												"master")))));
		AtomicNbt.write(legacy.resolve("world.nbt"), document, false);

		var loaded = WorldStore.read(world).orElseThrow();
		assertEquals("character_portal:custom", loaded.settings().preset());
		assertEquals(
				"master",
				loaded.settings()
						.extensions()
						.get("character_portal_confluence:world_difficulty"));
		assertFalse(Files.exists(legacy));
		assertTrue(Files.isDirectory(WorldStore.directory(world)));
	}

	@Test
	void clientDataFolderIsTakenOverOnlyOnce() throws Exception {
		var game = root.resolve("game");
		Files.createDirectories(game.resolve("terra_character/wardrobe/parts"));
		var data = LegacyNamespace.gameData(game);
		assertEquals(game.resolve("character_portal"), data);
		assertTrue(Files.isDirectory(data.resolve("wardrobe/parts")));
		assertFalse(Files.exists(game.resolve("terra_character")));
		assertEquals(data, LegacyNamespace.gameData(game));
	}
}
