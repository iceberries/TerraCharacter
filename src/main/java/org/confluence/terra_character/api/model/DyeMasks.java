package org.confluence.terra_character.api.model;

import org.confluence.terra_character.service.SkinLayout;

import java.util.*;

/**
 * Character-owned overrides in canonical slim UVs; absent means resource defaults, empty means no
 * pixels.
 */
public record DyeMasks(Map<WardrobeSlot, List<Integer>> regions) {
	public static final DyeMasks EMPTY = new DyeMasks(Map.of());

	public DyeMasks {
		var copy = new EnumMap<WardrobeSlot, List<Integer>>(WardrobeSlot.class);
		var used = new BitSet(4096);
		regions.forEach(
				(slot, pixels) -> {
					if (pixels.size() > 4096)
						throw new IllegalArgumentException("Dye mask is too large");
					if (slot == WardrobeSlot.OUTFIT || slot == WardrobeSlot.OVERALLS)
						throw new IllegalArgumentException("Use individual dye regions");
					var sorted = new TreeSet<Integer>();
					for (int pixel : pixels) {
						if (pixel < 0
								|| pixel >= 4096
								|| used.get(pixel)
								|| (!SkinLayout.contains(
												SkinLayout.Layer.BASE, true, pixel % 64, pixel / 64)
										&& !SkinLayout.contains(
												SkinLayout.Layer.OUTER,
												true,
												pixel % 64,
												pixel / 64)))
							throw new IllegalArgumentException("Invalid or overlapping dye mask");
						sorted.add(pixel);
					}
					sorted.forEach(used::set);
					copy.put(slot, List.copyOf(sorted));
				});
		regions = Map.copyOf(copy);
	}

	/** Reassign newly selected pixels from other custom regions. */
	public DyeMasks with(WardrobeSlot slot, Collection<Integer> pixels) {
		var next = new EnumMap<WardrobeSlot, List<Integer>>(WardrobeSlot.class);
		next.putAll(regions);
		var selected = new HashSet<>(pixels);
		next.replaceAll(
				(other, values) ->
						other == slot
								? values
								: values.stream().filter(p -> !selected.contains(p)).toList());
		next.put(slot, List.copyOf(selected));
		return new DyeMasks(next);
	}

	public DyeMasks reset(WardrobeSlot slot) {
		var next = new EnumMap<WardrobeSlot, List<Integer>>(WardrobeSlot.class);
		next.putAll(regions);
		next.remove(slot);
		return new DyeMasks(next);
	}
}
