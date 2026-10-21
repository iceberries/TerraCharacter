package org.confluence.terra_character.storage;

import static java.nio.file.StandardOpenOption.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import org.confluence.terra_character.api.model.CharacterProfile;
import org.confluence.terra_character.api.repository.ProfileRepository;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Metadata, assets, journey state and all world-local snapshots share a single atomic commit. */
public final class CharacterStore implements ProfileRepository<CharacterProfile> {
	private final Path root;
	private final Map<UUID, String> problems = new LinkedHashMap<>();

	public CharacterStore(Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	public Path root() {
		return root;
	}

	public Path directory(UUID id) {
		return root.resolve(id.toString());
	}

	public Path file(UUID id) {
		return directory(id).resolve("character.nbt");
	}

	public synchronized Map<UUID, String> problems() {
		return Map.copyOf(problems);
	}

	@Override
	public synchronized List<CharacterProfile> list() throws IOException {
		problems.clear();
		if (!Files.isDirectory(root)) return List.of();
		var profiles = new ArrayList<CharacterProfile>();
		try (var dirs = Files.list(root)) {
			for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
				UUID id;
				try {
					id = UUID.fromString(dir.getFileName().toString());
				} catch (IllegalArgumentException ignored) {
					continue;
				}
				try {
					var document = read(id);
					if (!document.getBoolean("Archived"))
						profiles.add(ProfileCodec.character(document.getCompound("Profile")));
				} catch (IOException ex) {
					problems.put(id, Objects.requireNonNullElse(ex.getMessage(), ex.toString()));
				}
			}
		}
		profiles.sort(
				Comparator.comparing(CharacterProfile::name).thenComparing(CharacterProfile::id));
		return List.copyOf(profiles);
	}

	@Override
	public Optional<CharacterProfile> find(UUID id) throws IOException {
		if (!Files.exists(file(id))) return Optional.empty();
		var document = read(id);
		return document.getBoolean("Archived")
				? Optional.empty()
				: Optional.of(ProfileCodec.character(document.getCompound("Profile")));
	}

	@Override
	public void create(CharacterProfile profile) throws IOException {
		try (var lock = lock(profile.id())) {
			if (Files.exists(file(profile.id())))
				throw new IOException("Character identity already exists");
			var document = new CompoundTag();
			document.putInt("FormatVersion", ProfileCodec.VERSION);
			document.putLong("Revision", 0);
			document.put("Profile", ProfileCodec.character(profile));
			document.put("Adapters", new CompoundTag());
			document.put("Worlds", new CompoundTag());
			document.put("Journey", new CompoundTag());
			AtomicNbt.write(file(profile.id()), document, false);
		}
	}

	public CompoundTag read(UUID id) throws IOException {
		if (Files.isSymbolicLink(directory(id)))
			throw new IOException("Symbolic link character directory rejected");
		var document = AtomicNbt.read(file(id));
		ProfileCodec.version(document);
		validateBundle(document);
		if (!ProfileCodec.character(document.getCompound("Profile")).id().equals(id))
			throw new IOException("Character identity mismatch");
		if (Files.exists(directory(id).resolve("dead.nbt")))
			document.getCompound("Profile").putString("Status", "DEAD");
		return document;
	}

	/** Caller must hold the identity lock. An optimistic revision check protects stale editors. */
	public void commit(UUID id, CompoundTag document, long expectedRevision) throws IOException {
		commit(id, document, expectedRevision, false);
	}

	/** Remote stores are mirrors; a client snapshot can supersede even a stale server death. */
	void commitMirror(UUID id, CompoundTag document, long expectedRevision) throws IOException {
		commit(id, document, expectedRevision, true);
		Path tombstone = directory(id).resolve("dead.nbt");
		if (Files.exists(tombstone)) {
			AtomicNbt.write(
					directory(id).resolve("previous-server-death.nbt"),
					AtomicNbt.read(tombstone),
					true);
			Files.delete(tombstone);
		}
	}

	private void commit(UUID id, CompoundTag document, long expectedRevision, boolean mirror)
			throws IOException {
		var current = read(id);
		if (current.getLong("Revision") != expectedRevision)
			throw new IOException("Character changed since it was read");
		ProfileCodec.version(document);
		validateBundle(document);
		if (!ProfileCodec.character(document.getCompound("Profile")).id().equals(id))
			throw new IOException("Character identity mismatch");
		if (!mirror
				&& current.getCompound("Profile").getString("Status").equals("DEAD")
				&& !document.getCompound("Profile").getString("Status").equals("DEAD")) {
			throw new IOException("A permanently dead character cannot be revived");
		}
		document.putLong("Revision", expectedRevision + 1);
		document.putLong("SavedAt", System.currentTimeMillis());
		AtomicNbt.write(file(id), document, true);
	}

	public void edit(CharacterProfile profile) throws IOException {
		try (var lock = lock(profile.id())) {
			var document = read(profile.id());
			var old = ProfileCodec.character(document.getCompound("Profile"));
			if (old.difficulty() != profile.difficulty() || old.status() != profile.status())
				throw new IOException("Difficulty and death status are immutable");
			document.put("Profile", ProfileCodec.character(profile));
			commit(profile.id(), document, document.getLong("Revision"));
		}
	}

	/**
	 * Reversible deletion: retain assets and backups instead of recursively deleting a directory.
	 */
	public void archive(UUID id) throws IOException {
		try (var lock = lock(id)) {
			var document = read(id);
			document.putBoolean("Archived", true);
			commit(id, document, document.getLong("Revision"));
		}
	}

	public List<CharacterProfile> archived() throws IOException {
		if (!Files.isDirectory(root)) return List.of();
		var result = new ArrayList<CharacterProfile>();
		try (var dirs = Files.list(root)) {
			for (Path dir : dirs.filter(Files::isDirectory).toList()) {
				try {
					var tag = read(UUID.fromString(dir.getFileName().toString()));
					if (tag.getBoolean("Archived"))
						result.add(ProfileCodec.character(tag.getCompound("Profile")));
				} catch (IllegalArgumentException | IOException ignored) {
				}
			}
		}
		return List.copyOf(result);
	}

	public void unarchive(UUID id) throws IOException {
		try (var lock = lock(id)) {
			var tag = read(id);
			tag.remove("Archived");
			commit(id, tag, tag.getLong("Revision"));
		}
	}

	/** Only archived, idle identities may be erased. World saves are never traversed. */
	public synchronized void deletePermanently(UUID id) throws IOException {
		Path target = directory(id).toAbsolutePath().normalize();
		if (!target.getParent().equals(root)
				|| Files.isSymbolicLink(root)
				|| Files.isSymbolicLink(target)
				|| !target.toRealPath().getParent().equals(root.toRealPath()))
			throw new IOException("Character deletion target is outside the character store");
		Path lockFile = target.resolve("character.lock");
		try (var ignored = lock(id)) {
			if (!read(id).getBoolean("Archived"))
				throw new IOException("Only archived characters can be permanently deleted");
			if (Files.exists(SessionCheckpoint.marker(this, id)))
				throw new IOException(
						"Restore the character and recover its interrupted session before deleting"
								+ " it");
			// Do not follow links. Keep metadata until all assets/backups have been removed,
			// and keep the open lock file until Windows releases its handle.
			try (var paths = Files.walk(target)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
					if (!path.startsWith(target))
						throw new IOException("Unsafe character deletion path");
					if (path.equals(target) || path.equals(lockFile) || path.equals(file(id)))
						continue;
					Files.delete(path);
				}
			}
			Files.delete(file(id));
		}
		Files.deleteIfExists(lockFile);
		Files.delete(target);
		problems.remove(id);
	}

	public void recover(UUID id) throws IOException {
		try (var lock = lock(id)) {
			if (Files.exists(directory(id).resolve("dead.nbt")))
				throw new IOException("Cannot roll back a hardcore death");
			Path backups = directory(id).resolve("backups");
			if (!Files.isDirectory(backups)) throw new IOException("No backup available");
			try (var files = Files.list(backups)) {
				for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
					try {
						var document = AtomicNbt.read(path);
						ProfileCodec.version(document);
						validateBundle(document);
						if (!ProfileCodec.character(document.getCompound("Profile"))
								.id()
								.equals(id)) continue;
						AtomicNbt.write(file(id), document, true);
						return;
					} catch (IOException ignored) {
						/* Try the preceding valid backup. */
					}
				}
			}
			throw new IOException("No valid backup available");
		}
	}

	public IdentityLock lock(UUID id) throws IOException {
		Files.createDirectories(directory(id));
		if (Files.isSymbolicLink(root) || Files.isSymbolicLink(directory(id)))
			throw new IOException("Symbolic link character directory rejected");
		var channel = FileChannel.open(directory(id).resolve("character.lock"), CREATE, WRITE);
		try {
			var lock = channel.tryLock();
			if (lock == null) throw new IOException("Character is already in use");
			return new IdentityLock(channel, lock);
		} catch (IOException | OverlappingFileLockException ex) {
			channel.close();
			throw new IOException("Character is already in use", ex);
		}
	}

	public static void validateBundle(CompoundTag tag) throws IOException {
		CharacterStatisticsCodec.validate(tag);
		if (!tag.contains("Revision", Tag.TAG_LONG) || tag.getLong("Revision") < 0)
			throw new IOException("Invalid character revision");
		for (String key : List.of("Profile", "Adapters", "Worlds", "Journey"))
			if (!tag.contains(key, Tag.TAG_COMPOUND))
				throw new IOException("Missing character section: " + key);
		validateAdapters(tag.getCompound("Adapters"));
		for (String world : tag.getCompound("Worlds").getAllKeys()) {
			try {
				UUID.fromString(world);
			} catch (IllegalArgumentException ex) {
				throw new IOException("Invalid world identity", ex);
			}
			if (!tag.getCompound("Worlds").contains(world, Tag.TAG_COMPOUND))
				throw new IOException("Invalid world snapshot");
			validateAdapters(tag.getCompound("Worlds").getCompound(world));
		}
	}

	private static void validateAdapters(CompoundTag group) throws IOException {
		for (String key : group.getAllKeys()) {
			if (!group.contains(key, Tag.TAG_COMPOUND))
				throw new IOException("Invalid adapter snapshot: " + key);
			var value = group.getCompound(key);
			if (!value.contains("Version", Tag.TAG_INT)
					|| value.getInt("Version") < 1
					|| !value.contains("Data", Tag.TAG_COMPOUND))
				throw new IOException("Incomplete adapter snapshot: " + key);
		}
	}

	public static final class IdentityLock implements AutoCloseable {
		private final FileChannel channel;
		private final FileLock lock;

		private IdentityLock(FileChannel channel, FileLock lock) {
			this.channel = channel;
			this.lock = lock;
		}

		public synchronized boolean isValid() {
			return lock.isValid();
		}

		@Override
		public synchronized void close() throws IOException {
			try {
				if (lock.isValid()) lock.release();
			} finally {
				channel.close();
			}
		}
	}
}
