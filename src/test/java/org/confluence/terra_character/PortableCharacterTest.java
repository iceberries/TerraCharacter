package org.confluence.terra_character;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.*;

import org.confluence.terra_character.api.integration.CharacterDataAdapter;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.integration.*;
import org.confluence.terra_character.network.CharacterTransfer;
import org.confluence.terra_character.storage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

class PortableCharacterTest {
	@TempDir Path root;

	@BeforeAll
	static void adapters() {
		if (AdapterRegistry.all().stream()
				.noneMatch(a -> a.id().toString().equals(VanillaDataAdapter.PORTABLE))) {
			AdapterRegistry.register(new VanillaDataAdapter(CharacterDataAdapter.Scope.CHARACTER));
			AdapterRegistry.register(
					new VanillaDataAdapter(CharacterDataAdapter.Scope.CHARACTER_WORLD));
		}
	}

	private CharacterProfile profile() {
		return new CharacterProfile(
				UUID.randomUUID(),
				"Portable",
				CharacterDifficulty.SOFTCORE,
				new CharacterAppearance("terra_character:wide", "terra_character:short", Map.of()),
				CharacterProfile.Status.ACTIVE);
	}

	@Test
	void interruptedSoloSessionMustBeRecoveredBeforeUploadingCharacter() throws Exception {
		var p = profile();
		var store = new CharacterStore(root.resolve("characters"));
		store.create(p);
		assertEquals(store.read(p.id()), PortableCharacter.readForTravel(store, p.id()));
		SessionCheckpoint.begin(store, p.id(), root.resolve("world"));
		assertThrows(IOException.class, () -> PortableCharacter.readForTravel(store, p.id()));
		SessionCheckpoint.finish(store, p.id());
		assertEquals(store.read(p.id()), PortableCharacter.readForTravel(store, p.id()));
	}

	@Test
	void multiplayerSoloMultiplayerPreservesEachStoresWorldDataDespiteDifferentRevisions()
			throws Exception {
		var p = profile();
		var client = new CharacterStore(root.resolve("client"));
		client.create(p);
		var server = RemoteCharacters.store(root, UUID.randomUUID());
		try (var lock = client.lock(p.id())) {
			var d = client.read(p.id());
			d.getCompound("Worlds").put(UUID.randomUUID().toString(), new CompoundTag());
			d.getCompound("Journey").putInt("Research", 11);
			d.put(
					"Statistics",
					CharacterStatisticsCodec.encode(
							new CharacterStatistics(20, 20, false, 0, 0, 100)));
			client.commit(p.id(), d, 0);
		}
		try (var lock =
				RemoteCharacters.prepare(server, PortableCharacter.extract(client.read(p.id())))) {
			var d = server.read(p.id());
			d.getCompound("Worlds").put(UUID.randomUUID().toString(), new CompoundTag());
			for (int i = 0; i < 7; i++) {
				d.getCompound("Journey").putInt("Research", 22);
				d.put(
						"Statistics",
						CharacterStatisticsCodec.encode(
								new CharacterStatistics(20, 20, false, 0, 0, 200)));
				server.commit(p.id(), d, d.getLong("Revision"));
			}
		}
		var clientWorlds = client.read(p.id()).getCompound("Worlds").copy();
		var serverWorlds = server.read(p.id()).getCompound("Worlds").copy();
		try (var lock = client.lock(p.id())) {
			PortableCharacter.merge(client, p.id(), PortableCharacter.extract(server.read(p.id())));
			assertEquals(22, client.read(p.id()).getCompound("Journey").getInt("Research"));
			assertEquals(200, CharacterStatisticsCodec.read(client.read(p.id())).playTicks());
			var d = client.read(p.id());
			d.getCompound("Journey").putInt("Research", 99);
			d.put(
					"Statistics",
					CharacterStatisticsCodec.encode(
							new CharacterStatistics(18, 20, false, 0, 0, 500)));
			client.commit(p.id(), d, d.getLong("Revision"));
		}
		assertTrue(
				client.read(p.id()).getLong("Revision") < server.read(p.id()).getLong("Revision"));
		try (var lock =
				RemoteCharacters.prepare(server, PortableCharacter.extract(client.read(p.id())))) {
			assertEquals(99, server.read(p.id()).getCompound("Journey").getInt("Research"));
			assertEquals(500, CharacterStatisticsCodec.read(server.read(p.id())).playTicks());
			assertEquals(serverWorlds, server.read(p.id()).getCompound("Worlds"));
		}
		assertEquals(clientWorlds, client.read(p.id()).getCompound("Worlds"));
	}

	@Test
	void rejectsWorldAndAccountFieldsInPortableVanillaAndWrongIdentity() throws Exception {
		var p = profile();
		var store = new CharacterStore(root);
		store.create(p);
		var portable = PortableCharacter.extract(store.read(p.id()));
		var adapter =
				AdapterRegistry.all().stream()
						.filter(a -> a.id().toString().equals(VanillaDataAdapter.PORTABLE))
						.findFirst()
						.orElseThrow();
		var bad = new CompoundTag();
		bad.putString("Dimension", "minecraft:the_end");
		portable.getCompound("Adapters")
				.put(VanillaDataAdapter.PORTABLE, AdapterRegistry.envelope(adapter, bad));
		assertThrows(IOException.class, () -> PortableCharacter.validate(portable));
		portable.getCompound("Adapters").remove(VanillaDataAdapter.PORTABLE);
		portable.getCompound("Profile").putUUID("Id", UUID.randomUUID());
		try (var lock = store.lock(p.id())) {
			assertThrows(IOException.class, () -> PortableCharacter.merge(store, p.id(), portable));
		}
		portable.put("Worlds", new CompoundTag());
		assertThrows(IOException.class, () -> PortableCharacter.validate(portable));
	}

	@Test
	void transfersLargeIncompressibleDataInBoundedPartsAndRejectsReorderCorruptionAndOversize()
			throws Exception {
		var tag = new CompoundTag();
		byte[] data = new byte[150000];
		new Random(12).nextBytes(data);
		tag.putByteArray("Payload", data);
		var parts = CharacterTransfer.parts(tag);
		assertTrue(parts.size() > 1);
		var receiver = new CharacterTransfer.Assembly();
		CompoundTag result = null;
		for (int i = 0; i < parts.size(); i++) {
			assertTrue(parts.get(i).length <= CharacterTransfer.PART);
			result = receiver.accept(i, parts.size(), parts.get(i));
		}
		assertEquals(tag, result);
		assertThrows(
				IOException.class,
				() -> new CharacterTransfer.Assembly().accept(1, parts.size(), parts.get(0)));
		assertThrows(
				IOException.class,
				() -> new CharacterTransfer.Assembly().accept(0, 1000, parts.get(0)));
		assertThrows(
				IOException.class,
				() ->
						new CharacterTransfer.Assembly()
								.accept(0, 1, new byte[CharacterTransfer.PART + 1]));
		assertThrows(
				IOException.class,
				() -> new CharacterTransfer.Assembly().accept(0, 1, new byte[] {1, 2, 3}));
		var duplicate = new CharacterTransfer.Assembly();
		duplicate.accept(0, parts.size(), parts.get(0));
		assertThrows(IOException.class, () -> duplicate.accept(0, parts.size(), parts.get(0)));
	}
}
