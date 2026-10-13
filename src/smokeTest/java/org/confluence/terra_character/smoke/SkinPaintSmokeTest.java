package org.confluence.terra_character.smoke;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.client.*;
import org.confluence.terra_character.runtime.CharacterSession;
import org.confluence.terra_character.service.SkinLayout;
import org.confluence.terra_character.service.SkinLayout.*;

import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Development-only UI, texture, persistence and in-world skin regression. Uses isolated game
 * directories.
 */
@EventBusSubscriber(modid = TerraCharacter.MODID, value = Dist.CLIENT)
public final class SkinPaintSmokeTest {
	private static int stage, ticks, delay;
	private static boolean finished;
	private static CharacterProfile person;
	private static SkinPaint expected;
	private static CompletableFuture<Void> check;
	private static final long START = System.currentTimeMillis();

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		if (!Boolean.getBoolean("terra_character.skin_smoke") || finished) return;
		var mc = Minecraft.getInstance();
		ticks++;
		try {
			if (System.currentTimeMillis() - START > 300_000)
				throw new AssertionError("Skin smoke timeout stage " + stage);
			if (stage == 0 && mc.getOverlay() == null && ticks > 60) {
				Files.writeString(
						mc.gameDirectory.toPath().resolve("skin-result.txt"), "RUNNING\n");
				person =
						new CharacterProfile(
								UUID.randomUUID(),
								"Skin Painter",
								CharacterDifficulty.SOFTCORE,
								ClientProfiles.defaults(),
								CharacterProfile.Status.ACTIVE);
				ClientProfiles.store().create(person);
				verifyImages();
				mc.options.renderDistance().set(2);
				mc.options.simulationDistance().set(5);
				mc.getWindow().setWindowed(854, 480);
				mc.options.guiScale().set(2);
				mc.resizeDisplay();
				mc.setScreen(new CharacterSelectionScreen(new TitleScreen()));
				stage = 30;
				delay = 0;
			} else if (stage == 30 && ++delay > 15) {
				require(
						mc.screen instanceof CharacterSelectionScreen,
						"Character list remains open");
				require(
						mc.screen.children().stream()
								.noneMatch(
										w ->
												w instanceof Button b
														&& b.getMessage()
																.getString()
																.equals(
																		ClientProfiles.text("edit")
																				.getString())),
						"Character list has no Edit appearance option");
				shot("character-list-no-edit.png");
				mc.setScreen(new CharacterEditorScreen(new TitleScreen(), person, null));
				stage = 1;
				delay = 0;
			} else if (stage == 1 && ++delay > 15) {
				shot("skin-character-editor.png");
				press("skin.edit");
				advance();
			} else if (stage == 2 && ++delay > 15) {
				require(mc.screen instanceof SkinEditorScreen, "Paint entry opens editor");
				color("E34C55");
				stroke(Part.HEAD, Face.FRONT, false, 0, 0, 7, 7, 0);
				mc.screen.resize(
						mc, mc.screen.width, mc.screen.height); // draft survives widget rebuild
				stage = 20;
				delay = 0;
			} else if (stage == 20 && ++delay > 15) {
				shot("skin-base.png");
				press("skin.layer.base");
				color("1155EE");
				stroke(Part.HEAD, Face.FRONT, false, 0, 0, 7, 7, 0);
				stroke(Part.HEAD, Face.FRONT, false, 0, 0, 0, 0, 1); // erase only outer
				press("skin.undo");
				press("skin.redo");
				press("skin.reset");
				press("skin.undo");
				stage = 3;
				delay = 0;
			} else if (stage == 3 && ++delay > 15) {
				verifyOuterPreview(true);
				shot("skin-outer.png");
				press("skin.outer.show");
				stage = 21;
				delay = 0;
			} else if (stage == 21 && ++delay > 15) {
				verifyOuterPreview(false);
				shot("skin-outer-hidden.png");
				press("skin.outer.hide");
				press("skin.apply");
				require(
						ClientProfiles.store()
								.find(person.id())
								.orElseThrow()
								.appearance()
								.paint()
								.isEmpty(),
						"Apply does not persist before character save");
				press("skin.edit");
				press("skin.reset");
				press("cancel"); // cancel base reset
				press("save");
				person = ClientProfiles.store().find(person.id()).orElseThrow();
				expected = person.appearance().paint();
				require(
						expected.pixel(8, 8) == 0xFFE34C55,
						"Base stroke survives outer edits, rebuild and cancel");
				require(
						expected.pixel(40, 8) == SkinPaint.TRANSPARENT,
						"Outer erase remains explicitly transparent");
				require(
						expected.pixel(47, 15) == 0xFF1155EE,
						"Outer stroke survives undo/redo/reset/cancel");
				mc.setScreen(new CharacterEditorScreen(new TitleScreen(), person, null));
				press("body.slim");
				press("body.wide");
				press("skin.edit");
				press("skin.part.head");
				press("skin.part.body"); // slim right arm
				press("skin.size", 1);
				press("skin.size", 2); // size 3
				color("22AA66");
				stroke(Part.RIGHT_ARM, Face.FRONT, true, 0, 0, 2, 11, 0);
				stage = 4;
				delay = 0;
			} else if (stage == 4 && ++delay > 15) {
				shot("skin-slim-arm.png");
				mc.getWindow().setWindowed(960, 720);
				mc.options.guiScale().set(3);
				mc.resizeDisplay();
				stage = 22;
				delay = 0;
			} else if (stage == 22 && ++delay > 15) {
				require(
						mc.screen.width == 320 && mc.screen.height == 240,
						"Minimum 320x240 GUI layout");
				for (var widget : mc.screen.children())
					if (widget instanceof AbstractWidget w) {
						require(
								w.getX() >= 0
										&& w.getX() + w.getWidth() <= mc.screen.width
										&& w.getY() >= 0
										&& w.getY() + w.getHeight() <= mc.screen.height,
								"Narrow layout widget bounds");
					}
				color("FFFFFF");
				stroke(Part.RIGHT_ARM, Face.FRONT, true, 0, 0, 0, 0, 0);
				press("skin.undo");
				shot("skin-narrow.png");
				press("skin.apply");
				press("save");
				person = ClientProfiles.store().find(person.id()).orElseThrow();
				expected = person.appearance().paint();
				require(person.appearance().bodyStyle().endsWith(":slim"), "Slim body saved");
				require(
						expected.pixel(44, 20) == 0xFF22AA66
								&& expected.pixel(46, 31) == 0xFF22AA66,
						"Slim arm face painted");
				require(
						expected.pixel(43, 20) == 0 && expected.pixel(47, 20) == 0,
						"Brush did not bleed onto arm sides");
				require(
						expected.pixel(8, 8) == 0xFFE34C55 && expected.pixel(47, 15) == 0xFF1155EE,
						"Other parts/layers preserved");
				var path =
						ClientProfiles.saves()
								.resolve("skin-" + UUID.randomUUID().toString().substring(0, 8));
				var world =
						new WorldProfile(
								UUID.randomUUID(),
								"Skin test",
								new WorldSettings("42", "minecraft:flat", WorldDifficulty.NORMAL));
				ClientProfiles.setSelected(person.id());
				CharacterSession.arm(ClientProfiles.store(), person.id(), path, world);
				var rules = new GameRules();
				rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
				var settings =
						new LevelSettings(
								world.name(),
								GameType.SURVIVAL,
								false,
								Difficulty.NORMAL,
								false,
								rules,
								WorldDataConfiguration.DEFAULT);
				mc.createWorldOpenFlows()
						.createFreshLevel(
								path.getFileName().toString(),
								settings,
								new WorldOptions(42, false, false),
								r ->
										r.registryOrThrow(Registries.WORLD_PRESET)
												.getHolderOrThrow(WorldPresets.FLAT)
												.value()
												.createWorldDimensions(),
								new TitleScreen());
				stage = 5;
				delay = 0;
			} else if (stage == 5
					&& mc.player != null
					&& ClientState.profile() != null
					&& ++delay > 40) {
				require(
						ClientState.profile().appearance().paint().equals(expected),
						"Paint survives server state payload");
				require(
						mc.player.getSkin().equals(CharacterSkins.skin(person.appearance())),
						"Real player renderer uses painted skin");
				var server = mc.getSingleplayerServer();
				check =
						CompletableFuture.runAsync(
								() -> {
									var p = server.getPlayerList().getPlayers().getFirst();
									var session = CharacterSession.of(p);
									require(
											session != null
													&& !session.failed()
													&& session.profile()
															.appearance()
															.paint()
															.equals(expected),
											"Server loads painted profile");
									try {
										session.save(p);
									} catch (Exception ex) {
										throw new RuntimeException(ex);
									}
								},
								server);
				mc.setScreen(new InventoryScreen(mc.player));
				advance();
			} else if (stage == 6 && check.isDone() && ++delay > 20) {
				check.join();
				shot("skin-in-game.png");
				mc.level.disconnect();
				mc.disconnect();
				mc.setScreen(new TitleScreen());
				advance();
			} else if (stage == 7
					&& mc.getSingleplayerServer() == null
					&& CharacterSession.current() == null
					&& ++delay > 15) {
				require(
						ClientProfiles.store()
								.find(person.id())
								.orElseThrow()
								.appearance()
								.paint()
								.equals(expected),
						"World shutdown preserves paint");
				Files.writeString(
						mc.gameDirectory.toPath().resolve("skin-result.txt"),
						"PASS\n"
							+ "bundled fire_berry source pixels, slim model, both layers and outer"
							+ " erasure\n"
							+ "character list edit button removed\n"
							+ "base and outer alpha + RGB + slim UV\n"
							+ "preview outer visible/hidden framebuffer checks\n"
							+ "brush drag, clipping, erase, undo, redo, reset\n"
							+ "resize draft, 320x240 GUI, apply vs save, cancel\n"
							+ "atomic profile reload, slim body\n"
							+ "server payload, actual player skin, world save\n");
				TerraCharacter.LOGGER.info("SKIN SMOKE ALL PASSED");
				finished = true;
				mc.stop();
			}
		} catch (Throwable ex) {
			finished = true;
			TerraCharacter.LOGGER.error("SKIN SMOKE FAILED stage " + stage, ex);
			try {
				Files.writeString(
						mc.gameDirectory.toPath().resolve("skin-result.txt"),
						"FAIL stage " + stage + "\n" + ex);
			} catch (Exception ignored) {
			}
			if (mc.level != null) {
				mc.level.disconnect();
				mc.disconnect();
			}
			mc.stop();
		}
	}

	private static void advance() {
		stage++;
		delay = 0;
	}

	private static void require(boolean test, String message) {
		if (!test) throw new AssertionError(message);
	}

	private static void press(String key, Object... args) {
		var text = ClientProfiles.text(key, args).getString();
		var button =
				Minecraft.getInstance().screen.children().stream()
						.filter(
								w ->
										w instanceof Button b
												&& b.getMessage().getString().equals(text))
						.findFirst()
						.orElseThrow(() -> new AssertionError("Button missing: " + text));
		require(((Button) button).active, "Button active: " + text);
		((Button) button).onPress();
	}

	private static void color(String rgb) {
		Minecraft.getInstance().screen.children().stream()
				.filter(EditBox.class::isInstance)
				.map(EditBox.class::cast)
				.findFirst()
				.orElseThrow()
				.setValue(rgb);
	}

	private static void stroke(
			Part part, Face face, boolean slim, int x0, int y0, int x1, int y1, int button) {
		var screen = Minecraft.getInstance().screen;
		var r = SkinLayout.region(part, face, Layer.BASE, slim);
		boolean wide = screen.width >= 416;
		int cw = wide ? 156 : 110;
		int zoom =
				Math.max(
						1,
						Math.min(
								(cw - 12) / r.width(),
								Math.max(12, screen.height - 112) / r.height()));
		int x = screen.width / 2 - (wide ? 200 : 152) + (cw - r.width() * zoom) / 2,
				y = 64 + (Math.max(12, screen.height - 112) - r.height() * zoom) / 2;
		screen.mouseClicked(x + (x0 + 0.5) * zoom, y + (y0 + 0.5) * zoom, button);
		screen.mouseDragged(
				x + (x1 + 0.5) * zoom,
				y + (y1 + 0.5) * zoom,
				button,
				(x1 - x0) * zoom,
				(y1 - y0) * zoom);
		screen.mouseReleased(x + (x1 + 0.5) * zoom, y + (y1 + 0.5) * zoom, button);
	}

	private static void shot(String name) {
		var mc = Minecraft.getInstance();
		Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> {});
	}

	private static void verifyOuterPreview(boolean visible) {
		var mc = Minecraft.getInstance();
		var screen = mc.screen;
		try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
			double sx = image.getWidth() / (double) screen.width,
					sy = image.getHeight() / (double) screen.height;
			int blue = 0, left = screen.width / 2 - 200;
			for (int y = (int) (87 * sy); y < (int) (113 * sy); y++)
				for (int x = (int) ((left + 324) * sx); x < (int) ((left + 353) * sx); x++) {
					int pixel = CharacterSkins.swapRedBlue(image.getPixelRGBA(x, y));
					int r = (pixel >>> 16) & 255, g = (pixel >>> 8) & 255, b = pixel & 255;
					if (b > r * 2 && b > g * 1.8) blue++;
				}
			require(
					visible ? blue > 10 : blue <= 10,
					"Outer visibility matches preview toggle (visible="
							+ visible
							+ ", pixels="
							+ blue
							+ ")");
		}
	}

	private static void verifyImages() throws Exception {
		var defaults = ClientProfiles.defaults();
		require(
				defaults.bodyStyle().endsWith(":slim") && CharacterSkins.bundled(defaults),
				"New characters use bundled fire_berry slim skin");
		try (var input =
						Minecraft.getInstance()
								.getResourceManager()
								.open(CharacterSkins.DEFAULT_TEXTURE);
				var original = NativeImage.read(input);
				var rendered = CharacterSkins.image(defaults)) {
			int outerPixels = 0, outerX = -1, outerY = -1;
			for (int y = 0; y < 64; y++)
				for (int x = 0; x < 64; x++) {
					boolean base = SkinLayout.contains(Layer.BASE, true, x, y),
							outer = SkinLayout.contains(Layer.OUTER, true, x, y);
					int expected =
							base
									? original.getPixelRGBA(x, y) | 0xFF000000
									: outer ? original.getPixelRGBA(x, y) : 0;
					require(
							rendered.getPixelRGBA(x, y) == expected,
							"Bundled skin retains source UV pixels and both layers");
					if (outer && (expected >>> 24) != 0) {
						outerPixels++;
						outerX = x;
						outerY = y;
					}
				}
			require(outerPixels > 0, "Bundled skin contains an outer layer");
			int[] erase = new int[4096];
			erase[outerY * 64 + outerX] = SkinPaint.TRANSPARENT;
			try (var edited =
					CharacterSkins.image(
							new CharacterAppearance(
									defaults.bodyStyle(),
									defaults.hairStyle(),
									defaults.colors(),
									new SkinPaint(erase)))) {
				require(
						edited.getPixelRGBA(outerX, outerY) == 0,
						"Eraser removes original bundled outer pixels");
				require(
						edited.getPixelRGBA(8, 8) == rendered.getPixelRGBA(8, 8),
						"Outer erase preserves original base");
			}
		}
		try (var wide =
				CharacterSkins.image(
						new CharacterAppearance(
								"terra_character:wide", defaults.hairStyle(), defaults.colors()))) {
			for (int y = 0; y < 64; y++)
				for (int x = 0; x < 64; x++)
					if (SkinLayout.contains(Layer.BASE, false, x, y))
						require(
								(wide.getPixelRGBA(x, y) >>> 24) == 255,
								"Slim bundled skin remaps to complete wide base UVs");
		}

		for (boolean slim : new boolean[] {false, true}) {
			var a = ClientProfiles.defaults();
			a =
					new CharacterAppearance(
							slim ? "terra_character:slim" : "terra_character:wide",
							"terra_character:short",
							a.colors());
			try (var image = CharacterSkins.image(a)) {
				for (int y = 0; y < 64; y++)
					for (int x = 0; x < 64; x++)
						require(
								(image.getPixelRGBA(x, y) >>> 24)
										== (SkinLayout.contains(Layer.BASE, slim, x, y) ? 255 : 0),
								"Base opacity/outer transparency " + x + "," + y);
			}
			int[] pixels = new int[4096];
			pixels[8 * 64 + 8] = 0xFF123456;
			pixels[8 * 64 + 40] = 0xFFABCDEF;
			pixels[0] = 0xFFFFFFFF;
			try (var image =
					CharacterSkins.image(
							new CharacterAppearance(
									a.bodyStyle(),
									a.hairStyle(),
									a.colors(),
									new SkinPaint(pixels)))) {
				require(
						CharacterSkins.swapRedBlue(image.getPixelRGBA(8, 8)) == 0xFF123456,
						"Base RGB correct");
				require(
						CharacterSkins.swapRedBlue(image.getPixelRGBA(40, 8)) == 0xFFABCDEF,
						"Outer RGB correct");
				require(image.getPixelRGBA(0, 0) == 0, "Unused UV remains transparent");
			}
		}
	}
}
