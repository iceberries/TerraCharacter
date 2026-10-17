package org.confluence.terra_character;

import static org.junit.jupiter.api.Assertions.*;

import org.confluence.terra_character.api.model.SkinPaint;
import org.confluence.terra_character.api.model.WardrobeSlot;
import org.confluence.terra_character.service.SelectedPart;
import org.confluence.terra_character.service.SkinCanvas;
import org.confluence.terra_character.service.SkinLayout.Region;
import org.confluence.terra_character.service.SkinSelection;
import org.confluence.terra_character.service.WardrobeDefinitions;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

class SkinSelectionTest {
	@Test
	void invalidCaptureBoundsAreRejectedBeforeAllocatingOrReadingPixels() {
		assertThrows(
				IllegalArgumentException.class,
				() ->
						SkinSelection.capture(
								new Region(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE),
								(x, y) -> {
									throw new AssertionError("Must not read invalid selection");
								}));
		assertThrows(
				IllegalArgumentException.class,
				() -> SkinSelection.capture(new Region(63, 0, 2, 1), (x, y) -> 0));
		assertThrows(
				NullPointerException.class,
				() -> SkinSelection.capture(new Region(0, 0, 1, 1), null));
	}

	@Test
	void quarterTurnsPreserveEveryPixelAndDimensions() {
		var selection = new SkinSelection(new Region(8, 8, 2, 3), new int[] {1, 2, 3, 4, 5, 6});
		var right = selection.rotate(1);
		assertEquals(new Region(8, 8, 3, 2), right.bounds());
		assertEquals(5, right.pixel(0, 0));
		assertEquals(1, right.pixel(2, 0));
		assertEquals(6, right.pixel(0, 1));
		var left = selection.rotate(-1);
		assertEquals(2, left.pixel(0, 0));
		assertEquals(5, left.pixel(2, 1));
		var full = right.rotate(1).rotate(1).rotate(1);
		assertEquals(selection.bounds(), full.bounds());
		for (int y = 0; y < 3; y++)
			for (int x = 0; x < 2; x++) assertEquals(selection.pixel(x, y), full.pixel(x, y));
	}

	@Test
	void overlappingCutMoveUsesSnapshotAndIsOneUndoStep() {
		var canvas = new SkinCanvas(SkinPaint.EMPTY);
		var face = new Region(40, 8, 8, 8);
		var selection =
				new SkinSelection(new Region(40, 8, 2, 1), new int[] {0xFF123456, 0xFFABCDEF});
		selection.replace(canvas, face, selection.at(41, 8), true);
		assertEquals(SkinPaint.TRANSPARENT, canvas.pixel(40, 8));
		assertEquals(0xFF123456, canvas.pixel(41, 8));
		assertEquals(0xFFABCDEF, canvas.pixel(42, 8));
		assertEquals(0, canvas.pixel(8, 8));
		canvas.undo();
		assertTrue(canvas.snapshot().isEmpty());
		assertFalse(canvas.canUndo());
		canvas.redo();
		assertEquals(0xFFABCDEF, canvas.pixel(42, 8));
	}

	@Test
	void invalidRotationDoesNotChangeCanvasOrHistory() {
		var canvas = new SkinCanvas(SkinPaint.EMPTY);
		var face = new Region(8, 8, 8, 8);
		var selection = new SkinSelection(new Region(14, 8, 2, 3), new int[6]);
		assertThrows(
				IllegalArgumentException.class,
				() -> selection.replace(canvas, face, selection.rotate(1), false));
		assertTrue(canvas.snapshot().isEmpty());
		assertFalse(canvas.canUndo());
	}

	@Test
	void baseCutRestoresSourceAndOuterTransparencyRemainsExplicit() {
		var canvas = new SkinCanvas(SkinPaint.EMPTY);
		var face = new Region(8, 8, 8, 8);
		canvas.beginStroke();
		canvas.line(face, 0, 0, 0, 0, 1, 0xFF123456);
		canvas.endStroke();
		var selection = new SkinSelection(new Region(8, 8, 1, 1), new int[] {0xFF123456});
		selection.replace(canvas, face, selection.at(9, 8), false);
		assertEquals(0, canvas.pixel(8, 8));
		assertEquals(0xFF123456, canvas.pixel(9, 8));
		canvas.undo();
		assertEquals(0xFF123456, canvas.pixel(8, 8));
		assertEquals(0, canvas.pixel(9, 8));
		var outer = new Region(40, 8, 8, 8);
		var empty = new SkinSelection(new Region(40, 8, 1, 1), new int[] {0});
		empty.replace(canvas, outer, empty.at(41, 8), true);
		assertEquals(SkinPaint.TRANSPARENT, canvas.pixel(41, 8));
	}

	@Test
	void exportedPartRetainsSlotBodyUVAndAlphaAndRejectsCrossSlotSelection() {
		var selection = new SkinSelection(new Region(10, 12, 2, 1), new int[] {0xFF112233, 0});
		var allowed = new BitSet(4096);
		allowed.set(12 * 64 + 10);
		assertFalse(SelectedPart.belongsTo(selection, allowed));
		assertThrows(
				IllegalArgumentException.class,
				() -> SelectedPart.json("Eyes", WardrobeSlot.EYES, selection, allowed, true));
		allowed.set(12 * 64 + 11);
		var json = SelectedPart.json("Eyes", WardrobeSlot.EYES, selection, allowed, true);
		var part = WardrobeDefinitions.part("example:eyes", json);
		assertEquals(WardrobeSlot.EYES, part.slot());
		var fragment = part.fragments().getFirst();
		assertEquals(10, fragment.x());
		assertEquals(12, fragment.y());
		assertEquals(2, fragment.width());
		assertEquals("slim", fragment.body());
		assertEquals(0, fragment.pixels().get(1));
		assertTrue(fragment.replace());
		assertThrows(
				IllegalArgumentException.class,
				() -> SelectedPart.json(" ", WardrobeSlot.EYES, selection, allowed, true));
	}

	@Test
	void selectionOwnsItsSnapshotAndHalfTurnsDoNotRequireIntermediateSpace() {
		int[] pixels = {1, 2, 3, 4, 5, 6};
		var selection = new SkinSelection(new Region(62, 8, 2, 3), pixels);
		pixels[0] = 99;
		assertEquals(1, selection.pixel(0, 0));
		assertEquals(6, selection.rotate(2).pixel(0, 0));
		assertThrows(IllegalArgumentException.class, () -> selection.rotate(1));
	}
}
