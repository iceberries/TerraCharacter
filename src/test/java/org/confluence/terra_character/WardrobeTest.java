package org.confluence.terra_character;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.service.*;
import org.confluence.terra_character.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

class WardrobeTest {
	@TempDir Path root;

	@Test
	void outfitsReplaceOnlyTheirOccupiedSlots() {
		var parts =
				Wardrobe.EMPTY
						.equip(WardrobeSlot.SOCKS, "test:socks")
						.equip(WardrobeSlot.GLOVES, "test:gloves")
						.equip(WardrobeSlot.SHOES, "test:shoes")
						.equip(WardrobeSlot.PANTS, "test:pants")
						.equip(WardrobeSlot.OUTERWEAR, "test:coat");
		var overalls = parts.equip(WardrobeSlot.OVERALLS, "test:overalls");
		assertEquals(
				Set.of(
						WardrobeSlot.OVERALLS,
						WardrobeSlot.SOCKS,
						WardrobeSlot.GLOVES,
						WardrobeSlot.SHOES),
				overalls.parts().keySet());
		var outfit = overalls.equip(WardrobeSlot.OUTFIT, "test:outfit");
		assertEquals(
				Set.of(WardrobeSlot.OUTFIT, WardrobeSlot.SOCKS, WardrobeSlot.GLOVES),
				outfit.parts().keySet());
		var separate = outfit.equip(WardrobeSlot.PANTS, "test:pants");
		assertFalse(separate.parts().containsKey(WardrobeSlot.OUTFIT));
		assertTrue(parts.parts().containsKey(WardrobeSlot.OUTERWEAR));
		assertThrows(
				IllegalArgumentException.class,
				() ->
						new Wardrobe(
								Map.of(
										WardrobeSlot.OUTFIT,
										"test:a",
										WardrobeSlot.PANTS,
										"test:b")));
	}

	@Test
	void saveAndNetworkCodecPreservePartsColorsAndPaintWithoutResources() throws Exception {
		int[] pixels = new int[4096];
		pixels[8 * 64 + 40] = SkinPaint.TRANSPARENT;
		var appearance =
				new CharacterAppearance(
						"terra_character:slim",
						"terra_character:fire_berry",
						Map.of("test:iris", 0x123456),
						new SkinPaint(pixels),
						Wardrobe.EMPTY.equip(WardrobeSlot.EYES, "missing:eyes"));
		var profile =
				new CharacterProfile(
						UUID.randomUUID(),
						"Wardrobe",
						CharacterDifficulty.SOFTCORE,
						appearance,
						CharacterProfile.Status.ACTIVE);
		var store = new CharacterStore(root);
		store.create(profile);
		assertEquals(profile, store.find(profile.id()).orElseThrow());
		assertEquals(profile, RemoteCharacters.metadata(ProfileCodec.character(profile)));
		assertEquals(appearance.wardrobe(), appearance.withPaint(SkinPaint.EMPTY).wardrobe());
		var old = ProfileCodec.character(profile);
		old.getCompound("Appearance").remove("Parts");
		assertEquals(Wardrobe.EMPTY, ProfileCodec.character(old).appearance().wardrobe());
		old.getCompound("Appearance").putString("Parts", "bad");
		assertThrows(IOException.class, () -> ProfileCodec.character(old));
	}

	@Test
	void resourceParserSupportsPixelPatchesTextureRegionsAndTintChannels() {
		var part =
				WardrobeDefinitions.part(
						"test:eyes",
						JsonParser.parseString(
										"""
{"slot":"eyes","fragments":[
  {"rect":[10,12,1,1],"pixels":["80FFFFFF"],"tint":"test:iris"},
  {"rect":[40,8,8,8],"texture":"test:textures/face.png","body":"slim","blend":"replace"}]}
""")
								.getAsJsonObject());
		assertEquals(WardrobeSlot.EYES, part.slot());
		assertEquals(0x80FFFFFF, part.fragments().getFirst().pixels().getFirst());
		assertEquals("test:iris", part.fragments().getFirst().tint());
		assertTrue(part.fragments().get(1).replace());
		assertEquals("slim", part.fragments().get(1).body());
	}

	@Test
	void malformedPatchesAndConflictingPresetsAreRejected() {
		for (String fragment :
				List.of(
						"{\"rect\":[63,0,2,1],\"pixels\":[\"FFFFFFFF\",\"FFFFFFFF\"]}",
						"{\"rect\":[8,8,1,1],\"pixels\":[\"FFFFFF\"]}",
						"{\"rect\":[8,8,1,1],\"pixels\":[\"FFFFFFFF\"],\"texture\":\"test:a\"}",
						"{\"rect\":[8,8,1,1],\"pixels\":[\"FFFFFFFF\"],\"body\":\"unknown\"}"))
			assertThrows(
					IllegalArgumentException.class,
					() ->
							WardrobeDefinitions.part(
									"test:bad",
									JsonParser.parseString(
													"{\"slot\":\"hair\",\"fragments\":["
															+ fragment
															+ "]}")
											.getAsJsonObject()));
		assertThrows(
				IllegalArgumentException.class,
				() ->
						WardrobeDefinitions.preset(
								"test:bad",
								JsonParser.parseString(
												"""
												{"parts":{"outfit":"test:one","pants":"test:two"}}
												""")
										.getAsJsonObject()));
	}

	@Test
	void presetCarriesBaseAndOuterPixelRegionsAndRejectsOverlaps() {
		var preset =
				WardrobeDefinitions.preset(
						"test:regions",
						JsonParser.parseString(
										"""
										{"regions":{"eyes":[520,552],"mouth":[907,908]}}
										""")
								.getAsJsonObject());
		assertEquals(
				List.of(520, 552), preset.appearance().dyeMasks().regions().get(WardrobeSlot.EYES));
		assertEquals(
				List.of(907, 908),
				preset.appearance().dyeMasks().regions().get(WardrobeSlot.MOUTH));
		assertThrows(
				IllegalArgumentException.class,
				() ->
						WardrobeDefinitions.preset(
								"test:overlap",
								JsonParser.parseString(
												"""
												{"regions":{"eyes":[520],"mouth":[520]}}
												""")
										.getAsJsonObject()));
	}

	@Test
	void alphaBlendKeepsVanillaBaseOpaqueAndCanClearOuterPixels() {
		assertEquals(0xFF123456, SkinPixelBlend.compose(0xFF123456, 0, true, true));
		assertEquals(0, SkinPixelBlend.compose(0xFF123456, 0, true, false));
		assertEquals(0x80FF0000, SkinPixelBlend.compose(0, 0x80FF0000, false, false));
		assertEquals(0xFF80007F, SkinPixelBlend.compose(0xFF0000FF, 0x80FF0000, false, true));
		assertEquals(0x80123456, SkinPixelBlend.tint(0x80FFFFFF, 0x123456));
		assertEquals(0x80123456, SkinPixelBlend.compose(0xFF000000, 0x80123456, true, false));
		assertEquals(0x80FFFFFF, SkinPixelBlend.compose(0x01FFFFFF, 0x7FFFFFFF, false, false));
	}

	@Test
	void bundledPresetsAreValidAndRequireNoNewArt() throws Exception {
		for (String name : List.of("default", "account")) {
			try (var stream =
					getClass()
							.getResourceAsStream(
									"/assets/terra_character/wardrobe/presets/" + name + ".json")) {
				assertNotNull(stream);
				var preset =
						WardrobeDefinitions.preset(
								"terra_character:" + name,
								JsonParser.parseString(
												new String(
														stream.readAllBytes(),
														StandardCharsets.UTF_8))
										.getAsJsonObject());
				assertTrue(preset.appearance().wardrobe().parts().isEmpty());
				assertTrue(preset.appearance().paint().isEmpty());
				if (name.equals("default"))
					assertEquals(11, preset.appearance().dyeMasks().regions().size());
				else {
					assertEquals("terra_character:account", preset.appearance().hairStyle());
					assertTrue(preset.appearance().dyeMasks().regions().isEmpty());
				}
			}
		}
	}
}
