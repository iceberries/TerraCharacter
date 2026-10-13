package org.confluence.terra_character.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import org.confluence.terra_character.api.model.CharacterAppearance;
import org.confluence.terra_character.api.model.SkinBase;
import org.confluence.terra_character.api.model.WardrobeSlot;
import org.confluence.terra_character.service.SkinLayout;

import java.awt.Color;
import java.util.*;

/** Explicit source UV masks: recolor materials without guessing skin colors at runtime. */
final class BaseSkinDyes {
	private record Channel(String id, WardrobeSlot slot, int reference, BitSet pixels) {}

	private static Map<String, List<Channel>> definitions = Map.of();
	private static CharacterAppearance cachedAppearance;
	private static List<Channel> cachedChannels = List.of();
	private static SkinBase cachedBase;
	private static List<Channel> accountChannels = List.of();

	/**
	 * Anatomical UV regions work for any account skin without guessing clothing or facial features.
	 */
	private static List<Channel> accountChannels(CharacterAppearance value) {
		var base = value.baseSkin();
		if (base.equals(cachedBase)) return accountChannels;
		var result = new ArrayList<Channel>();
		var groups =
				List.of(
						List.of(SkinLayout.Part.HEAD),
						List.of(SkinLayout.Part.BODY),
						List.of(SkinLayout.Part.RIGHT_ARM, SkinLayout.Part.LEFT_ARM),
						List.of(SkinLayout.Part.RIGHT_LEG, SkinLayout.Part.LEFT_LEG));
		var slots =
				List.of(
						WardrobeSlot.SKIN,
						WardrobeSlot.OUTERWEAR,
						WardrobeSlot.GLOVES,
						WardrobeSlot.PANTS);
		var names = List.of("head", "body", "arms", "legs");
		for (int i = 0; i < groups.size(); i++) {
			var mask = new BitSet(4096);
			long r = 0, g = 0, b = 0, total = 0;
			for (var part : groups.get(i))
				for (var layer : SkinLayout.Layer.values())
					for (var face : SkinLayout.Face.values()) {
						var canonical = SkinLayout.region(part, face, layer, true);
						var source = SkinLayout.region(part, face, layer, base.slim());
						for (int y = 0; y < canonical.height(); y++)
							for (int x = 0; x < canonical.width(); x++) {
								mask.set((canonical.y() + y) * 64 + canonical.x() + x);
								int
										pixel =
												base.pixel(
														source.x()
																+ x
																		* source.width()
																		/ canonical.width(),
														source.y() + y),
										alpha = pixel >>> 24;
								r += (long) (pixel >> 16 & 255) * alpha;
								g += (long) (pixel >> 8 & 255) * alpha;
								b += (long) (pixel & 255) * alpha;
								total += alpha;
							}
					}
			int reference =
					total == 0
							? 0xFFFFFF
							: ((int) (r / total) << 16)
									| ((int) (g / total) << 8)
									| (int) (b / total);
			result.add(
					new Channel(
							"terra_character:account_" + names.get(i),
							slots.get(i),
							reference,
							mask));
		}
		cachedBase = base;
		accountChannels = List.copyOf(result);
		return accountChannels;
	}

	static void reload(ResourceManager manager) {
		var result = new HashMap<String, List<Channel>>();
		manager.listResources("wardrobe/dyes", id -> id.getPath().endsWith(".json"))
				.forEach(
						(location, resource) -> {
							try (var reader = resource.openAsReader()) {
								var json = JsonParser.parseReader(reader).getAsJsonObject();
								String skin =
										ResourceLocation.parse(json.get("skin").getAsString())
												.toString();
								var channels = new ArrayList<Channel>();
								var ids = new HashSet<String>();
								var used = new BitSet(4096);
								for (var entry : json.getAsJsonArray("channels")) {
									var c = entry.getAsJsonObject();
									String id =
											ResourceLocation.parse(c.get("id").getAsString())
													.toString();
									if (!ids.add(id) || ids.size() > 32)
										throw new IllegalArgumentException(
												"Duplicate or too many dye channels");
									String hex = c.get("reference").getAsString();
									if (!hex.matches("[0-9a-fA-F]{6}"))
										throw new IllegalArgumentException(
												"Reference must be RRGGBB");
									var mask = new BitSet(4096);
									for (var pixel : c.getAsJsonArray("pixels")) {
										int p = pixel.getAsInt();
										if (p < 0
												|| p >= 4096
												|| used.get(p)
												|| (!SkinLayout.contains(
																SkinLayout.Layer.BASE,
																true,
																p % 64,
																p / 64)
														&& !SkinLayout.contains(
																SkinLayout.Layer.OUTER,
																true,
																p % 64,
																p / 64)))
											throw new IllegalArgumentException(
													"Invalid or overlapping dye pixel");
										mask.set(p);
									}
									if (mask.isEmpty())
										throw new IllegalArgumentException("Empty dye mask");
									used.or(mask);
									channels.add(
											new Channel(
													id,
													WardrobeSlot.valueOf(
															c.get("slot")
																	.getAsString()
																	.toUpperCase(Locale.ROOT)),
													Integer.parseInt(hex, 16),
													mask));
								}
								result.put(skin, List.copyOf(channels));
							} catch (Exception ex) {
								WardrobeResources.warn(location.toString(), ex);
							}
						});
		definitions = Map.copyOf(result);
		cachedAppearance = null;
		cachedChannels = List.of();
	}

	private static List<Channel> definitions(CharacterAppearance value) {
		var originals =
				value.baseSkin().isEmpty()
						? definitions.getOrDefault(value.hairStyle(), List.of())
						: accountChannels(value);
		if (value.dyeMasks().regions().isEmpty()) return originals;
		if (value.equals(cachedAppearance)) return cachedChannels;
		var claimed = new BitSet(4096);
		value.dyeMasks().regions().values().forEach(list -> list.forEach(claimed::set));
		var result = new ArrayList<Channel>();
		var seen = EnumSet.noneOf(WardrobeSlot.class);
		for (var channel : originals) {
			var override = value.dyeMasks().regions().get(channel.slot());
			var mask = new BitSet(4096);
			if (override != null) override.forEach(mask::set);
			else {
				mask.or(channel.pixels());
				mask.andNot(claimed);
			}
			result.add(new Channel(channel.id(), channel.slot(), channel.reference(), mask));
			seen.add(channel.slot());
		}
		value.dyeMasks()
				.regions()
				.forEach(
						(slot, list) -> {
							if (seen.contains(slot)) return;
							var mask = new BitSet(4096);
							list.forEach(mask::set);
							result.add(
									new Channel(
											"terra_character:base_"
													+ slot.name().toLowerCase(Locale.ROOT),
											slot,
											0xFFFFFF,
											mask));
						});
		cachedAppearance = value;
		cachedChannels = List.copyOf(result);
		return cachedChannels;
	}

	static BitSet pixels(CharacterAppearance value, WardrobeSlot slot) {
		var result = new BitSet(4096);
		for (var c : definitions(value)) if (c.slot() == slot) result.or(c.pixels());
		return result;
	}

	static int sourceIndex(int x, int y, boolean slim) {
		for (var layer : SkinLayout.Layer.values())
			for (var part : SkinLayout.Part.values())
				for (var face : SkinLayout.Face.values()) {
					var target = SkinLayout.region(part, face, layer, slim);
					if (!target.contains(x, y)) continue;
					var source = SkinLayout.region(part, face, layer, true);
					return (source.y() + y - target.y()) * 64
							+ source.x()
							+ (x - target.x()) * source.width() / target.width();
				}
		return -1;
	}

	static List<String> channels(CharacterAppearance value, WardrobeSlot slot) {
		return definitions(value).stream()
				.filter(c -> slot == null || slot == c.slot() || slot.occupied().contains(c.slot()))
				.filter(
						c ->
								value.dyeMasks().regions().containsKey(c.slot())
										|| value.wardrobe().parts().entrySet().stream()
												.noneMatch(
														e ->
																WardrobeResources.part(e.getValue())
																				!= null
																		&& (e.getKey() == c.slot()
																				|| e.getKey()
																						.occupied()
																						.contains(
																								c
																										.slot()))))
				.map(Channel::id)
				.toList();
	}

	static int reference(CharacterAppearance value, String id) {
		return definitions(value).stream()
				.filter(c -> c.id().equals(id))
				.map(Channel::reference)
				.findFirst()
				.orElse(0xFFFFFF);
	}

	static Boolean marked(CharacterAppearance value, WardrobeSlot slot, int x, int y) {
		var channels = channels(value, slot);
		if (channels.isEmpty()) return null;
		boolean slim = value.bodyStyle().endsWith(":slim");
		for (var layer : SkinLayout.Layer.values())
			for (var part : SkinLayout.Part.values())
				for (var face : SkinLayout.Face.values()) {
					var target = SkinLayout.region(part, face, layer, slim);
					if (!target.contains(x, y)) continue;
					var source = SkinLayout.region(part, face, layer, true);
					int index =
							(source.y() + y - target.y()) * 64
									+ source.x()
									+ (x - target.x()) * source.width() / target.width();
					for (var channel : definitions(value))
						if (channels.contains(channel.id()) && channel.pixels().get(index))
							return true;
					return false;
				}
		return false;
	}

	static void apply(NativeImage image, CharacterAppearance value) {
		apply(image, value, false);
	}

	static void applyCustom(NativeImage image, CharacterAppearance value) {
		apply(image, value, true);
	}

	static int dyePaintPixel(CharacterAppearance value, int x, int y, int argb) {
		if (value.dyeMasks().regions().isEmpty() || (argb >>> 24) == 0) return argb;
		int p = sourceIndex(x, y, value.bodyStyle().endsWith(":slim"));
		for (var channel : definitions(value)) {
			Integer rgb = value.colors().get(channel.id());
			if (p >= 0
					&& value.dyeMasks().regions().containsKey(channel.slot())
					&& channel.pixels().get(p)
					&& rgb != null) return tint(argb, channel.reference(), rgb);
		}
		return argb;
	}

	private static int tint(int argb, int reference, int rgb) {
		if (reference == rgb) return argb;
		float[] from = hsv(reference), to = hsv(rgb), original = hsv(argb);
		float hue = original[0] + to[0] - from[0];
		hue -= (float) Math.floor(hue);
		float saturation = Math.clamp(original[1] + to[1] - from[1], 0f, 1f);
		float brightness = Math.clamp(original[2] * to[2] / Math.max(.001f, from[2]), 0f, 1f);
		return (argb & 0xFF000000) | (Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF);
	}

	private static void apply(NativeImage image, CharacterAppearance value, boolean custom) {
		boolean slim = value.bodyStyle().endsWith(":slim");
		var available = new HashSet<>(channels(value, null));
		for (var channel : definitions(value)) {
			if (value.dyeMasks().regions().containsKey(channel.slot()) != custom) continue;
			Integer rgb = value.colors().get(channel.id());
			if (rgb == null || rgb == channel.reference() || !available.contains(channel.id()))
				continue;
			for (var layer : SkinLayout.Layer.values())
				for (var part : SkinLayout.Part.values())
					for (var face : SkinLayout.Face.values()) {
						var source = SkinLayout.region(part, face, layer, true);
						var target = SkinLayout.region(part, face, layer, slim);
						for (int y = 0; y < target.height(); y++)
							for (int x = 0; x < target.width(); x++) {
								int sx = source.x() + x * source.width() / target.width(),
										sy = source.y() + y;
								if (!channel.pixels().get(sy * 64 + sx)) continue;
								int px = target.x() + x,
										py = target.y() + y,
										argb =
												CharacterSkins.swapRedBlue(
														image.getPixelRGBA(px, py));
								if ((argb >>> 24) == 0) continue;
								int tinted = tint(argb, channel.reference(), rgb);
								image.setPixelRGBA(px, py, CharacterSkins.swapRedBlue(tinted));
							}
					}
		}
	}

	private static float[] hsv(int rgb) {
		return Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
	}

	private BaseSkinDyes() {}
}
