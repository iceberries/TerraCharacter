package org.confluence.terra_character.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Client resource packs own visuals; servers store only bounded part IDs and colors. */
@EventBusSubscriber(
		modid = TerraCharacter.MODID,
		value = Dist.CLIENT,
		bus = EventBusSubscriber.Bus.MOD)
public final class WardrobeResources implements ResourceManagerReloadListener {
	private static volatile Map<String, WardrobeDefinitions.Part> parts = Map.of();
	private static volatile Map<String, WardrobeDefinitions.Preset> presets = Map.of();
	private static final Set<String> warned = ConcurrentHashMap.newKeySet();
	private static volatile long generation;

	public static long generation() {
		return generation;
	}

	public static WardrobeDefinitions.Part part(String id) {
		return id == null ? null : parts.get(id);
	}

	public static List<WardrobeDefinitions.Part> parts(WardrobeSlot slot) {
		return parts.values().stream()
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
		BaseSkinDyes.reload(manager);
		CharacterSkins.clearCache();
		generation++;
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
