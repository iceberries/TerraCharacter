package org.confluence.terra_character.api.model;

import java.util.Set;

/** Logical editing categories, not additional player model layers. */
public enum WardrobeSlot {
	SKIN,
	EYES,
	MOUTH,
	SKIN_DETAIL,
	UNDERWEAR,
	SOCKS,
	PANTS,
	SHOES,
	GLOVES,
	HAIR,
	OUTERWEAR,
	OVERALLS,
	OUTFIT;

	public Set<WardrobeSlot> occupied() {
		return switch (this) {
			case OUTFIT -> Set.of(UNDERWEAR, OUTERWEAR, PANTS, SHOES);
			case OVERALLS -> Set.of(UNDERWEAR, OUTERWEAR, PANTS);
			default -> Set.of(this);
		};
	}
}
