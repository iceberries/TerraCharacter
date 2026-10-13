package org.confluence.terra_character.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.DirectoryLock;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Offline baseline: an interrupted session must restore BOTH the world and character before playing
 * again.
 */
public final class SessionCheckpoint {
	private SessionCheckpoint() {}

	public static Path marker(CharacterStore store, UUID id) {
		return store.directory(id).resolve("session.nbt");
	}

	public static void begin(CharacterStore store, UUID id, Path world) throws IOException {
		if (Files.exists(marker(store, id)))
			throw new IOException("Interrupted session: use Recover session before playing");
		Path checkpoint = store.directory(id).resolve("checkpoint-" + UUID.randomUUID());
		Files.createDirectories(checkpoint);
		Files.copy(store.file(id), checkpoint.resolve("character.nbt"));
		boolean exists = Files.isDirectory(world);
		if (exists) {
			try (var ignored = DirectoryLock.create(world);
					var zip =
							new ZipOutputStream(
									Files.newOutputStream(checkpoint.resolve("world.zip")))) {
				try (var paths = Files.walk(world)) {
					for (Path source : paths.sorted().toList()) {
						if (Files.isSymbolicLink(source))
							throw new IOException("Symbolic links in world saves are unsupported");
						if (!Files.isRegularFile(source)
								|| source.getFileName().toString().equals("session.lock")) continue;
						zip.putNextEntry(
								new ZipEntry(
										world.relativize(source).toString().replace('\\', '/')));
						Files.copy(source, zip);
						zip.closeEntry();
					}
				}
			}
		}
		var tag = new CompoundTag();
		tag.putString("Checkpoint", checkpoint.getFileName().toString());
		tag.putString("World", world.toAbsolutePath().normalize().toString());
		tag.putBoolean("ExistingWorld", exists);
		AtomicNbt.write(marker(store, id), tag, false);
	}

	public static void finish(CharacterStore store, UUID id) throws IOException {
		if (!Files.exists(marker(store, id))) return;
		var tag = AtomicNbt.read(marker(store, id));
		Path checkpoint = checkpoint(store, id, tag);
		// Delete only named files created by this class. Never recursively delete user saves.
		Files.deleteIfExists(marker(store, id));
		Files.deleteIfExists(checkpoint.resolve("world.zip"));
		Files.deleteIfExists(checkpoint.resolve("character.nbt"));
		Files.deleteIfExists(checkpoint);
	}

	public static void recover(CharacterStore store, UUID id, Path savesRoot) throws IOException {
		try (var ignored = store.lock(id)) {
			var tag = AtomicNbt.read(marker(store, id));
			Path world = Path.of(tag.getString("World")).toAbsolutePath().normalize();
			Path saves = savesRoot.toAbsolutePath().normalize();
			if (!world.startsWith(saves)
					|| world.equals(saves)
					|| !world.getParent().equals(saves)
					|| Files.isSymbolicLink(world)
					|| Files.isSymbolicLink(saves))
				throw new IOException("Recovery world is outside the saves directory");
			Path checkpoint = checkpoint(store, id, tag);
			// Sharing is permanent: an offline baseline must never rewind other players' changes.
			if (WorldStore.shared(world)) {
				try (var worldLock = DirectoryLock.create(world)) {
					Path owner = WorldStore.directory(world).resolve("session.nbt");
					if (Files.exists(owner)
							&& AtomicNbt.read(owner).getUUID("Character").equals(id))
						Files.delete(owner);
					finish(store, id);
				}
				return;
			}
			var baseline = AtomicNbt.read(checkpoint.resolve("character.nbt"));
			ProfileCodec.version(baseline);
			CharacterStore.validateBundle(baseline);
			if (!ProfileCodec.character(baseline.getCompound("Profile")).id().equals(id))
				throw new IOException("Checkpoint character mismatch");
			Path restored = saves.resolve("terra-restore-" + UUID.randomUUID());
			if (tag.getBoolean("ExistingWorld")) {
				Files.createDirectory(restored);
				try (var zip =
						new ZipInputStream(Files.newInputStream(checkpoint.resolve("world.zip")))) {
					ZipEntry entry;
					while ((entry = zip.getNextEntry()) != null) {
						Path target = restored.resolve(entry.getName()).normalize();
						if (!target.startsWith(restored))
							throw new IOException("Unsafe checkpoint path");
						if (entry.isDirectory()) Files.createDirectories(target);
						else {
							Files.createDirectories(target.getParent());
							Files.copy(zip, target);
						}
					}
				}
			}
			if (Files.exists(world)) {
				try (var lock = DirectoryLock.create(world)) {
					/* Verify no running server owns the world. */
				}
				Path preserved =
						saves.resolve(world.getFileName() + ".terra-recovery-" + UUID.randomUUID());
				Files.move(world, preserved, StandardCopyOption.ATOMIC_MOVE);
			}
			if (tag.getBoolean("ExistingWorld"))
				Files.move(restored, world, StandardCopyOption.ATOMIC_MOVE);
			if (Files.exists(store.directory(id).resolve("dead.nbt")))
				baseline.getCompound("Profile").putString("Status", "DEAD");
			AtomicNbt.write(store.file(id), baseline, true);
			Files.delete(marker(store, id));
			// Retain recovery material and the interrupted world for manual inspection.
		}
	}

	private static Path checkpoint(CharacterStore store, UUID id, CompoundTag tag)
			throws IOException {
		Path parent = store.directory(id);
		Path path = parent.resolve(tag.getString("Checkpoint")).normalize();
		if (!path.getParent().equals(parent)
				|| !path.getFileName().toString().startsWith("checkpoint-")
				|| Files.isSymbolicLink(path)) throw new IOException("Invalid checkpoint path");
		return path;
	}
}
