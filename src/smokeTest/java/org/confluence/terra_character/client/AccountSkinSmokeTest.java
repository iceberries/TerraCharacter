package org.confluence.terra_character.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.service.SkinLayout;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Real account request and base/paint renderer check in an isolated client, without entering a
 * world.
 */
@EventBusSubscriber(modid = TerraCharacter.MODID, value = Dist.CLIENT)
public final class AccountSkinSmokeTest {
	private static int stage, ticks, frames;
	private static boolean done;

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		if (done || !Boolean.getBoolean("terra_character.account_skin_smoke")) return;
		var mc = Minecraft.getInstance();
		try {
			if (++ticks > 1800) throw new AssertionError("Account skin check timed out");
			if (mc.getOverlay() != null) return;
			if (stage == 0) {
				if (mc.screen != null
						&& mc.screen
								.getClass()
								.getSimpleName()
								.equals("AccessibilityOnboardingScreen")) {
					mc.setScreen(new TitleScreen());
					return;
				}
				if (!(mc.screen instanceof TitleScreen)) return;
				require(
						mc.gameDirectory
								.toPath()
								.toAbsolutePath()
								.normalize()
								.endsWith(Path.of("build", "account-skin-smoke-run")),
						"Isolated skin check directory");
				mc.setScreen(new CharacterEditorScreen(new TitleScreen(), null, null));
				mc.screen.children().stream()
						.filter(EditBox.class::isInstance)
						.map(EditBox.class::cast)
						.findFirst()
						.orElseThrow()
						.setValue("NotTheLoginName");
				stage = 1;
			} else if (stage == 1) {
				var editor = (CharacterEditorScreen) mc.screen;
				var applied = editor.getClass().getDeclaredField("accountSkinApplied");
				applied.setAccessible(true);
				if (!applied.getBoolean(editor) || ++frames < 10) return;
				var field = editor.getClass().getDeclaredField("draft");
				field.setAccessible(true);
				var appearance = (CharacterAppearance) field.get(editor);
				require(
						appearance.hairStyle().equals(AccountSkins.STYLE)
								&& !appearance.baseSkin().isEmpty(),
						"Account skin becomes the default base");
				require(
						editor.children().stream().anyMatch(DiscreteSlider.class::isInstance)
								== ModList.get().isLoaded("confluence"),
						"Character difficulty selector requires Confluence");
				require(
						editor.children().stream()
								.filter(
										w ->
												w instanceof Button b
														&& b.getMessage()
																.getString()
																.equals(
																		ClientProfiles.text("save")
																				.getString()))
								.allMatch(w -> ((Button) w).active),
						"Save becomes available after lookup");
				for (boolean slim : List.of(false, true)) {
					var value =
							new CharacterAppearance(
									slim ? "terra_character:slim" : "terra_character:wide",
									AccountSkins.STYLE,
									Map.of(),
									SkinPaint.EMPTY,
									Wardrobe.EMPTY,
									DyeMasks.EMPTY,
									appearance.baseSkin());
					try (var image = CharacterSkins.image(value)) {
						for (var layer : SkinLayout.Layer.values())
							for (var part : SkinLayout.Part.values())
								for (var face : SkinLayout.Face.values()) {
									var from =
											SkinLayout.region(
													part, face, layer, value.baseSkin().slim());
									var to = SkinLayout.region(part, face, layer, slim);
									for (int y = 0; y < to.height(); y++)
										for (int x = 0; x < to.width(); x++)
											require(
													CharacterSkins.swapRedBlue(
																	image.getPixelRGBA(
																			to.x() + x, to.y() + y))
															== value.baseSkin()
																	.pixel(
																			from.x()
																					+ x
																							* from
																									.width()
																							/ to
																									.width(),
																			from.y() + y),
													"Base and outer pixels map correctly to both"
															+ " arm models");
								}
					}
					int[] edits = new int[4096];
					edits[8 * 64 + 40] = SkinPaint.TRANSPARENT;
					try (var image = CharacterSkins.image(value.withPaint(new SkinPaint(edits)))) {
						require(
								(image.getPixelRGBA(40, 8) >>> 24) == 0,
								"Outer layer can be erased independently");
					}
					try (var image = CharacterSkins.image(value.withPaint(SkinPaint.EMPTY))) {
						require(
								CharacterSkins.swapRedBlue(image.getPixelRGBA(40, 8))
										== value.baseSkin().pixel(40, 8),
								"Clearing paint restores the downloaded base");
					}
					require(
							EditorUi.color(value, "test:channel", 0x123456)
									.baseSkin()
									.equals(value.baseSkin()),
							"Dye changes preserve the base skin");
					require(
							EditorUi.channels(value).size() == 4,
							"Account skins have four anatomical dye channels");
					var tinted = EditorUi.color(value, "terra_character:account_body", 0xFFFFFF);
					boolean changed = false;
					try (var original = CharacterSkins.image(value);
							var image = CharacterSkins.image(tinted)) {
						for (int y = 0; y < 64; y++)
							for (int x = 0; x < 64; x++) {
								int before = original.getPixelRGBA(x, y),
										after = image.getPixelRGBA(x, y);
								require(
										(before >>> 24) == (after >>> 24),
										"Quick dye preserves both layer alpha values");
								boolean torso = false;
								for (var layer : SkinLayout.Layer.values())
									for (var face : SkinLayout.Face.values())
										torso |=
												SkinLayout.region(
																SkinLayout.Part.BODY,
																face,
																layer,
																slim)
														.contains(x, y);
								if (!torso)
									require(
											before == after,
											"Torso dye leaves other body parts unchanged");
								changed |= before != after;
							}
					}
					require(changed, "Quick dye changes downloaded skin pixels");
					require(
							EditorUi.resetColor(tinted, "terra_character:account_body")
									.equals(value),
							"Reset restores original skin appearance");
				}
				checkRequestIsolation();
				var person =
						new CharacterProfile(
								UUID.randomUUID(),
								"NotTheLoginName",
								CharacterDifficulty.SOFTCORE,
								appearance,
								CharacterProfile.Status.ACTIVE);
				ClientProfiles.store().create(person);
				require(
						ClientProfiles.store().find(person.id()).orElseThrow().equals(person),
						"Downloaded base persists with the character");
				var result = AccountSkins.load().join();
				String login = mc.getUser().getName().toLowerCase(Locale.ROOT);
				if (result.official())
					require(
							Files.exists(
									mc.gameDirectory
											.toPath()
											.resolve(
													"terra_character/account_skins/"
															+ login
															+ ".nbt")),
							"Cache uses the Minecraft login name");
				finish(
						"PASS: "
								+ (result.official()
										? "official account skin downloaded/cached"
										: "network unavailable; vanilla fallback")
								+ ", login name lookup, model conversion, dual layers, paint"
								+ " restoration, dye retention and character persistence");
			}
		} catch (Throwable ex) {
			TerraCharacter.LOGGER.error("Account skin check failed", ex);
			finish("FAIL: " + ex);
		}
	}

	private static void checkRequestIsolation() throws Exception {
		var generation = AccountSkins.class.getDeclaredField("generation");
		generation.setAccessible(true);
		var current = AccountSkins.class.getDeclaredField("current");
		current.setAccessible(true);
		var original = (AccountSkins.Result) current.get(null);
		var method =
				AccountSkins.class.getDeclaredMethod(
						"completeRequest",
						CompletableFuture.class,
						long.class,
						AtomicReference.class,
						String.class);
		method.setAccessible(true);
		int[] pixels = original.skin().pixels();
		pixels[0] ^= 0x00FFFFFF;
		var late = new AccountSkins.Result(new SkinBase(original.skin().slim(), pixels), true);
		var source = new CompletableFuture<AccountSkins.Result>();
		var stale =
				(CompletableFuture<?>)
						method.invoke(
								null,
								source,
								generation.getLong(null) - 1,
								new AtomicReference<>(original),
								"stale-test");
		source.complete(late);
		stale.join();
		require(
				current.get(null) == original,
				"A superseded account request cannot replace the active skin");
		var timed = new CompletableFuture<AccountSkins.Result>();
		var timeout =
				(CompletableFuture<?>)
						method.invoke(
								null,
								timed,
								generation.getLong(null),
								new AtomicReference<>(original),
								"timeout-test");
		timed.completeExceptionally(new TimeoutException());
		require(timeout.join() == original, "Timeout returns the request's own cached fallback");
		require(
				!timed.complete(late) && current.get(null) == original,
				"Late completion after timeout cannot replace the displayed skin");
	}

	private static void require(boolean value, String text) {
		if (!value) throw new AssertionError(text);
	}

	private static void finish(String result) {
		done = true;
		TerraCharacter.LOGGER.info(result);
		try {
			Files.writeString(
					Minecraft.getInstance()
							.gameDirectory
							.toPath()
							.resolve("account-skin-result.txt"),
					result);
		} catch (Exception ex) {
			TerraCharacter.LOGGER.error("Cannot write skin check report", ex);
		}
		Minecraft.getInstance().stop();
	}
}
