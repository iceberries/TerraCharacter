package org.confluence.terra_character.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.confluence.terra_character.api.model.WardrobeSlot;

import java.util.BitSet;
import java.util.Locale;
import java.util.Objects;

/** Exports standard wardrobe JSON, but only within the selected slot's explicitly owned pixels. */
public final class SelectedPart {
	private SelectedPart() {}

	public static boolean belongsTo(SkinSelection selection, BitSet allowed) {
		Objects.requireNonNull(allowed, "allowed");
		var r = selection.bounds();
		for (int y = 0; y < r.height(); y++)
			for (int x = 0; x < r.width(); x++)
				if (!allowed.get((r.y() + y) * 64 + r.x() + x)) return false;
		return true;
	}

	public static JsonObject json(
			String name, WardrobeSlot slot, SkinSelection selection, BitSet allowed, boolean slim) {
		if (name == null || name.isBlank() || name.length() > 80)
			throw new IllegalArgumentException("Invalid part name");
		Objects.requireNonNull(slot, "slot");
		if (!belongsTo(selection, allowed))
			throw new IllegalArgumentException(
					"Selection crosses the target slot's pixel boundary");
		var r = selection.bounds();
		var rect = new JsonArray();
		for (int value : new int[] {r.x(), r.y(), r.width(), r.height()}) rect.add(value);
		var pixels = new JsonArray();
		for (int y = 0; y < r.height(); y++)
			for (int x = 0; x < r.width(); x++)
				pixels.add(String.format(Locale.ROOT, "%08X", selection.pixel(x, y)));
		var fragment = new JsonObject();
		fragment.add("rect", rect);
		fragment.add("pixels", pixels);
		fragment.addProperty("body", slim ? "slim" : "wide");
		fragment.addProperty("blend", "replace");
		var fragments = new JsonArray();
		fragments.add(fragment);
		var json = new JsonObject();
		json.addProperty("name", name.strip());
		json.addProperty("slot", slot.name().toLowerCase(Locale.ROOT));
		json.add("fragments", fragments);
		return json;
	}
}
