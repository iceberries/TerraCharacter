package org.confluence.terra_character.storage;

import net.minecraft.nbt.CompoundTag;

import org.confluence.terra_character.api.model.CharacterProfile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Account identity comes exclusively from the authenticated server connection. */
public final class RemoteCharacters {
	private RemoteCharacters() {}

	public static CharacterStore store(Path serverDirectory, UUID account) {
		return new CharacterStore(
				serverDirectory
						.resolve("terra_character/server_characters")
						.resolve(account.toString())
						.resolve("characters"));
	}

	public static CharacterProfile metadata(CompoundTag tag) throws IOException {
		if (!Set.of("Id", "Name", "Difficulty", "Status", "Appearance")
				.containsAll(tag.getAllKeys()))
			throw new IOException("Only character metadata may be submitted");
		var a = tag.getCompound("Appearance");
		if (!Set.of("Body", "Hair", "Colors", "Paint", "Parts", "DyeMasks", "BaseSkin")
						.containsAll(a.getAllKeys())
				|| a.getString("Body").length() > 255
				|| a.getString("Hair").length() > 255
				|| a.getCompound("Colors").size() > 32
				|| a.getCompound("Colors").getAllKeys().stream().anyMatch(k -> k.length() > 255))
			throw new IOException("Invalid appearance size");
		var profile = ProfileCodec.character(tag);
		if (profile.status() != CharacterProfile.Status.ACTIVE)
			throw new IOException("A dead character cannot join");
		return profile;
	}

	public static CharacterStore.IdentityLock prepare(
			CharacterStore store, CharacterProfile submitted) throws IOException {
		var portable = new CompoundTag();
		portable.put("Profile", ProfileCodec.character(submitted));
		portable.put("Adapters", new CompoundTag());
		portable.put("Journey", new CompoundTag());
		return prepare(store, portable);
	}

	public static CharacterStore.IdentityLock prepare(CharacterStore store, CompoundTag portable)
			throws IOException {
		PortableCharacter.validate(portable);
		portable = PortableCharacter.extract(portable);
		var submitted = metadata(portable.getCompound("Profile"));
		if (!Files.exists(store.file(submitted.id()))) {
			if (Files.isDirectory(store.root()))
				try (var entries = Files.list(store.root())) {
					if (entries.filter(Files::isDirectory).count() >= 64)
						throw new IOException(
								"Server character limit reached for this account (64)");
				}
			store.create(submitted);
		}
		var lock = store.lock(submitted.id());
		try {
			var document = store.read(submitted.id());
			if (document.getBoolean("Archived"))
				throw new IOException("This server character is archived");
			// The client's portable snapshot wins even when its revision is numerically lower.
			// Server-owned world snapshots, statistics and advancements stay in their world.
			if (!PortableCharacter.extract(document).equals(portable))
				PortableCharacter.mergeMirror(store, submitted.id(), portable);
			return lock;
		} catch (Exception ex) {
			lock.close();
			if (ex instanceof IOException io) throw io;
			throw new IOException(ex.getMessage(), ex);
		}
	}
}
