package org.confluence.terra_character;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import org.confluence.terra_character.api.integration.CharacterDataAdapter;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.integration.AdapterRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

class IntegrationBoundaryTest {
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
		public CompoundTag capture(ServerPlayer p) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void restore(ServerPlayer p, CompoundTag tag, int version) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void initialize(ServerPlayer p) {
			throw new UnsupportedOperationException();
		}
	}

	@Test
	void missingAndFutureAdapterDataAreRejectedWithoutMutation() {
		var original = new CompoundTag();
		var unknown = new CompoundTag();
		unknown.putInt("Version", 1);
		unknown.putString("Payload", "retain me");
		original.put("missing_mod:character", unknown);
		var copy = original.copy();
		assertThrows(IOException.class, () -> AdapterRegistry.validate(original));
		assertEquals(copy, original);
		var adapter = new Adapter(ResourceLocation.parse("storage_test:future"));
		AdapterRegistry.register(adapter);
		var future = new CompoundTag();
		var value = new CompoundTag();
		value.putInt("Version", 99);
		future.put(adapter.id().toString(), value);
		assertThrows(IOException.class, () -> AdapterRegistry.validate(future));
		future.put(adapter.id().toString(), AdapterRegistry.envelope(adapter, new CompoundTag()));
		assertDoesNotThrow(() -> AdapterRegistry.validate(future));
	}

	@Test
	void providerMustUnderstandOptionsAndCannotRegisterTwice() {
		var settings =
				new WorldSettings(
						"1",
						"minecraft:normal",
						WorldDifficulty.NORMAL,
						Map.of("options_test:mode", "supported"));
		assertThrows(IOException.class, () -> AdapterRegistry.validateWorld(settings));
		AdapterRegistry.registerWorldProvider(
				"options_test",
				value -> {
					if (!value.extensions().get("options_test:mode").equals("supported"))
						throw new IllegalArgumentException("Unknown mode");
				});
		assertDoesNotThrow(() -> AdapterRegistry.validateWorld(settings));
		assertThrows(
				IllegalArgumentException.class,
				() -> AdapterRegistry.registerWorldProvider("options_test", value -> {}));
		assertThrows(
				IOException.class,
				() ->
						AdapterRegistry.validateWorld(
								new WorldSettings(
										"1",
										"minecraft:normal",
										WorldDifficulty.NORMAL,
										Map.of("options_test:mode", "future"))));
	}

	@Test
	void envelopeIsAnOwnedCopyAndDuplicateAdaptersAreRejected() {
		var adapter = new Adapter(ResourceLocation.parse("storage_test:unique"));
		AdapterRegistry.register(adapter);
		assertThrows(IllegalArgumentException.class, () -> AdapterRegistry.register(adapter));
		var source = new CompoundTag();
		source.putInt("Items", 42);
		var wrapper = AdapterRegistry.envelope(adapter, source);
		source.putInt("Items", 0);
		assertEquals(42, wrapper.getCompound("Data").getInt("Items"));
	}
}
