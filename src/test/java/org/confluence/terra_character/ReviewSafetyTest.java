package org.confluence.terra_character;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import org.confluence.terra_character.api.integration.CharacterDataAdapter;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.integration.AdapterRegistry;
import org.confluence.terra_character.network.CharacterTransfer;
import org.confluence.terra_character.service.WardrobeDefinitions;
import org.confluence.terra_character.service.WorldIndex;
import org.confluence.terra_character.storage.AtomicNbt;
import org.confluence.terra_character.storage.CharacterStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

class ReviewSafetyTest {
	@Test
	void missingWorldErrorMessageCannotBreakTheWorldList() {
		var entry = new WorldIndex.Entry(Path.of("missing-world"), null, false, false, false, null);
		assertFalse(entry.error().isEmpty());
		assertNull(entry.profile());
	}

	@TempDir private Path root;

	private record Adapter(ResourceLocation id) implements CharacterDataAdapter {
		@Override
		public int schemaVersion() {
			return 1;
		}

		@Override
		public Scope scope() {
			return Scope.CHARACTER;
		}

		@Override
		public CompoundTag capture(ServerPlayer player) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void initialize(ServerPlayer player) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void restore(ServerPlayer player, CompoundTag data, int version) {
			throw new UnsupportedOperationException();
		}
	}

	@Test
	void concurrentRegistrationAndValidationUseConsistentSnapshots() throws Exception {
		String prefix = "review_" + UUID.randomUUID().toString().replace("-", "");
		try (var executor = Executors.newFixedThreadPool(4)) {
			var futures = new ArrayList<Future<?>>();
			for (int i = 0; i < 48; i++) {
				String namespace = prefix + "_" + i;
				futures.add(
						executor.submit(
								() -> {
									var adapter =
											new Adapter(
													ResourceLocation.fromNamespaceAndPath(
															namespace, "character"));
									AdapterRegistry.register(adapter);
									AdapterRegistry.registerWorldProvider(
											namespace, settings -> {});
									var group = new CompoundTag();
									group.put(
											adapter.id().toString(),
											AdapterRegistry.envelope(adapter, new CompoundTag()));
									try {
										for (int j = 0; j < 10; j++) {
											AdapterRegistry.validate(group);
											AdapterRegistry.validateWorld(
													new WorldSettings(
															"1",
															"minecraft:normal",
															WorldDifficulty.NORMAL,
															Map.of(namespace + ":mode", "test")));
											var snapshot = AdapterRegistry.all();
											assertEquals(
													snapshot.size(),
													snapshot.stream()
															.map(CharacterDataAdapter::id)
															.distinct()
															.count());
										}
									} catch (IOException ex) {
										throw new CompletionException(ex);
									}
								}));
			}
			for (var future : futures) future.get(20, TimeUnit.SECONDS);
		}
	}

	@Test
	void invalidRegistrationCannotPolluteRegistry() {
		int before = AdapterRegistry.all().size();
		assertThrows(NullPointerException.class, () -> AdapterRegistry.register(null));
		assertThrows(NullPointerException.class, () -> AdapterRegistry.register(new Adapter(null)));
		assertEquals(before, AdapterRegistry.all().size());
		assertThrows(
				NullPointerException.class,
				() -> AdapterRegistry.registerWorldProvider("review_null", null));
		assertDoesNotThrow(
				() -> AdapterRegistry.registerWorldProvider("review_null", settings -> {}));
	}

	@Test
	void concurrentRepeatedLockCloseAllowsReacquisition() throws Exception {
		var store = new CharacterStore(root);
		UUID id = UUID.randomUUID();
		var lock = store.lock(id);
		try (var executor = Executors.newFixedThreadPool(2)) {
			Callable<Void> close =
					() -> {
						lock.close();
						return null;
					};
			var a = executor.submit(close);
			var b = executor.submit(close);
			a.get(10, TimeUnit.SECONDS);
			b.get(10, TimeUnit.SECONDS);
		}
		assertFalse(lock.isValid());
		try (var next = store.lock(id)) {
			assertTrue(next.isValid());
		}
	}

	@Test
	void malformedTransfersAndDuplicateClothingSlotsAreRejected() {
		assertThrows(IOException.class, () -> new CharacterTransfer.Assembly().accept(0, 1, null));
		var duplicate =
				JsonParser.parseString(
								"{\"parts\":{\"eyes\":\"example:a\",\"EYES\":\"example:b\"}}")
						.getAsJsonObject();
		assertThrows(
				IllegalArgumentException.class,
				() -> WardrobeDefinitions.preset("example:duplicate", duplicate));
		var masks =
				JsonParser.parseString("{\"regions\":{\"eyes\":[778],\"EYES\":[781]}}")
						.getAsJsonObject();
		assertThrows(
				IllegalArgumentException.class,
				() -> WardrobeDefinitions.preset("example:duplicate", masks));
		assertThrows(
				IllegalArgumentException.class,
				() ->
						WardrobeDefinitions.part(
								"example:missing", JsonParser.parseString("{}").getAsJsonObject()));
	}

	@Test
	void relativeFileNameIsAValidAtomicWriteTarget() throws Exception {
		Path target = Path.of("review-" + UUID.randomUUID() + ".nbt");
		var tag = new CompoundTag();
		tag.putInt("Value", 7);
		try {
			AtomicNbt.write(target, tag, false);
			assertEquals(tag, AtomicNbt.read(target));
		} finally {
			Files.deleteIfExists(target);
		}
	}
}
