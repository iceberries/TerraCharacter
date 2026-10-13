package org.confluence.terra_character.service;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.util.DirectoryLock;

import org.confluence.terra_character.api.integration.CharacterDataAdapter;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.integration.*;
import org.confluence.terra_character.storage.*;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Explicit, backed-up and idempotent. Never deletes or replaces the source account's player data.
 */
public final class LegacyImporter {
	private LegacyImporter() {}

	public record Source(Path file, String label, CompoundTag player, int dataVersion) {}

	public static List<Source> sources(Path world) throws IOException {
		var result = new ArrayList<Source>();
		var data = AtomicNbt.read(world.resolve("level.dat")).getCompound("Data");
		if (data.contains("Player", Tag.TAG_COMPOUND))
			result.add(
					new Source(
							world.resolve("level.dat"),
							"Singleplayer owner",
							data.getCompound("Player"),
							data.getInt("DataVersion")));
		Path players = world.resolve("playerdata");
		if (Files.isDirectory(players))
			try (var files = Files.list(players)) {
				for (Path file :
						files.filter(p -> p.getFileName().toString().endsWith(".dat"))
								.sorted()
								.toList()) {
					var player = AtomicNbt.read(file);
					result.add(
							new Source(
									file,
									file.getFileName().toString(),
									player,
									player.getInt("DataVersion")));
				}
			}
		return List.copyOf(result);
	}

	public static CharacterProfile importCharacter(
			CharacterStore store,
			WorldIndex.Entry world,
			Source source,
			String name,
			CharacterDifficulty difficulty,
			CharacterAppearance appearance)
			throws IOException {
		if (source.dataVersion()
				!= SharedConstants.getCurrentVersion().getDataVersion().getVersion())
			throw new IOException(
					"Open and save this source in the current Minecraft version before importing");
		var known = new HashSet<String>();
		AdapterRegistry.all().forEach(adapter -> known.addAll(adapter.legacyAttachmentIds()));
		for (String key : source.player().getCompound("neoforge:attachments").getAllKeys())
			if (!known.contains(key)) throw new IOException("No importer for attachment: " + key);
		if (!source.player().getCompound("ForgeCaps").isEmpty())
			throw new IOException("Legacy Forge capabilities require migration before import");
		DataIntegrity.validate(source.player(), null);
		UUID account =
				source.player().hasUUID("UUID")
						? source.player().getUUID("UUID")
						: UUID.nameUUIDFromBytes(source.label().getBytes(StandardCharsets.UTF_8));
		String importId = world.profile().id() + ":" + account;
		Path ledger = store.root().resolve("imports.nbt");
		Files.createDirectories(store.root());
		if (Files.exists(WorldStore.directory(world.directory()).resolve("session.nbt")))
			throw new IOException("Recover the interrupted session before importing this world");
		try (var worldLock = DirectoryLock.create(world.directory());
				var ledgerLock =
						FileChannel.open(
								store.root().resolve("imports.lock"),
								StandardOpenOption.CREATE,
								StandardOpenOption.WRITE);
				var ignored = ledgerLock.lock()) {
			var imports = Files.exists(ledger) ? AtomicNbt.read(ledger) : new CompoundTag();
			if (imports.contains(importId)) {
				UUID id = imports.getUUID(importId);
				if (!Files.exists(store.file(id))) {
					var staged = AtomicNbt.read(store.directory(id).resolve("import-pending.nbt"));
					ProfileCodec.version(staged);
					CharacterStore.validateBundle(staged);
					if (!ProfileCodec.character(staged.getCompound("Profile")).id().equals(id))
						throw new IOException("Import identity mismatch");
					try (var lock = store.lock(id)) {
						AtomicNbt.write(store.file(id), staged, false);
					}
				}
				return store.find(id)
						.orElseThrow(
								() ->
										new IOException(
												"Source already imported; restore its archived"
														+ " character instead"));
			}
			var status =
					world.hardcore() && source.player().getFloat("Health") <= 0
							? CharacterProfile.Status.DEAD
							: CharacterProfile.Status.ACTIVE;
			if (world.hardcore() && difficulty != CharacterDifficulty.HARDCORE)
				throw new IOException("A hardcore source must remain hardcore");
			var profile =
					new CharacterProfile(UUID.randomUUID(), name, difficulty, appearance, status);
			Files.createDirectories(store.directory(profile.id()));
			Path backup = store.directory(profile.id()).resolve("import-source");
			Files.createDirectories(backup);
			Files.copy(
					source.file(),
					backup.resolve(source.file().getFileName()),
					StandardCopyOption.COPY_ATTRIBUTES);
			var doc = new CompoundTag();
			doc.putInt("FormatVersion", ProfileCodec.VERSION);
			doc.putLong("Revision", 0);
			doc.put("Profile", ProfileCodec.character(profile));
			doc.put("Journey", new CompoundTag());
			var portable = new CompoundTag();
			var local = new CompoundTag();
			for (var adapter : AdapterRegistry.all())
				adapter.importLegacy(source.player())
						.ifPresent(
								tag ->
										(adapter.scope() == CharacterDataAdapter.Scope.CHARACTER
														? portable
														: local)
												.put(
														adapter.id().toString(),
														AdapterRegistry.envelope(adapter, tag)));
			if (source.player().getFloat("Health") <= 0)
				local.getCompound(VanillaDataAdapter.LOCAL)
						.getCompound("Data")
						.putBoolean("NeedsRespawn", true);
			var worlds = new CompoundTag();
			worlds.put(world.profile().id().toString(), local);
			doc.put("Adapters", portable);
			doc.put("Worlds", worlds);
			doc.putString("ImportSource", importId);
			Path progress =
					WorldStore.directory(world.directory()).resolve("progress/" + profile.id());
			Files.createDirectories(progress);
			for (String type : List.of("stats", "advancements")) {
				Path old = world.directory().resolve(type + "/" + account + ".json");
				if (Files.exists(old)) {
					Files.copy(old, backup.resolve(type + ".json"));
					Files.copy(old, progress.resolve(type + ".json"));
				}
			}
			WorldStore.write(world.directory(), world.profile());
			// A durable staging record lets retries finish a reserved import without exposing an
			// empty character.
			AtomicNbt.write(
					store.directory(profile.id()).resolve("import-pending.nbt"), doc, false);
			if (status == CharacterProfile.Status.DEAD) {
				var death = new CompoundTag();
				death.putUUID("Id", profile.id());
				AtomicNbt.write(store.directory(profile.id()).resolve("dead.nbt"), death, false);
			}
			imports.putUUID(importId, profile.id());
			AtomicNbt.write(ledger, imports, true);
			try (var lock = store.lock(profile.id())) {
				AtomicNbt.write(store.file(profile.id()), doc, false);
			}
			return profile;
		}
	}
}
