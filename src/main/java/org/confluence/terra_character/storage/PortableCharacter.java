package org.confluence.terra_character.storage;

import net.minecraft.nbt.*;

import org.confluence.terra_character.api.integration.CharacterDataAdapter;
import org.confluence.terra_character.integration.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;

/** Only character-owned sections cross a connection; revisions and worlds remain local. */
public final class PortableCharacter {
	private PortableCharacter() {}

	public static CompoundTag readForTravel(CharacterStore store, UUID id) throws IOException {
		if (Files.exists(SessionCheckpoint.marker(store, id)))
			throw new IOException(
					"Interrupted session: use Recover session before joining a server");
		var document = store.read(id);
		if (document.getBoolean("Archived")) throw new IOException("Character is archived");
		RemoteCharacters.metadata(document.getCompound("Profile"));
		validate(extract(document));
		return document;
	}

	public static CompoundTag extract(CompoundTag document) {
		var out = new CompoundTag();
		for (String key : Set.of("Profile", "Adapters", "Journey"))
			out.put(key, document.getCompound(key).copy());
		return out;
	}

	public static void validate(CompoundTag portable) throws IOException {
		if (!portable.getAllKeys().equals(Set.of("Profile", "Adapters", "Journey")))
			throw new IOException("Invalid portable character sections");
		for (String key : portable.getAllKeys())
			if (!portable.contains(key, Tag.TAG_COMPOUND))
				throw new IOException("Invalid portable character section: " + key);
		// Death is allowed in a server snapshot, but rejected when requesting entry.
		var metadata = portable.getCompound("Profile").copy();
		metadata.putString("Status", "ACTIVE");
		RemoteCharacters.metadata(metadata);
		ProfileCodec.character(portable.getCompound("Profile"));
		var group = portable.getCompound("Adapters");
		AdapterRegistry.validate(group);
		for (String key : group.getAllKeys()) {
			var entry = group.getCompound(key);
			if (!entry.contains("Data", Tag.TAG_COMPOUND)
					|| !entry.getAllKeys().equals(Set.of("Version", "Data")))
				throw new IOException("Invalid adapter envelope: " + key);
			var adapter =
					AdapterRegistry.all().stream()
							.filter(a -> a.id().toString().equals(key))
							.findFirst()
							.orElseThrow();
			if (adapter.scope() != CharacterDataAdapter.Scope.CHARACTER)
				throw new IOException("World data cannot be uploaded: " + key);
			if (key.equals(VanillaDataAdapter.PORTABLE)
					&& !VanillaDataAdapter.CHARACTER_KEYS.containsAll(
							entry.getCompound("Data").getAllKeys()))
				throw new IOException("Non-portable vanilla player fields");
		}
	}

	/** Caller holds the store's identity lock. Never compare revision numbers across stores. */
	public static void merge(CharacterStore store, UUID id, CompoundTag portable)
			throws IOException {
		merge(store, id, portable, false);
	}

	static void mergeMirror(CharacterStore store, UUID id, CompoundTag portable)
			throws IOException {
		merge(store, id, portable, true);
	}

	private static void merge(CharacterStore store, UUID id, CompoundTag portable, boolean mirror)
			throws IOException {
		validate(portable);
		if (!ProfileCodec.character(portable.getCompound("Profile")).id().equals(id))
			throw new IOException("Portable character identity mismatch");
		var document = store.read(id);
		long revision = document.getLong("Revision");
		for (String key : portable.getAllKeys()) document.put(key, portable.get(key).copy());
		if (mirror) store.commitMirror(id, document, revision);
		else store.commit(id, document, revision);
	}
}
