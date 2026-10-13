package org.confluence.terra_character.api.model;

import java.util.*;

/** Immutable selections; unknown resource IDs survive saves and resource pack changes. */
public record Wardrobe(Map<WardrobeSlot, String> parts) {
	public static final Wardrobe EMPTY = new Wardrobe(Map.of());

	public Wardrobe {
		parts = Map.copyOf(Objects.requireNonNull(parts));
		parts.values()
				.forEach(
						id -> {
							ProfileValidation.resourceId(id);
							if (id.length() > 255)
								throw new IllegalArgumentException("Part ID too long");
						});
		var used = EnumSet.noneOf(WardrobeSlot.class);
		for (var slot : parts.keySet())
			for (var occupied : slot.occupied())
				if (!used.add(occupied))
					throw new IllegalArgumentException("Conflicting clothing selections");
	}

	public Wardrobe equip(WardrobeSlot slot, String id) {
		var next = new EnumMap<WardrobeSlot, String>(WardrobeSlot.class);
		next.putAll(parts);
		next.keySet().removeIf(other -> !Collections.disjoint(slot.occupied(), other.occupied()));
		if (id != null) next.put(slot, id);
		return new Wardrobe(next);
	}
}
