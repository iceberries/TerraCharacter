package org.confluence.terra_character;

import static org.junit.jupiter.api.Assertions.*;

import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.service.SkinCanvas;
import org.confluence.terra_character.service.SkinLayout;
import org.confluence.terra_character.service.SkinLayout.*;
import org.confluence.terra_character.storage.CharacterStore;
import org.confluence.terra_character.storage.ProfileCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

class SkinPaintingTest {
	@TempDir Path root;

	private CharacterProfile profile(SkinPaint paint) {
		return new CharacterProfile(
				UUID.randomUUID(),
				"Painter",
				CharacterDifficulty.SOFTCORE,
				new CharacterAppearance(
						"terra_character:wide", "terra_character:short", Map.of(), paint),
				CharacterProfile.Status.ACTIVE);
	}

	@Test
	void textureOwnsItsPixelsAndUsesContentEqualityForSkinCache() {
		int[] pixels = new int[4096];
		pixels[8 * 64 + 8] = 0xFF123456;
		var paint = new SkinPaint(pixels);
		var equal = new SkinPaint(pixels);
		pixels[8 * 64 + 8] = 0;
		paint.pixels()[8 * 64 + 8] = 0;
		assertEquals(0xFF123456, paint.pixel(8, 8));
		assertEquals(equal, paint);
		assertEquals(equal.hashCode(), paint.hashCode());
		assertFalse(paint.isEmpty());
		assertTrue(SkinPaint.EMPTY.isEmpty());
		assertThrows(IllegalArgumentException.class, () -> new SkinPaint(new int[4095]));
		pixels[0] = 0x77123456;
		assertThrows(IllegalArgumentException.class, () -> new SkinPaint(pixels));
	}

	@Test
	void allFacesAndLayersAreDisjointAndMatchModernSkinPixelCounts() {
		for (boolean slim : new boolean[] {false, true}) {
			boolean[] visited = new boolean[4096];
			int bases = 0, outers = 0;
			for (Layer layer : Layer.values())
				for (Part part : Part.values())
					for (Face face : Face.values()) {
						var r = SkinLayout.region(part, face, layer, slim);
						for (int y = r.y(); y < r.y() + r.height(); y++)
							for (int x = r.x(); x < r.x() + r.width(); x++) {
								assertTrue(x >= 0 && x < 64 && y >= 0 && y < 64);
								assertFalse(visited[y * 64 + x], part + "/" + face + "/" + layer);
								visited[y * 64 + x] = true;
								if (layer == Layer.BASE) bases++;
								else outers++;
							}
					}
			assertEquals(slim ? 1568 : 1632, bases);
			assertEquals(bases, outers);
		}
		assertEquals(
				new Region(36, 52, 3, 12),
				SkinLayout.region(Part.LEFT_ARM, Face.FRONT, Layer.BASE, true));
		assertEquals(
				new Region(52, 52, 3, 12),
				SkinLayout.region(Part.LEFT_ARM, Face.FRONT, Layer.OUTER, true));
		assertEquals(
				new Region(40, 8, 8, 8),
				SkinLayout.region(Part.HEAD, Face.FRONT, Layer.OUTER, false));
	}

	@Test
	void wideBrushAndInterpolatedStrokeStayInsideSelectedSlimOuterFace() {
		var canvas = new SkinCanvas(SkinPaint.EMPTY);
		var r = SkinLayout.region(Part.LEFT_ARM, Face.FRONT, Layer.OUTER, true);
		canvas.beginStroke();
		canvas.line(r, 0, 0, 2, 11, 3, 0xFF55AA99);
		canvas.endStroke();
		for (int y = 0; y < 64; y++)
			for (int x = 0; x < 64; x++) {
				if (!r.contains(x, y)) assertEquals(0, canvas.pixel(x, y));
			}
		for (int y = 0; y < 12; y++)
			assertTrue(
					canvas.pixel(r.x(), r.y() + y) != 0
							|| canvas.pixel(r.x() + 1, r.y() + y) != 0
							|| canvas.pixel(r.x() + 2, r.y() + y) != 0);
		canvas.undo();
		assertTrue(canvas.snapshot().isEmpty());
		canvas.redo();
		assertFalse(canvas.snapshot().isEmpty());
		canvas.beginStroke();
		canvas.line(r, -100, -100, 100, 100, 1, 0xFFFFFFFF);
		canvas.endStroke();
		assertTrue(canvas.canUndo());
		canvas.undo();
		assertTrue(canvas.snapshot().isEmpty());
	}

	@Test
	void outerEraseAndResetDoNotRemoveBasePaintAndOneStrokeIsOneUndo() {
		var canvas = new SkinCanvas(SkinPaint.EMPTY);
		var base = SkinLayout.region(Part.HEAD, Face.FRONT, Layer.BASE, false);
		var outer = SkinLayout.region(Part.HEAD, Face.FRONT, Layer.OUTER, false);
		canvas.beginStroke();
		canvas.line(base, 0, 0, 7, 7, 1, 0xFF102030);
		canvas.endStroke();
		canvas.beginStroke();
		canvas.line(outer, 0, 0, 7, 7, 1, 0xFFAABBCC);
		canvas.line(outer, 7, 0, 0, 7, 1, 0xFFAABBCC);
		canvas.endStroke();
		var both = canvas.snapshot();
		canvas.undo();
		assertEquals(0, canvas.pixel(40, 8));
		assertEquals(0xFF102030, canvas.pixel(8, 8));
		canvas.redo();
		assertEquals(both, canvas.snapshot());
		canvas.beginStroke();
		canvas.line(outer, 0, 0, 0, 0, 1, 0);
		canvas.endStroke();
		assertEquals(0, canvas.pixel(40, 8));
		assertEquals(0xFF102030, canvas.pixel(8, 8));
		canvas.reset(outer);
		assertEquals(0, canvas.pixel(47, 15));
		assertEquals(0xFF102030, canvas.pixel(15, 15));
		canvas.undo();
		assertEquals(0xFFAABBCC, canvas.pixel(47, 15));
		canvas.beginStroke();
		canvas.line(base, 1, 0, 1, 0, 1, 0xFFFFFFFF);
		canvas.endStroke();
		assertFalse(canvas.canRedo());
	}

	@Test
	void paintedAppearancePersistsThroughAtomicEditReloadAndArchive() throws Exception {
		int[] pixels = new int[4096];
		pixels[8 * 64 + 8] = 0xFF123456;
		pixels[8 * 64 + 40] = 0xFFABCDEF;
		var original = profile(SkinPaint.EMPTY);
		var painted =
				new CharacterProfile(
						original.id(),
						original.name(),
						original.difficulty(),
						new CharacterAppearance(
								"terra_character:wide",
								"terra_character:short",
								Map.of(),
								new SkinPaint(pixels)),
						original.status());
		var store = new CharacterStore(root.resolve("characters"));
		store.create(original);
		store.edit(painted);
		var reload = new CharacterStore(store.root());
		assertEquals(painted, reload.find(original.id()).orElseThrow());
		reload.archive(original.id());
		reload.unarchive(original.id());
		assertEquals(painted, reload.find(original.id()).orElseThrow());
		assertEquals(painted, ProfileCodec.character(ProfileCodec.character(painted)));
	}

	@Test
	void explicitOuterTransparencySurvivesSaveUndoResetAndReload() throws Exception {
		var canvas = new SkinCanvas(SkinPaint.EMPTY);
		var outer = SkinLayout.region(Part.HEAD, Face.FRONT, Layer.OUTER, true);
		canvas.beginStroke();
		canvas.line(outer, 0, 0, 0, 0, 1, SkinPaint.TRANSPARENT);
		canvas.endStroke();
		assertEquals(SkinPaint.TRANSPARENT, canvas.pixel(40, 8));
		canvas.undo();
		assertTrue(canvas.snapshot().isEmpty());
		canvas.redo();
		var p = profile(canvas.snapshot());
		var store = new CharacterStore(root);
		store.create(p);
		assertEquals(
				SkinPaint.TRANSPARENT,
				store.find(p.id()).orElseThrow().appearance().paint().pixel(40, 8));
		canvas.reset(outer);
		assertTrue(canvas.snapshot().isEmpty());
	}

	@Test
	void oldProfilesLoadWithoutPaintAndMalformedPaintIsRejected() throws Exception {
		var profile = profile(SkinPaint.EMPTY);
		var tag = ProfileCodec.character(profile);
		assertFalse(tag.getCompound("Appearance").contains("Paint"));
		assertEquals(profile, ProfileCodec.character(tag));
		tag.getCompound("Appearance").putIntArray("Paint", new int[4097]);
		assertThrows(IOException.class, () -> ProfileCodec.character(tag));
		tag.getCompound("Appearance").putString("Paint", "bad");
		assertThrows(IOException.class, () -> ProfileCodec.character(tag));
		int[] pixels = new int[4096];
		pixels[0] = 0x33123456;
		tag.getCompound("Appearance").putIntArray("Paint", pixels);
		assertThrows(IOException.class, () -> ProfileCodec.character(tag));
	}
}
