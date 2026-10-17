package org.confluence.terra_character.service;

import org.confluence.terra_character.api.model.SkinPaint;
import org.confluence.terra_character.service.SkinLayout.Region;

import java.util.Objects;
import java.util.function.IntBinaryOperator;

/** Immutable, face-local selection. Rotation never resamples pixels or clips another UV face. */
public final class SkinSelection {
	private final Region bounds;
	private final int[] pixels;

	public SkinSelection(Region bounds, int[] pixels) {
		validateBounds(bounds);
		Objects.requireNonNull(pixels, "pixels");
		if (pixels.length != bounds.width() * bounds.height())
			throw new IllegalArgumentException("Wrong selection pixel count");
		this.bounds = bounds;
		this.pixels = pixels.clone();
	}

	private static void validateBounds(Region bounds) {
		Objects.requireNonNull(bounds, "bounds");
		if (bounds.width() < 1
				|| bounds.height() < 1
				|| bounds.width() > 64
				|| bounds.height() > 64
				|| bounds.x() < 0
				|| bounds.y() < 0
				|| bounds.x() > 64 - bounds.width()
				|| bounds.y() > 64 - bounds.height())
			throw new IllegalArgumentException("Invalid selection bounds");
	}

	public static SkinSelection capture(Region bounds, IntBinaryOperator pixel) {
		validateBounds(bounds);
		Objects.requireNonNull(pixel, "pixel");
		int[] values = new int[bounds.width() * bounds.height()];
		for (int y = 0; y < bounds.height(); y++)
			for (int x = 0; x < bounds.width(); x++)
				values[y * bounds.width() + x] = pixel.applyAsInt(bounds.x() + x, bounds.y() + y);
		return new SkinSelection(bounds, values);
	}

	public Region bounds() {
		return bounds;
	}

	public int pixel(int x, int y) {
		if (x < 0 || y < 0 || x >= bounds.width() || y >= bounds.height())
			throw new IndexOutOfBoundsException("Pixel outside selection");
		return pixels[y * bounds.width() + x];
	}

	public SkinSelection at(int x, int y) {
		return new SkinSelection(new Region(x, y, bounds.width(), bounds.height()), pixels);
	}

	public SkinSelection rotate(int quarterTurns) {
		int turns = Math.floorMod(quarterTurns, 4);
		if (turns == 0) return this;
		int width = turns % 2 == 0 ? bounds.width() : bounds.height();
		int height = turns % 2 == 0 ? bounds.height() : bounds.width();
		int[] next = new int[pixels.length];
		for (int y = 0; y < bounds.height(); y++)
			for (int x = 0; x < bounds.width(); x++) {
				int tx =
						turns == 1
								? bounds.height() - 1 - y
								: turns == 2 ? bounds.width() - 1 - x : y;
				int ty =
						turns == 1
								? x
								: turns == 2 ? bounds.height() - 1 - y : bounds.width() - 1 - x;
				next[ty * width + tx] = pixel(x, y);
			}
		return new SkinSelection(new Region(bounds.x(), bounds.y(), width, height), next);
	}

	public boolean fits(Region face) {
		return face.contains(bounds.x(), bounds.y())
				&& face.contains(bounds.x() + bounds.width() - 1, bounds.y() + bounds.height() - 1);
	}

	/** Cut uses the editor's existing semantics: restore base pixels, erase outer pixels. */
	public void replace(SkinCanvas canvas, Region face, SkinSelection destination, boolean outer) {
		if (!fits(face) || !destination.fits(face) || pixels.length != destination.pixels.length)
			throw new IllegalArgumentException("Selection outside current face");
		canvas.beginStroke();
		for (int y = 0; y < bounds.height(); y++)
			for (int x = 0; x < bounds.width(); x++)
				canvas.line(
						face,
						bounds.x() + x - face.x(),
						bounds.y() + y - face.y(),
						bounds.x() + x - face.x(),
						bounds.y() + y - face.y(),
						1,
						outer ? SkinPaint.TRANSPARENT : 0);
		Region target = destination.bounds;
		for (int y = 0; y < target.height(); y++)
			for (int x = 0; x < target.width(); x++) {
				int value = destination.pixel(x, y);
				canvas.line(
						face,
						target.x() + x - face.x(),
						target.y() + y - face.y(),
						target.x() + x - face.x(),
						target.y() + y - face.y(),
						1,
						(value >>> 24) == 0
								? (outer ? SkinPaint.TRANSPARENT : 0)
								: value | 0xFF000000);
			}
		canvas.endStroke();
	}
}
