package org.confluence.terra_character.integration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import org.confluence.terra_character.api.integration.CharacterDataAdapter;
import org.confluence.terra_character.api.model.WorldSettings;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public final class AdapterRegistry {
	private static final Map<ResourceLocation, CharacterDataAdapter> ADAPTERS =
			new LinkedHashMap<>();
	private static final Map<String, Consumer<WorldSettings>> WORLD_PROVIDERS = new HashMap<>();

	private AdapterRegistry() {}

	public static synchronized void register(CharacterDataAdapter adapter) {
		Objects.requireNonNull(adapter, "adapter");
		Objects.requireNonNull(adapter.id(), "adapter.id");
		Objects.requireNonNull(adapter.scope(), "adapter.scope");
		if (adapter.schemaVersion() < 1 || ADAPTERS.putIfAbsent(adapter.id(), adapter) != null) {
			throw new IllegalArgumentException("Duplicate or invalid adapter: " + adapter.id());
		}
	}

	public static synchronized List<CharacterDataAdapter> all() {
		return List.copyOf(ADAPTERS.values());
	}

	public static synchronized void registerWorldProvider(
			String namespace, Consumer<WorldSettings> validator) {
		Objects.requireNonNull(validator, "validator");
		ResourceLocation.fromNamespaceAndPath(namespace, "validation");
		if (WORLD_PROVIDERS.putIfAbsent(namespace, validator) != null)
			throw new IllegalArgumentException("Duplicate world option provider: " + namespace);
	}

	public static void validate(CompoundTag group) throws IOException {
		Map<ResourceLocation, CharacterDataAdapter> adapters;
		synchronized (AdapterRegistry.class) {
			adapters = Map.copyOf(ADAPTERS);
		}
		for (String key : group.getAllKeys()) {
			var id = ResourceLocation.tryParse(key);
			var adapter = id == null ? null : adapters.get(id);
			if (adapter == null) throw new IOException("Missing character adapter: " + key);
			if (group.getCompound(key).getInt("Version") != adapter.schemaVersion())
				throw new IOException("Unsupported adapter version: " + key);
		}
	}

	public static void validateWorld(WorldSettings settings) throws IOException {
		Map<String, Consumer<WorldSettings>> providers;
		synchronized (AdapterRegistry.class) {
			providers = Map.copyOf(WORLD_PROVIDERS);
		}
		var checked = new HashSet<String>();
		for (String key : settings.extensions().keySet()) {
			String provider = key.substring(0, key.indexOf(':'));
			var validator = providers.get(provider);
			if (validator == null)
				throw new IOException("Missing world option provider: " + provider);
			if (checked.add(provider))
				try {
					validator.accept(settings);
				} catch (RuntimeException ex) {
					throw new IOException(
							"Unsupported world options: " + provider + ": " + ex.getMessage(), ex);
				}
		}
	}

	public static CompoundTag envelope(CharacterDataAdapter adapter, CompoundTag data) {
		var tag = new CompoundTag();
		tag.putInt("Version", adapter.schemaVersion());
		tag.put("Data", data.copy());
		return tag;
	}
}
