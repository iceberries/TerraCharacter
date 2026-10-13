package org.confluence.terra_character;

import static org.junit.jupiter.api.Assertions.*;

import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.service.SkinBasePixels;
import org.confluence.terra_character.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

class SkinBaseTest {
	@TempDir Path directory;

	@Test
	void modernSkinKeepsOuterAlphaAndOwnsItsPixels() {
		int[] pixels = new int[4096];
		pixels[8 * 64 + 8] = 0x00123456;
		pixels[8 * 64 + 40] = 0x80224466;
		var skin = SkinBasePixels.normalize(pixels, 64, 64, true);
		assertEquals(0xFF123456, skin.pixel(8, 8));
		assertEquals(0x80224466, skin.pixel(40, 8));
		pixels[8 * 64 + 8] = 0;
		var copy = skin.pixels();
		copy[8 * 64 + 8] = 0;
		assertEquals(0xFF123456, skin.pixel(8, 8));
		assertThrows(IllegalArgumentException.class, () -> new SkinBase(false, new int[4095]));
		assertEquals(SkinBase.EMPTY, new SkinBase(true, new int[0]));
		assertThrows(
				IllegalArgumentException.class,
				() -> SkinBasePixels.normalize(new int[4096], 128, 32, false));
	}

	@Test
	void legacySkinMirrorsLeftLimbsAndClearsOpaqueHat() {
		int[] pixels = new int[2048];
		Arrays.fill(pixels, 0xFF102030);
		for (int x = 4; x < 8; x++) pixels[20 * 64 + x] = 0xFF000000 | x;
		var skin = SkinBasePixels.normalize(pixels, 64, 32, true);
		assertFalse(skin.slim());
		assertEquals(0xFF000007, skin.pixel(20, 52));
		assertEquals(0xFF000004, skin.pixel(23, 52));
		assertEquals(0, skin.pixel(40, 8) >>> 24);
		assertEquals(0, skin.pixel(4, 36) >>> 24);
	}

	@Test
	void accountPixelsRoundTripThroughStoreAndMultiplayerMetadataWithoutReplacingPaint()
			throws Exception {
		int[] pixels = new int[4096];
		pixels[8 * 64 + 40] = 0x40224466;
		var base = SkinBasePixels.normalize(pixels, 64, 64, false);
		int[] edits = new int[4096];
		edits[8 * 64 + 40] = SkinPaint.TRANSPARENT;
		var appearance =
				new CharacterAppearance(
						"terra_character:wide",
						"terra_character:account",
						Map.of(),
						new SkinPaint(edits),
						Wardrobe.EMPTY,
						DyeMasks.EMPTY,
						base);
		var person =
				new CharacterProfile(
						UUID.randomUUID(),
						"A character name",
						CharacterDifficulty.SOFTCORE,
						appearance,
						CharacterProfile.Status.ACTIVE);
		var encoded = ProfileCodec.character(person);
		assertEquals(person, RemoteCharacters.metadata(encoded));
		var store = new CharacterStore(directory);
		store.create(person);
		assertEquals(person, store.find(person.id()).orElseThrow());
		assertEquals(base, appearance.withPaint(SkinPaint.EMPTY).baseSkin());
		assertEquals(
				base,
				appearance.withWardrobe(Wardrobe.EMPTY).withDyeMasks(DyeMasks.EMPTY).baseSkin());
		assertEquals(
				0x40224466, ProfileCodec.character(encoded).appearance().baseSkin().pixel(40, 8));
		encoded.getCompound("Appearance").getCompound("BaseSkin").putIntArray("Pixels", new int[2]);
		assertThrows(IOException.class, () -> ProfileCodec.character(encoded));
	}

	@Test
	void oldProfilesStillLoadWithoutADownloadedBase() throws Exception {
		var old =
				new CharacterProfile(
						UUID.randomUUID(),
						"Old",
						CharacterDifficulty.SOFTCORE,
						new CharacterAppearance(
								"terra_character:slim", "terra_character:fire_berry", Map.of()),
						CharacterProfile.Status.ACTIVE);
		var tag = ProfileCodec.character(old);
		assertFalse(tag.getCompound("Appearance").contains("BaseSkin"));
		assertEquals(old, ProfileCodec.character(tag));
	}
}
