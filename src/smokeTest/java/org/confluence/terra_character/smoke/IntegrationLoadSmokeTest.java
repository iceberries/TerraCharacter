package org.confluence.terra_character.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.client.WorldEditors;
import org.confluence.terra_character.client.WorldStarEntry;
import org.confluence.terra_character.client.WorldStarsView;
import org.confluence.terra_character.integration.AdapterRegistry;
import org.confluence.terra_character.service.CharacterStatisticsService;
import org.confluence.terra_character.storage.AtomicNbt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/** Narrow startup check for the optional integration; never creates or enters a world. */
@EventBusSubscriber(modid = TerraCharacter.MODID, value = Dist.CLIENT)
public final class IntegrationLoadSmokeTest {
	private static boolean done;
	private static int ticks;

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		var mc = Minecraft.getInstance();
		if (done || !Boolean.getBoolean("terra_character.integration_smoke")) return;
		if (mc.getOverlay() == null
				&& mc.screen != null
				&& mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) {
			mc.setScreen(new TitleScreen());
			return;
		}
		if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) {
			if (++ticks > 1800) {
				done = true;
				TerraCharacter.LOGGER.error(
						"Integration startup timed out at screen {}", mc.screen);
				mc.stop();
			}
			return;
		}
		done = true;
		String result;
		try {
			require(
					mc.gameDirectory
							.toPath()
							.toAbsolutePath()
							.normalize()
							.endsWith(Path.of("build", "integration-smoke-run")),
					"Isolated startup directory");
			boolean confluence = ModList.get().isLoaded("confluence");
			var summary = CharacterStatisticsService.summary(new CompoundTag());
			require(
					summary.hasMana() == confluence && summary.playTicks() == 0,
					"Offline character statistics load without an active world; mana is optional");
			require(
					WorldStarsView.class.isAssignableFrom(WorldSelectionList.class)
							&& WorldStarEntry.class.isAssignableFrom(
									WorldSelectionList.WorldListEntry.class),
					"World star hooks apply without replacing the existing world list");
			require(
					!ModList.get().isLoaded("terra_character_confluence"),
					"No separate compatibility mod installed");
			var adapters =
					AdapterRegistry.all().stream()
							.map(a -> a.id().toString())
							.collect(Collectors.toSet());
			var compatible =
					adapters.stream()
							.filter(id -> id.startsWith("terra_character_confluence:"))
							.collect(Collectors.toSet());
			require(
					(WorldEditors.getIntegration() != null) == confluence,
					"World options activate only with Confluence");
			if (confluence) {
				var expected =
						Set.of(
										"mana",
										"soul",
										"soulcerer_skill",
										"ever_beneficial",
										"extra_inventory",
										"piggy_bank",
										"safe",
										"void_vault",
										"quests",
										"world_quests",
										"curios")
								.stream()
								.map(id -> "terra_character_confluence:" + id)
								.collect(Collectors.toSet());
				require(
						compatible.equals(expected),
						"Legacy adapter identifiers preserved and new character attachments"
							+ " covered");
				var legacy = new CompoundTag();
				for (String id : expected) {
					var entry = new CompoundTag();
					entry.putInt("Version", 1);
					var data = new CompoundTag();
					data.putString("LegacyValue", id);
					entry.put("Data", data);
					legacy.put(id, entry);
				}
				Path fixture = mc.gameDirectory.toPath().resolve("legacy-adapters.nbt");
				AtomicNbt.write(fixture, legacy, false);
				var loaded = AtomicNbt.read(fixture);
				AdapterRegistry.validate(loaded);
				require(
						legacy.equals(loaded),
						"Legacy adapter envelopes are recognized without rewriting IDs or data");
				require(
						Class.forName(
										"org.confluence.terra_character.compat.confluence.mixin.ManaMixin")
								.isAssignableFrom(
										Class.forName(
												"org.confluence.mod.common.attachment.ManaStorage")),
						"Conditional Confluence mixins applied");
			} else {
				require(compatible.isEmpty(), "No optional adapter registered without Confluence");
				require(adapters.size() == 2, "Standalone vanilla adapters remain available");
				try {
					Class.forName("org.confluence.mod.Confluence");
					throw new AssertionError("Confluence leaked onto standalone runtime classpath");
				} catch (ClassNotFoundException expected) {
				}
			}
			result =
					"PASS: "
							+ (confluence
									? "built-in Confluence integration, eleven adapters, client"
											+ " hooks, conditional mixins and legacy adapter"
											+ " envelopes"
									: "standalone loading, optional integration disabled, no"
											+ " Confluence runtime classes");
		} catch (Throwable ex) {
			TerraCharacter.LOGGER.error("Integration startup check failed", ex);
			result = "FAIL: " + ex;
		}
		try {
			Files.writeString(mc.gameDirectory.toPath().resolve("integration-result.txt"), result);
		} catch (Exception ex) {
			TerraCharacter.LOGGER.error("Cannot write integration startup report", ex);
		}
		TerraCharacter.LOGGER.info(result);
		mc.stop();
	}

	private static void require(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}
}
