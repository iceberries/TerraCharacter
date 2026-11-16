package org.confluence.character_portal.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforge.client.gui.widget.ModListWidget;

import org.confluence.character_portal.CharacterPortal;
import org.confluence.character_portal.client.WorldEditors;
import org.confluence.character_portal.client.WorldStarEntry;
import org.confluence.character_portal.client.WorldStarsView;
import org.confluence.character_portal.integration.AdapterRegistry;
import org.confluence.character_portal.service.CharacterStatisticsService;
import org.confluence.character_portal.storage.AtomicNbt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/** Narrow startup check for the optional integration; never creates or enters a world. */
@EventBusSubscriber(modid = CharacterPortal.MODID, value = Dist.CLIENT)
public final class IntegrationLoadSmokeTest {
	private static boolean done;
	private static int ticks;

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		var mc = Minecraft.getInstance();
		if (done || !Boolean.getBoolean("character_portal.integration_smoke")) return;
		if (mc.getOverlay() == null
				&& mc.screen != null
				&& mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) {
			mc.setScreen(new TitleScreen());
			return;
		}
		if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) {
			if (++ticks > 1800) {
				done = true;
				CharacterPortal.LOGGER.error(
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
					!ModList.get().isLoaded("character_portal_confluence"),
					"No separate compatibility mod installed");
			var adapters =
					AdapterRegistry.all().stream()
							.map(a -> a.id().toString())
							.collect(Collectors.toSet());
			require(
					adapters.containsAll(
							Set.of(
									"character_portal:vanilla",
									"character_portal:world_local",
									"character_portal:attributes",
									"character_portal:persistent_data")),
					"Core character and world state adapters are registered");
			var compatible =
					adapters.stream()
							.filter(id -> id.startsWith("character_portal_confluence:"))
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
								.map(id -> "character_portal_confluence:" + id)
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
										"org.confluence.character_portal.compat.confluence.mixin.ManaMixin")
								.isAssignableFrom(
										Class.forName(
												"org.confluence.mod.common.attachment.ManaStorage")),
						"Conditional Confluence mixins applied");
			} else {
				require(compatible.isEmpty(), "No optional adapter registered without Confluence");
				try {
					Class.forName("org.confluence.mod.Confluence");
					throw new AssertionError("Confluence leaked onto standalone runtime classpath");
				} catch (ClassNotFoundException expected) {
				}
			}
			checkSettings(mc);
			result =
					"PASS: "
							+ (confluence
									? "built-in Confluence integration, eleven adapters, client"
											+ " hooks, conditional mixins and legacy adapter"
											+ " envelopes"
									: "standalone loading, optional integration disabled, no"
											+ " Confluence runtime classes")
							+ "; settings navigation and options.txt persistence";
		} catch (Throwable ex) {
			CharacterPortal.LOGGER.error("Integration startup check failed", ex);
			result = "FAIL: " + ex;
		}
		try {
			Files.writeString(mc.gameDirectory.toPath().resolve("integration-result.txt"), result);
		} catch (Exception ex) {
			CharacterPortal.LOGGER.error("Cannot write integration startup report", ex);
		}
		CharacterPortal.LOGGER.info(result);
		mc.stop();
	}

	private static void checkSettings(Minecraft mc) throws IOException {
		Screen title = mc.screen;
		var mods = new ModListScreen(title);
		mc.setScreen(mods);
		var list = mods.children().stream()
				.filter(ModListWidget.class::isInstance)
				.map(ModListWidget.class::cast)
				.findFirst()
				.orElseThrow();
		var characterPortal = list.children().stream()
				.filter(entry -> CharacterPortal.MODID.equals(entry.getInfo().getModId()))
				.findFirst()
				.orElseThrow();
		characterPortal.setFocused(true);
		press(mods, "fml.menu.mods.config");
		Screen hub = mc.screen;
		require(
				hub != null && hub.getTitle().equals(
						Component.translatable("character_portal.settings.title")),
				"Mod List opens Character Portal settings");

		press(hub, "options.video");
		require(mc.screen instanceof VideoSettingsScreen, "Graphics opens vanilla video settings");
		mc.screen.onClose();
		require(mc.screen == hub, "Video settings returns to the hub");

		press(hub, "options.mouse_settings");
		require(mc.screen instanceof MouseSettingsScreen, "Mouse opens vanilla mouse settings");
		var invertY = mc.options.invertYMouse();
		boolean original = invertY.get();
		try {
			var mouseOptions = mc.screen.children().stream()
					.filter(OptionsList.class::isInstance)
					.map(OptionsList.class::cast)
					.findFirst()
					.orElseThrow();
			if (!(mouseOptions.findOption(invertY) instanceof CycleButton<?> toggle)) {
				throw new AssertionError("Vanilla mouse settings expose the invert Y toggle");
			}
			toggle.onPress();
			require(invertY.get() != original, "Mouse settings changes live Minecraft options");
			mc.screen.onClose();
			require(mc.screen == hub, "Mouse settings returns to the hub");
			require(
					Files.readAllLines(mc.options.getFile().toPath())
							.contains("invertYMouse:" + !original),
					"Vanilla mouse settings saves options.txt");
		} finally {
			invertY.set(original);
			mc.options.save();
		}

		press(hub, "controls.keybinds");
		require(mc.screen instanceof KeyBindsScreen, "Controls opens vanilla keybind settings");
		mc.screen.onClose();
		require(mc.screen == hub, "Keybind settings returns to the hub");

		press(hub, "options.language");
		require(mc.screen instanceof LanguageSelectScreen,
				"Language opens vanilla language settings");
		mc.screen.onClose();
		require(mc.screen == hub, "Language settings returns to the hub");

		press(hub, "options.accessibility");
		require(mc.screen instanceof AccessibilityOptionsScreen,
				"Accessibility opens vanilla accessibility settings");
		mc.screen.onClose();
		require(mc.screen == hub, "Accessibility settings returns to the hub");

		press(hub, "options.title");
		require(mc.screen instanceof OptionsScreen, "More options opens vanilla options");
		mc.screen.onClose();
		require(mc.screen == hub, "More options returns to the hub");
		hub.onClose();
		require(mc.screen == mods, "Settings hub returns to Mod List");
		mods.onClose();
		require(mc.screen == title, "Mod List returns to the title screen");
	}

	private static void press(Screen screen, String key) {
		var button = screen.children().stream()
				.filter(Button.class::isInstance)
				.map(Button.class::cast)
				.filter(widget -> widget.getMessage().getContents()
						instanceof TranslatableContents message && message.getKey().equals(key))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing button " + key));
		require(button.active, "Active button " + key);
		button.onPress();
	}

	private static void require(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}
}
