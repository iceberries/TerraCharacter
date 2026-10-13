package org.confluence.terra_character.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.client.WorldEditors;
import org.confluence.terra_character.integration.AdapterRegistry;

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
										"ever_beneficial",
										"extra_inventory",
										"piggy_bank",
										"safe",
										"quests",
										"world_quests",
										"curios")
								.stream()
								.map(id -> "terra_character_confluence:" + id)
								.collect(Collectors.toSet());
				require(
						compatible.equals(expected),
						"All eight legacy adapter identifiers preserved");
				require(
						Class.forName("org.confluence.terra_character_confluence.mixin.ManaMixin")
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
									? "built-in Confluence integration, eight adapters, client"
											+ " hooks and conditional mixins"
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
