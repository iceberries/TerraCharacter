package org.confluence.terra_character.service;

import net.minecraft.SharedConstants;
import net.minecraft.util.DirectoryLock;

import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.storage.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class WorldIndex {
	private WorldIndex() {}

	public record Entry(
			Path directory,
			WorldProfile profile,
			boolean managed,
			boolean hardcore,
			boolean duplicate,
			String error) {
		public Entry {
			directory = Objects.requireNonNull(directory, "directory");
			error = Objects.requireNonNullElse(error, "Cannot read world metadata");
		}

		public String folder() {
			return directory.getFileName().toString();
		}
	}

	public static List<Entry> scan(Path saves) throws IOException {
		if (!Files.isDirectory(saves)) return List.of();
		var result = new ArrayList<Entry>();
		try (var dirs = Files.list(saves)) {
			for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
				if (!Files.isRegularFile(dir.resolve("level.dat"))
						|| dir.getFileName().toString().contains(".terra-recovery-")) continue;
				try {
					if (Files.isSymbolicLink(dir))
						throw new IOException("Symbolic link world rejected");
					var data = AtomicNbt.read(dir.resolve("level.dat")).getCompound("Data");
					if (data.getInt("DataVersion")
							> SharedConstants.getCurrentVersion().getDataVersion().getVersion())
						throw new IOException("World was saved by a newer Minecraft version");
					var stored = WorldStore.read(dir);
					String displayName =
							data.getString("LevelName").isBlank()
									? dir.getFileName().toString()
									: data.getString("LevelName");
					WorldProfile profile =
							stored.map(p -> new WorldProfile(p.id(), displayName, p.settings()))
									.orElseGet(
											() ->
													new WorldProfile(
															UUID.nameUUIDFromBytes(
																	dir.toAbsolutePath()
																			.normalize()
																			.toString()
																			.getBytes(
																					StandardCharsets
																							.UTF_8)),
															displayName,
															new WorldSettings(
																	Long.toString(
																			data.getCompound(
																							"WorldGenSettings")
																					.getLong(
																							"seed")),
																	"minecraft:normal",
																	switch (data.getByte(
																			"Difficulty")) {
																		case 0 ->
																				WorldDifficulty
																						.PEACEFUL;
																		case 1 ->
																				WorldDifficulty
																						.EASY;
																		case 3 ->
																				WorldDifficulty
																						.HARD;
																		default ->
																				WorldDifficulty
																						.NORMAL;
																	})));
					result.add(
							new Entry(
									dir,
									profile,
									stored.isPresent(),
									data.getBoolean("hardcore"),
									false,
									""));
				} catch (IOException | RuntimeException ex) {
					result.add(new Entry(dir, null, false, false, false, ex.getMessage()));
				}
			}
		}
		var counts = new HashMap<UUID, Integer>();
		result.stream()
				.filter(e -> e.profile != null)
				.forEach(e -> counts.merge(e.profile.id(), 1, Integer::sum));
		return result.stream()
				.map(
						e ->
								new Entry(
										e.directory,
										e.profile,
										e.managed,
										e.hardcore,
										e.profile != null && counts.get(e.profile.id()) > 1,
										e.error))
				.toList();
	}

	public static void separateCopy(Entry entry) throws IOException {
		try (var lock = DirectoryLock.create(entry.directory())) {
			var old = entry.profile();
			WorldStore.write(
					entry.directory(),
					new WorldProfile(UUID.randomUUID(), old.name(), old.settings()));
			// Per-character progress is under the old world identity; move it aside instead of
			// reusing it.
			Path progress = WorldStore.directory(entry.directory()).resolve("progress");
			if (Files.exists(progress))
				Files.move(
						progress,
						progress.resolveSibling("progress-before-copy-" + UUID.randomUUID()));
		}
	}
}
