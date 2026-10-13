package org.confluence.terra_character.service;

import org.confluence.terra_character.api.model.SkinPaint;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Draft edits with one undo entry per stroke. Brush clipping never touches an adjacent UV
 * face/layer.
 */
public final class SkinCanvas {
	private int[] pixels;
	private int[] stroke;
	private final Deque<int[]> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

	public SkinCanvas(SkinPaint paint) {
		pixels = paint.pixels();
	}

	public SkinPaint snapshot() {
		return new SkinPaint(pixels);
	}

	public int pixel(int x, int y) {
		return pixels[y * SkinPaint.SIZE + x];
	}

	public boolean canUndo() {
		return !undo.isEmpty();
	}

	public boolean canRedo() {
		return !redo.isEmpty();
	}

	public void beginStroke() {
		endStroke();
		stroke = pixels.clone();
	}

	public void endStroke() {
		if (stroke == null) return;
		if (!Arrays.equals(stroke, pixels)) {
			undo.addLast(stroke);
			if (undo.size() > 32) undo.removeFirst();
			redo.clear();
		}
		stroke = null;
	}

	public void undo() {
		endStroke();
		if (!undo.isEmpty()) {
			redo.addLast(pixels);
			pixels = undo.removeLast();
		}
	}

	public void redo() {
		endStroke();
		if (!redo.isEmpty()) {
			undo.addLast(pixels);
			pixels = redo.removeLast();
		}
	}

	/**
	 * Coordinates are relative to the selected face; lines remain bounded even when the mouse
	 * leaves it.
	 */
	public void line(SkinLayout.Region region, int x0, int y0, int x1, int y1, int size, int argb) {
		if (size < 1
				|| size > 3
				|| (argb != 0 && argb != SkinPaint.TRANSPARENT && (argb >>> 24) != 255))
			throw new IllegalArgumentException("Invalid skin brush");
		if (x0 < 0
				|| x0 >= region.width()
				|| x1 < 0
				|| x1 >= region.width()
				|| y0 < 0
				|| y0 >= region.height()
				|| y1 < 0
				|| y1 >= region.height()) return;
		int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		for (int i = 0; i <= steps; i++) {
			int x = steps == 0 ? x0 : Math.round(x0 + (x1 - x0) * i / (float) steps);
			int y = steps == 0 ? y0 : Math.round(y0 + (y1 - y0) * i / (float) steps);
			for (int dy = 0; dy < size; dy++)
				for (int dx = 0; dx < size; dx++) {
					int px = region.x() + x + dx - size / 2, py = region.y() + y + dy - size / 2;
					if (region.contains(px, py)) pixels[py * SkinPaint.SIZE + px] = argb;
				}
		}
	}

	public void reset(SkinLayout.Region region) {
		beginStroke();
		for (int y = region.y(); y < region.y() + region.height(); y++) {
			Arrays.fill(
					pixels,
					y * SkinPaint.SIZE + region.x(),
					y * SkinPaint.SIZE + region.x() + region.width(),
					0);
		}
		endStroke();
	}
}
