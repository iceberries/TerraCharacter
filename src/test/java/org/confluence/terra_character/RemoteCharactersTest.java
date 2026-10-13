package org.confluence.terra_character;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundTag;

import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

class RemoteCharactersTest {
	@TempDir Path root;

	private CharacterProfile profile(UUID id, CharacterDifficulty difficulty) {
		return new CharacterProfile(
				id,
				"Remote",
				difficulty,
				new CharacterAppearance("terra_character:wide", "terra_character:short", Map.of()),
				CharacterProfile.Status.ACTIVE);
	}

	@Test
	void identicalCharacterIdsOnDifferentAccountsNeverShareAssets() throws Exception {
		var p = profile(UUID.randomUUID(), CharacterDifficulty.SOFTCORE);
		var a = RemoteCharacters.store(root, UUID.randomUUID());
		var b = RemoteCharacters.store(root, UUID.randomUUID());
		try (var lock = RemoteCharacters.prepare(a, p)) {
			var document = a.read(p.id());
			document.getCompound("Journey").putInt("Research", 7);
			a.commit(p.id(), document, 0);
			try (var other = RemoteCharacters.prepare(b, p)) {
				assertTrue(b.read(p.id()).getCompound("Journey").isEmpty());
			}
		}
		assertNotEquals(a.file(p.id()), b.file(p.id()));
		assertEquals(7, a.read(p.id()).getCompound("Journey").getInt("Research"));
	}

	@Test
	void clientPortableStateWinsOverStaleServerAssetsDifficultyAndDeath() throws Exception {
		var p = profile(UUID.randomUUID(), CharacterDifficulty.HARDCORE);
		var store = RemoteCharacters.store(root, UUID.randomUUID());
		try (var lock = RemoteCharacters.prepare(store, p)) {
			var d = store.read(p.id());
			d.getCompound("Journey").putBoolean("Flight", true);
			store.commit(p.id(), d, 0);
		}
		var changed = profile(p.id(), CharacterDifficulty.JOURNEY);
		try (var lock = RemoteCharacters.prepare(store, changed)) {
			assertEquals(
					CharacterDifficulty.JOURNEY, store.find(p.id()).orElseThrow().difficulty());
			assertFalse(store.read(p.id()).getCompound("Journey").getBoolean("Flight"));
		}
		AtomicNbt.write(store.directory(p.id()).resolve("dead.nbt"), new CompoundTag(), false);
		try (var lock = RemoteCharacters.prepare(store, changed)) {
			assertEquals(CharacterProfile.Status.ACTIVE, store.find(p.id()).orElseThrow().status());
		}
		var dead = ProfileCodec.character(changed);
		dead.putString("Status", "DEAD");
		assertThrows(IOException.class, () -> RemoteCharacters.metadata(dead));
		try (var lock = store.lock(p.id())) {
			assertTrue(lock.isValid());
		}
	}

	@Test
	void metadataEnvelopeRejectsExtraSectionsAndOversizedAppearance() throws Exception {
		var p = profile(UUID.randomUUID(), CharacterDifficulty.SOFTCORE);
		var metadata = ProfileCodec.character(p);
		assertEquals(p, RemoteCharacters.metadata(metadata));
		metadata.put("Adapters", new CompoundTag());
		assertThrows(IOException.class, () -> RemoteCharacters.metadata(metadata));
		metadata.remove("Adapters");
		metadata.getCompound("Appearance").putString("Body", "x:" + "a".repeat(256));
		assertThrows(IOException.class, () -> RemoteCharacters.metadata(metadata));
	}

	@Test
	void switchingCharactersReleasesAndReacquiresIndependentLocks() throws Exception {
		var store = RemoteCharacters.store(root, UUID.randomUUID());
		var a = profile(UUID.randomUUID(), CharacterDifficulty.SOFTCORE);
		var b = profile(UUID.randomUUID(), CharacterDifficulty.MEDIUMCORE);
		try (var lock = RemoteCharacters.prepare(store, a)) {
			assertThrows(IOException.class, () -> RemoteCharacters.prepare(store, a));
		}
		try (var lock = RemoteCharacters.prepare(store, b)) {
			assertEquals(
					CharacterDifficulty.MEDIUMCORE, store.find(b.id()).orElseThrow().difficulty());
		}
		try (var lock = RemoteCharacters.prepare(store, a)) {
			assertTrue(lock.isValid());
		}
	}

	@Test
	void obsoleteOfflineCheckpointCannotRewindSharedWorldOrCharacter() throws Exception {
		var store = new CharacterStore(root.resolve("characters"));
		var p = profile(UUID.randomUUID(), CharacterDifficulty.SOFTCORE);
		store.create(p);
		Path saves = root.resolve("saves"), world = saves.resolve("X");
		Files.createDirectories(world);
		Files.writeString(world.resolve("blocks.txt"), "before");
		SessionCheckpoint.begin(store, p.id(), world);
		Files.writeString(world.resolve("blocks.txt"), "guest changes");
		var owner = new CompoundTag();
		owner.putUUID("Character", p.id());
		AtomicNbt.write(WorldStore.directory(world).resolve("session.nbt"), owner, false);
		try (var lock = store.lock(p.id())) {
			var d = store.read(p.id());
			d.getCompound("Journey").putBoolean("Flight", true);
			store.commit(p.id(), d, 0);
		}
		WorldStore.markShared(world);
		SessionCheckpoint.recover(store, p.id(), saves);
		assertEquals("guest changes", Files.readString(world.resolve("blocks.txt")));
		assertTrue(store.read(p.id()).getCompound("Journey").getBoolean("Flight"));
		assertFalse(Files.exists(SessionCheckpoint.marker(store, p.id())));
		assertFalse(Files.exists(WorldStore.directory(world).resolve("session.nbt")));
	}
}
