package org.confluence.terra_character.api.model;

import java.util.Arrays;

/**
 * Immutable 64x64 ARGB edits. Zero restores the default pixel; TRANSPARENT erases an outer-layer
 * pixel.
 */
public final class SkinPaint {
	public static final int TRANSPARENT = 1;
	public static final int SIZE = 64;
	public static final int PIXELS = SIZE * SIZE;
	public static final SkinPaint EMPTY = new SkinPaint(new int[PIXELS]);
	private final int[] pixels;
	private final int hash;

	public SkinPaint(int[] pixels) {
		if (pixels.length != PIXELS)
			throw new IllegalArgumentException("Skin paint must contain 4096 pixels");
		this.pixels = pixels.clone();
		for (int pixel : this.pixels) {
			if (pixel != 0 && pixel != TRANSPARENT && (pixel >>> 24) != 255)
				throw new IllegalArgumentException(
						"Skin paint must be opaque, explicitly transparent or unset");
		}
		hash = Arrays.hashCode(this.pixels);
	}

	public int pixel(int x, int y) {
		if (x < 0 || x >= SIZE || y < 0 || y >= SIZE)
			throw new IndexOutOfBoundsException("Skin pixel outside texture");
		return pixels[y * SIZE + x];
	}

	public int[] pixels() {
		return pixels.clone();
	}

	public boolean isEmpty() {
		return Arrays.equals(pixels, EMPTY.pixels);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof SkinPaint paint && Arrays.equals(pixels, paint.pixels);
	}

	@Override
	public int hashCode() {
		return hash;
	}
}
