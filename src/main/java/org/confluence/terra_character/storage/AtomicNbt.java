package org.confluence.terra_character.storage;

import static java.nio.file.StandardOpenOption.WRITE;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Comparator;
import java.util.UUID;

/** A forced temporary file and an atomic rename form one commit. Never overwrite in place. */
public final class AtomicNbt {
	private AtomicNbt() {}

	public static CompoundTag read(Path path) throws IOException {
		if (Files.isSymbolicLink(path)) throw new IOException("Symbolic link save rejected");
		try {
			return NbtIo.readCompressed(path, NbtAccounter.create(64L * 1024 * 1024));
		} catch (RuntimeException ex) {
			throw new IOException("Unreadable NBT: " + path.getFileName(), ex);
		}
	}

	public static void write(Path target, CompoundTag tag, boolean backup) throws IOException {
		target = target.toAbsolutePath().normalize();
		Files.createDirectories(target.getParent());
		if (Files.isSymbolicLink(target) || Files.isSymbolicLink(target.getParent()))
			throw new IOException("Symbolic link save rejected");
		Path temporary =
				target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
		try {
			NbtIo.writeCompressed(tag, temporary);
			try (var channel = FileChannel.open(temporary, WRITE)) {
				channel.force(true);
			}
			// Detect a truncated or invalid temporary file before committing it.
			read(temporary);
			if (backup && Files.exists(target)) {
				Path backups = target.getParent().resolve("backups");
				Files.createDirectories(backups);
				Path previous =
						backups.resolve(
								target.getFileName()
										+ "."
										+ System.currentTimeMillis()
										+ "."
										+ UUID.randomUUID()
										+ ".nbt");
				Files.copy(target, previous);
				try (var channel = FileChannel.open(previous, WRITE)) {
					channel.force(true);
				}
			}
			Files.move(
					temporary,
					target,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
			// Backup cleanup is best effort and cannot invalidate an already committed save.
			if (backup) prune(target);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static void prune(Path target) {
		Path backups = target.getParent().resolve("backups");
		if (!Files.isDirectory(backups)) return;
		try (var files = Files.list(backups)) {
			var older =
					files.filter(
									p ->
											p.getFileName()
													.toString()
													.startsWith(target.getFileName() + "."))
							.sorted(
									Comparator.comparing((Path p) -> p.getFileName().toString())
											.reversed())
							.skip(8)
							.toList();
			for (Path path : older) Files.deleteIfExists(path);
		} catch (IOException ignored) {
			/* Retaining extra backups is safe. */
		}
	}
}
