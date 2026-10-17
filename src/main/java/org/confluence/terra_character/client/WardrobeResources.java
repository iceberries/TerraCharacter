package org.confluence.terra_character.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.model.WardrobeSlot;
import org.confluence.terra_character.service.WardrobeDefinitions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Client resource packs own visuals; servers store only bounded part IDs and colors. */
@EventBusSubscriber(
		modid = TerraCharacter.MODID,
		value = Dist.CLIENT,
		bus = EventBusSubscriber.Bus.MOD)
public final class WardrobeResources implements ResourceManagerReloadListener {
	private static volatile Map<String, WardrobeDefinitions.Part> parts = Map.of();
	private static volatile Map<String, WardrobeDefinitions.Preset> presets = Map.of();
	private static volatile Map<String, WardrobeDefinitions.Part> localParts = Map.of();
	private static final Set<String> warned = ConcurrentHashMap.newKeySet();
	private static final AtomicLong generation = new AtomicLong();

	public static long generation() {
		return generation.get();
	}

	public static WardrobeDefinitions.Part part(String id) {
		if (id == null) return null;
		var local = localParts.get(id);
		return local == null ? parts.get(id) : local;
	}

	public static List<WardrobeDefinitions.Part> parts(WardrobeSlot slot) {
		var combined = new HashMap<>(parts);
		combined.putAll(localParts);
		return combined.values().stream()
				.filter(p -> p.slot() == slot)
				.sorted(Comparator.comparing(WardrobeDefinitions.Part::id))
				.toList();
	}

	public static List<WardrobeDefinitions.Preset> presets() {
		return presets.values().stream()
				.sorted(Comparator.comparing(WardrobeDefinitions.Preset::id))
				.toList();
	}

	public static void warn(String id, Exception ex) {
		if (warned.add(id)) TerraCharacter.LOGGER.warn("Cannot use wardrobe resource {}", id, ex);
	}

	@SubscribeEvent
	public static void register(RegisterClientReloadListenersEvent event) {
		event.registerReloadListener(new WardrobeResources());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		warned.clear();
		parts = load(manager, "wardrobe/parts", WardrobeDefinitions::part);
		presets = load(manager, "wardrobe/presets", WardrobeDefinitions::preset);
		loadLocal();
		BaseSkinDyes.reload(manager);
		CharacterSkins.clearCache();
		generation.incrementAndGet();
	}

	private static Path localDirectory() {
		return Minecraft.getInstance()
				.gameDirectory
				.toPath()
				.resolve("terra_character/wardrobe/parts");
	}

	/** Unique generated IDs prevent overwriting another component or resource-pack definition. */
	static synchronized String saveLocal(JsonObject json) throws IOException {
		String filename = UUID.randomUUID().toString();
		String id = "terra_character:local/" + filename;
		var part = WardrobeDefinitions.part(id, json);
		Path directory = localDirectory();
		Files.createDirectories(directory);
		Path temporary = Files.createTempFile(directory, filename, ".tmp");
		try {
			Files.writeString(
					temporary,
					json.toString(),
					StandardCharsets.UTF_8,
					StandardOpenOption.TRUNCATE_EXISTING);
			try {
				Files.move(
						temporary,
						directory.resolve(filename + ".json"),
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(temporary, directory.resolve(filename + ".json"));
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
		var next = new HashMap<>(localParts);
		next.put(id, part);
		localParts = Map.copyOf(next);
		CharacterSkins.clearCache();
		generation.incrementAndGet();
		return id;
	}

	private static synchronized void loadLocal() {
		var next = new HashMap<String, WardrobeDefinitions.Part>();
		Path directory = localDirectory();
		if (Files.isDirectory(directory)) {
			try (var files = Files.list(directory)) {
				for (Path path :
						files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
					String name = path.getFileName().toString();
					String id = "terra_character:local/" + name.substring(0, name.length() - 5);
					try {
						if (Files.size(path) > 131072) throw new IOException("Part JSON too large");
						try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
							next.put(
									id,
									WardrobeDefinitions.part(
											id, JsonParser.parseReader(reader).getAsJsonObject()));
						}
					} catch (Exception ex) {
						warn(id, ex);
					}
				}
			} catch (IOException ex) {
				warn("local_parts", ex);
			}
		}
		localParts = Map.copyOf(next);
	}

	@FunctionalInterface
	private interface Parser<T> {
		T parse(String id, JsonObject json);
	}

	private static <T> Map<String, T> load(
			ResourceManager manager, String folder, Parser<T> parser) {
		var result = new TreeMap<String, T>();
		manager.listResources(folder, id -> id.getPath().endsWith(".json"))
				.forEach(
						(location, resource) -> {
							String path = location.getPath();
							String id =
									ResourceLocation.fromNamespaceAndPath(
													location.getNamespace(),
													path.substring(
															folder.length() + 1, path.length() - 5))
											.toString();
							try (var reader = resource.openAsReader()) {
								result.put(
										id,
										parser.parse(
												id,
												JsonParser.parseReader(reader).getAsJsonObject()));
							} catch (Exception ex) {
								warn(id, ex);
							}
						});
		return Map.copyOf(result);
	}
}
