package org.confluence.character_portal.integration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import org.confluence.character_portal.api.integration.CharacterDataAdapter;
import org.confluence.character_portal.api.model.WorldSettings;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class AdapterRegistry {
	private static final Map<ResourceLocation, CharacterDataAdapter> ADAPTERS =
			new LinkedHashMap<>();
	private static final Map<String, Consumer<WorldSettings>> WORLD_PROVIDERS = new HashMap<>();

	private AdapterRegistry() {}

	public static void register(CharacterDataAdapter adapter) {
		registerAll(List.of(adapter));
	}

	static void registerAll(List<CharacterDataAdapter> adapters) {
		var additions = new LinkedHashMap<ResourceLocation, CharacterDataAdapter>();
		for (var adapter : adapters) {
			var id = Objects.requireNonNull(adapter.id(), "adapter.id");
			Objects.requireNonNull(adapter.scope(), "adapter.scope");
			if (adapter.schemaVersion() < 1 || additions.putIfAbsent(id, adapter) != null) {
				throw new IllegalArgumentException("Duplicate or invalid adapter: " + id);
			}
		}
		synchronized (AdapterRegistry.class) {
			for (var id : additions.keySet()) {
				if (ADAPTERS.containsKey(id)) {
					throw new IllegalArgumentException("Duplicate adapter: " + id);
				}
			}
			ADAPTERS.putAll(additions);
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
			if (adapter instanceof PlayerAttributesAdapter)
				try {
					PlayerAttributesAdapter.validate(group.getCompound(key).getCompound("Data")
						.getList("Attributes", Tag.TAG_COMPOUND));
				} catch (IllegalArgumentException ex) {
					throw new IOException(ex.getMessage(), ex);
				}
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
