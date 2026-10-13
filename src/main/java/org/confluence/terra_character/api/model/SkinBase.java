package org.confluence.terra_character.api.model;

import java.util.Arrays;

/** Immutable downloaded 64x64 ARGB source, independent of the player's paint edits. */
public final class SkinBase {
	public static final SkinBase EMPTY = new SkinBase(false, new int[0]);
	private final boolean slim;
	private final int[] pixels;
	private final int hash;

	public SkinBase(boolean slim, int[] pixels) {
		if (pixels.length != 0 && pixels.length != 4096)
			throw new IllegalArgumentException("Base skin must contain 4096 pixels");
		this.slim = pixels.length != 0 && slim;
		this.pixels = pixels.clone();
		hash = 31 * Boolean.hashCode(this.slim) + Arrays.hashCode(this.pixels);
	}

	public boolean slim() {
		return slim;
	}

	public boolean isEmpty() {
		return pixels.length == 0;
	}

	public int pixel(int x, int y) {
		if (x < 0 || x >= 64 || y < 0 || y >= 64 || isEmpty())
			throw new IndexOutOfBoundsException("Missing base skin pixel");
		return pixels[y * 64 + x];
	}

	public int[] pixels() {
		return pixels.clone();
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object other) {
		return this == other
				|| other instanceof SkinBase skin
						&& slim == skin.slim
						&& Arrays.equals(pixels, skin.pixels);
	}
}
