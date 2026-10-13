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

class CharacterStorageTest {
	@TempDir Path root;

	private CharacterProfile profile(CharacterDifficulty difficulty) {
		return new CharacterProfile(
				UUID.randomUUID(),
				"Same",
				difficulty,
				new CharacterAppearance(
						"terra_character:wide",
						"terra_character:short",
						Map.of("terra_character:hair", 0x123456)),
				CharacterProfile.Status.ACTIVE);
	}

	private CharacterStore store() {
		return new CharacterStore(root.resolve("characters"));
	}

	@Test
	void reloadAndArchivePreserveSeparateSameNamedCharacters() throws Exception {
		var store = store();
		var a = profile(CharacterDifficulty.SOFTCORE);
		var b = profile(CharacterDifficulty.MEDIUMCORE);
		store.create(a);
		store.create(b);
		assertEquals(2, new CharacterStore(store.root()).list().size());
		assertEquals(a, store.find(a.id()).orElseThrow());
		store.archive(a.id());
		assertEquals(List.of(b), store.list());
		assertEquals(List.of(a), store.archived());
		store.unarchive(a.id());
		assertEquals(2, store.list().size());
	}

	@Test
	void exclusiveLockAndStaleRevisionRejectConcurrentWriters() throws Exception {
		var store = store();
		var p = profile(CharacterDifficulty.SOFTCORE);
		store.create(p);
		try (var lock = store.lock(p.id())) {
			assertThrows(IOException.class, () -> store.lock(p.id()));
			var old = store.read(p.id());
			store.commit(p.id(), old.copy(), 0);
			assertThrows(IOException.class, () -> store.commit(p.id(), old, 0));
		}
		try (var lock = store.lock(p.id())) {
			assertTrue(lock.isValid());
		}
	}

	@Test
	void permanentDeletionRequiresArchivedIdleCharacterAndKeepsOtherSaves() throws Exception {
		var store = store();
		var a = profile(CharacterDifficulty.SOFTCORE);
		var b = profile(CharacterDifficulty.SOFTCORE);
		store.create(a);
		store.create(b);
		Path world = root.resolve("saves/keep/level.dat");
		Files.createDirectories(world.getParent());
		Files.writeString(world, "keep");
		assertThrows(IOException.class, () -> store.deletePermanently(a.id()));
		store.archive(a.id());
		try (var ignored = store.lock(a.id())) {
			assertThrows(IOException.class, () -> store.deletePermanently(a.id()));
		}
		Files.writeString(SessionCheckpoint.marker(store, a.id()), "interrupted");
		assertThrows(IOException.class, () -> store.deletePermanently(a.id()));
		Files.delete(SessionCheckpoint.marker(store, a.id()));
		assertTrue(Files.isDirectory(store.directory(a.id()).resolve("backups")));
		store.deletePermanently(a.id());
		assertFalse(Files.exists(store.directory(a.id())));
		assertTrue(store.archived().isEmpty());
		assertEquals(b, store.find(b.id()).orElseThrow());
		assertEquals("keep", Files.readString(world));
	}

	@Test
	void corruptAndFutureEntriesAreIsolatedAndBackupsRecover() throws Exception {
		var store = store();
		var bad = profile(CharacterDifficulty.SOFTCORE);
		var good = profile(CharacterDifficulty.SOFTCORE);
		store.create(bad);
		store.create(good);
		try (var lock = store.lock(bad.id())) {
			store.commit(bad.id(), store.read(bad.id()), 0);
		}
		Files.write(store.file(bad.id()), new byte[] {1, 2, 3});
		assertEquals(List.of(good), store.list());
		assertTrue(store.problems().containsKey(bad.id()));
		store.recover(bad.id());
		assertEquals(bad, store.find(bad.id()).orElseThrow());
		var future = store.read(bad.id());
		future.putInt("FormatVersion", 99);
		AtomicNbt.write(store.file(bad.id()), future, false);
		assertThrows(IOException.class, () -> store.read(bad.id()));
		assertEquals(List.of(good), store.list());
	}

	@Test
	void deathTombstoneOverridesBundleAndCannotBeRolledBack() throws Exception {
		var store = store();
		var p = profile(CharacterDifficulty.HARDCORE);
		store.create(p);
		AtomicNbt.write(store.directory(p.id()).resolve("dead.nbt"), new CompoundTag(), false);
		assertEquals(CharacterProfile.Status.DEAD, store.find(p.id()).orElseThrow().status());
		assertThrows(IOException.class, () -> store.edit(p));
		assertThrows(IOException.class, () -> store.recover(p.id()));
	}

	@Test
	void oneAtomicBundleKeepsPortableAndWorldLocalStatesTogether() throws Exception {
		var store = store();
		var p = profile(CharacterDifficulty.SOFTCORE);
		store.create(p);
		String x = UUID.randomUUID().toString(), y = UUID.randomUUID().toString();
		try (var lock = store.lock(p.id())) {
			var tag = store.read(p.id());
			tag.getCompound("Adapters").put("storage_test:inventory", snapshot("count", 17));
			var local = new CompoundTag();
			local.put("storage_test:position", snapshot("position", 400));
			tag.getCompound("Worlds").put(x, local);
			store.commit(p.id(), tag, 0);
		}
		var reloaded = store.read(p.id());
		assertEquals(
				17,
				reloaded.getCompound("Adapters")
						.getCompound("storage_test:inventory")
						.getCompound("Data")
						.getInt("count"));
		assertEquals(
				400,
				reloaded.getCompound("Worlds")
						.getCompound(x)
						.getCompound("storage_test:position")
						.getCompound("Data")
						.getInt("position"));
		assertTrue(reloaded.getCompound("Worlds").getCompound(y).isEmpty());
	}

	@Test
	void worldIdentityAndExtensionsRoundTripWithoutRenamingIdentity() throws Exception {
		var world = root.resolve("saves/X");
		var id = UUID.randomUUID();
		var settings =
				new WorldSettings(
						"for the worthy",
						"minecraft:normal",
						WorldDifficulty.HARD,
						Map.of("other_mod:setting", "kept"));
		WorldStore.write(world, new WorldProfile(id, "First", settings));
		WorldStore.write(world, new WorldProfile(id, "Renamed", settings));
		assertEquals(
				new WorldProfile(id, "Renamed", settings), WorldStore.read(world).orElseThrow());
	}

	@Test
	void interruptedSessionRestoresWorldAndCharacterAsOneBaseline() throws Exception {
		var store = store();
		var p = profile(CharacterDifficulty.SOFTCORE);
		store.create(p);
		Path saves = root.resolve("saves"), world = saves.resolve("X");
		Files.createDirectories(world);
		Files.writeString(world.resolve("blocks.txt"), "before");
		SessionCheckpoint.begin(store, p.id(), world);
		Files.writeString(world.resolve("blocks.txt"), "after");
		try (var lock = store.lock(p.id())) {
			var tag = store.read(p.id());
			tag.getCompound("Adapters").put("storage_test:inventory", snapshot("items", 42));
			store.commit(p.id(), tag, 0);
		}
		assertThrows(IOException.class, () -> SessionCheckpoint.begin(store, p.id(), world));
		SessionCheckpoint.recover(store, p.id(), saves);
		assertEquals("before", Files.readString(world.resolve("blocks.txt")));
		assertEquals(0, store.read(p.id()).getLong("Revision"));
		assertFalse(Files.exists(SessionCheckpoint.marker(store, p.id())));
		try (var dirs = Files.list(saves)) {
			assertTrue(dirs.anyMatch(d -> d.getFileName().toString().contains(".terra-recovery-")));
		}
	}

	@Test
	void checkpointRecoveryCannotResurrectHardcoreOrEscapeSaves() throws Exception {
		var store = store();
		var p = profile(CharacterDifficulty.HARDCORE);
		store.create(p);
		Path saves = root.resolve("saves"), world = saves.resolve("X");
		Files.createDirectories(world);
		SessionCheckpoint.begin(store, p.id(), world);
		AtomicNbt.write(store.directory(p.id()).resolve("dead.nbt"), new CompoundTag(), false);
		assertThrows(
				IOException.class,
				() -> SessionCheckpoint.recover(store, p.id(), root.resolve("other")));
		SessionCheckpoint.recover(store, p.id(), saves);
		assertEquals(CharacterProfile.Status.DEAD, store.find(p.id()).orElseThrow().status());
	}

	private static CompoundTag snapshot(String key, int value) {
		var data = new CompoundTag();
		data.putInt(key, value);
		var wrapper = new CompoundTag();
		wrapper.putInt("Version", 1);
		wrapper.put("Data", data);
		return wrapper;
	}

	@Test
	void missingStateSectionIsNotInterpretedAsAnEmptyCharacter() throws Exception {
		var store = store();
		var p = profile(CharacterDifficulty.SOFTCORE);
		store.create(p);
		var tag = store.read(p.id());
		tag.remove("Adapters");
		AtomicNbt.write(store.file(p.id()), tag, false);
		assertThrows(IOException.class, () -> store.read(p.id()));
		assertTrue(store.list().isEmpty());
	}

	@Test
	void invalidReplacementLeavesCommittedNbtReadable() throws Exception {
		Path file = root.resolve("state.nbt");
		var tag = new CompoundTag();
		tag.putString("Value", "previous");
		AtomicNbt.write(file, tag, false);
		Path invalid = root.resolve("directory.nbt");
		Files.createDirectory(invalid);
		assertThrows(IOException.class, () -> AtomicNbt.write(invalid, new CompoundTag(), true));
		assertEquals("previous", AtomicNbt.read(file).getString("Value"));
	}
}
