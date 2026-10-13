package org.confluence.terra_character.service;

import com.google.gson.*;

import org.confluence.terra_character.api.model.*;

import java.util.*;

/** Resource pack schema for pixel patches on the vanilla 64x64 skin atlas. */
public final class WardrobeDefinitions {
	private WardrobeDefinitions() {}

	public record Fragment(
			int x,
			int y,
			int width,
			int height,
			String body,
			String texture,
			List<Integer> pixels,
			String tint,
			boolean replace) {
		public Fragment {
			pixels = List.copyOf(Objects.requireNonNull(pixels, "pixels"));
			Objects.requireNonNull(body, "body");
			if (x < 0
					|| y < 0
					|| width < 1
					|| height < 1
					|| width > 64
					|| height > 64
					|| x > 64 - width
					|| y > 64 - height)
				throw new IllegalArgumentException("Fragment outside 64x64 skin atlas");
			if (!Set.of("any", "slim", "wide").contains(body))
				throw new IllegalArgumentException("Unknown body model");
			if ((texture == null) == pixels.isEmpty())
				throw new IllegalArgumentException("Use either texture or pixels");
			if (texture != null) id(texture);
			if (texture == null && pixels.size() != width * height)
				throw new IllegalArgumentException("Wrong pixel count");
			if (tint != null) id(tint);
		}
	}

	public record Part(String id, String name, WardrobeSlot slot, List<Fragment> fragments) {
		public Part {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(slot, "slot");
			WardrobeDefinitions.id(id);
			fragments = List.copyOf(fragments);
			if (fragments.isEmpty() || fragments.size() > 128)
				throw new IllegalArgumentException("Invalid fragment count");
		}
	}

	public record Preset(String id, String name, CharacterAppearance appearance) {
		public Preset {
			WardrobeDefinitions.id(id);
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(appearance, "appearance");
		}
	}

	public static Part part(String key, JsonObject json) {
		if (json == null
				|| !json.has("fragments")
				|| !json.get("fragments").isJsonArray()
				|| !json.has("slot")
				|| json.get("slot").isJsonNull())
			throw new IllegalArgumentException("Part requires fragments and slot");
		var fragments = new ArrayList<Fragment>();
		for (var entry : json.getAsJsonArray("fragments")) {
			var f = entry.getAsJsonObject();
			var rect = f.getAsJsonArray("rect");
			if (rect.size() != 4)
				throw new IllegalArgumentException("rect requires x,y,width,height");
			var pixels = new ArrayList<Integer>();
			if (f.has("pixels"))
				for (var pixel : f.getAsJsonArray("pixels")) {
					String hex = pixel.getAsString();
					if (!hex.matches("[0-9a-fA-F]{8}"))
						throw new IllegalArgumentException("Pixel must be AARRGGBB");
					pixels.add((int) Long.parseLong(hex, 16));
				}
			String blend = text(f, "blend", "over");
			if (!Set.of("over", "replace").contains(blend))
				throw new IllegalArgumentException("Unknown blend operation");
			fragments.add(
					new Fragment(
							rect.get(0).getAsInt(),
							rect.get(1).getAsInt(),
							rect.get(2).getAsInt(),
							rect.get(3).getAsInt(),
							text(f, "body", "any"),
							text(f, "texture", null),
							pixels,
							text(f, "tint", null),
							blend.equals("replace")));
		}
		return new Part(
				key,
				text(json, "name", key),
				WardrobeSlot.valueOf(json.get("slot").getAsString().toUpperCase(Locale.ROOT)),
				fragments);
	}

	public static Preset preset(String key, JsonObject json) {
		var colors = new HashMap<String, Integer>();
		if (json.has("colors"))
			json.getAsJsonObject("colors")
					.entrySet()
					.forEach(
							e -> {
								String hex = e.getValue().getAsString();
								if (!hex.matches("[0-9a-fA-F]{6}"))
									throw new IllegalArgumentException("Color must be RRGGBB");
								colors.put(e.getKey(), Integer.parseInt(hex, 16));
							});
		var parts = new EnumMap<WardrobeSlot, String>(WardrobeSlot.class);
		if (json.has("parts"))
			json.getAsJsonObject("parts")
					.entrySet()
					.forEach(
							e -> {
								if (parts.putIfAbsent(
												WardrobeSlot.valueOf(
														e.getKey().toUpperCase(Locale.ROOT)),
												e.getValue().getAsString())
										!= null)
									throw new IllegalArgumentException("Duplicate clothing slot");
							});
		var masks = new EnumMap<WardrobeSlot, List<Integer>>(WardrobeSlot.class);
		if (json.has("regions"))
			json.getAsJsonObject("regions")
					.entrySet()
					.forEach(
							e -> {
								var pixels = new ArrayList<Integer>();
								for (var pixel : e.getValue().getAsJsonArray())
									pixels.add(pixel.getAsInt());
								if (masks.putIfAbsent(
												WardrobeSlot.valueOf(
														e.getKey().toUpperCase(Locale.ROOT)),
												pixels)
										!= null)
									throw new IllegalArgumentException("Duplicate region slot");
							});
		return new Preset(
				key,
				text(json, "name", key),
				new CharacterAppearance(
						text(json, "body", "terra_character:slim"),
						text(json, "hair", "terra_character:fire_berry"),
						colors,
						SkinPaint.EMPTY,
						new Wardrobe(parts),
						new DyeMasks(masks)));
	}

	private static String text(JsonObject json, String key, String fallback) {
		return json.has(key) ? json.get(key).getAsString() : fallback;
	}

	private static void id(String value) {
		if (value.length() > 255 || !value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
			throw new IllegalArgumentException("Invalid resource ID: " + value);
	}
}
