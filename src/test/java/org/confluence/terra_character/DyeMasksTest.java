package org.confluence.terra_character;

import static org.junit.jupiter.api.Assertions.*;

import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

class DyeMasksTest {
	@TempDir Path root;

	@Test
	void assignmentTransfersPixelsAndDistinguishesEmptyFromDefault() {
		var a = DyeMasks.EMPTY.with(WardrobeSlot.HAIR, List.of(520, 521));
		var b = a.with(WardrobeSlot.EYES, List.of(521, 522));
		assertEquals(List.of(520), b.regions().get(WardrobeSlot.HAIR));
		assertEquals(List.of(521, 522), b.regions().get(WardrobeSlot.EYES));
		var empty = b.with(WardrobeSlot.EYES, List.of());
		assertTrue(empty.regions().containsKey(WardrobeSlot.EYES));
		assertFalse(empty.reset(WardrobeSlot.EYES).regions().containsKey(WardrobeSlot.EYES));
		assertEquals(List.of(520, 521), a.regions().get(WardrobeSlot.HAIR));
	}

	@Test
	void invalidUnusedOverlappingAndCombinedRegionsAreRejected() {
		assertThrows(
				IllegalArgumentException.class,
				() -> DyeMasks.EMPTY.with(WardrobeSlot.EYES, List.of(4096)));
		assertThrows(
				IllegalArgumentException.class,
				() -> DyeMasks.EMPTY.with(WardrobeSlot.EYES, List.of(0)));
		assertThrows(
				IllegalArgumentException.class,
				() ->
						new DyeMasks(
								Map.of(
										WardrobeSlot.EYES,
										List.of(520),
										WardrobeSlot.HAIR,
										List.of(520))));
		assertThrows(
				IllegalArgumentException.class,
				() -> DyeMasks.EMPTY.with(WardrobeSlot.OUTFIT, List.of(520)));
	}

	@Test
	void customBaseAndOuterPixelsSurviveEditsReloadAndArchive() throws Exception {
		var masks = DyeMasks.EMPTY.with(WardrobeSlot.EYES, List.of(520, 552));
		var appearance =
				new CharacterAppearance(
								"terra_character:slim", "terra_character:fire_berry", Map.of())
						.withDyeMasks(masks);
		assertEquals(
				masks,
				appearance.withPaint(SkinPaint.EMPTY).withWardrobe(Wardrobe.EMPTY).dyeMasks());
		var p =
				new CharacterProfile(
						UUID.randomUUID(),
						"Regions",
						CharacterDifficulty.SOFTCORE,
						appearance,
						CharacterProfile.Status.ACTIVE);
		var store = new CharacterStore(root.resolve("characters"));
		store.create(p);
		store.edit(p);
		store.archive(p.id());
		store.unarchive(p.id());
		assertEquals(p, new CharacterStore(store.root()).find(p.id()).orElseThrow());
		assertEquals(p, RemoteCharacters.metadata(ProfileCodec.character(p)));
		var old = ProfileCodec.character(p);
		old.getCompound("Appearance").remove("DyeMasks");
		assertEquals(DyeMasks.EMPTY, ProfileCodec.character(old).appearance().dyeMasks());
		old.getCompound("Appearance").putString("DyeMasks", "invalid");
		assertThrows(IOException.class, () -> ProfileCodec.character(old));
	}
}
