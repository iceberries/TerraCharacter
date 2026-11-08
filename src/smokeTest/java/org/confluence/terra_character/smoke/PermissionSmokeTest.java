package org.confluence.terra_character.smoke;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.client.*;
import org.confluence.terra_character.runtime.CharacterSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Only world creation and server-owned command permissions; never opens a user world. */
@EventBusSubscriber(modid = TerraCharacter.MODID, value = Dist.CLIENT)
public final class PermissionSmokeTest {
	private static final long START = System.currentTimeMillis();
	private static final boolean[] CHEATS = {true, true, false, false, true};
	private static final Map<Boolean, WorldProfile> WORLDS = new HashMap<>();
	private static final Map<Boolean, String> FOLDERS = new HashMap<>();
	private static final StringBuilder REPORT = new StringBuilder();
	private static int phase, ticks, step;
	private static boolean done;
	private static UUID character;
	private static CompletableFuture<String> pending;

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		if (done || !Boolean.getBoolean("terra_character.permission_smoke")) return;
		var mc = Minecraft.getInstance();
		try {
			if (System.currentTimeMillis() - START > 360000)
				throw new AssertionError("Permission check timed out at " + phase + "/" + step);
			if (phase == 0 && mc.getOverlay() == null) {
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
								.endsWith(Path.of("build", "permission-smoke-run")),
						"Isolated directory");
				character = UUID.randomUUID();
				ClientProfiles.store()
						.create(
								new CharacterProfile(
										character,
										"Permission probe",
										CharacterDifficulty.SOFTCORE,
										ClientProfiles.defaults(),
										CharacterProfile.Status.ACTIVE));
				mc.options.pauseOnLostFocus = false;
				mc.options.renderDistance().set(2);
				openCreation(mc);
			} else if (phase == 1
					&& mc.screen instanceof CreateWorldScreen creation
					&& ++ticks > 10) {
				var state = creation.getUiState();
				if (step == 0) verifyChoices(creation);
				state.setName("Permission " + CHEATS[step] + " " + character);
				state.getNormalPresetList().stream()
						.filter(e -> e.preset() != null && e.preset().is(WorldPresets.FLAT))
						.findFirst()
						.ifPresent(state::setWorldType);
				state.setSeed("12345");
				state.setGameMode(SelectedGameMode.SURVIVAL);
				state.setAllowCommands(CHEATS[step]);
				state.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
				FOLDERS.put(CHEATS[step], state.getTargetFolder());
				var create =
						creation.children().stream()
								.filter(
										w ->
												w instanceof Button b
														&& b.getMessage()
																.getString()
																.equals(
																		Component.translatable(
																						"selectWorld.create")
																				.getString()))
								.map(Button.class::cast)
								.findFirst()
								.orElseThrow();
				phase = 2;
				ticks = 0;
				create.onPress();
			} else if (phase == 2
					&& mc.player != null
					&& ClientState.profile() != null
					&& ++ticks > 40) {
				var server = mc.getSingleplayerServer();
				boolean expected = CHEATS[step];
				pending =
						CompletableFuture.supplyAsync(
								() ->
										probe(
												server.getPlayerList().getPlayers().getFirst(),
												expected),
								server);
				phase = 3;
			} else if (phase == 3 && pending.isDone()) {
				String serverResult = pending.join();
				var root = mc.getConnection().getCommands().getRoot();
				require(root.getChild("help") != null, "Help remains available");
				require(
						(root.getChild("gamemode") != null) == CHEATS[step],
						"Client gamemode command visibility");
				require(
						(root.getChild("give") != null) == CHEATS[step],
						"Client give command visibility");
				REPORT.append("Step ")
						.append(step)
						.append(": ")
						.append(serverResult)
						.append(", client tree correct\n");
				if (++step == CHEATS.length) {
					finish(
							mc,
							"PASS: creation choices, native creation, save/reopen, cross-world"
								+ " permissions, guest isolation\n"
									+ REPORT);
					return;
				}
				phase = 4;
				mc.level.disconnect();
				mc.disconnect();
				mc.setScreen(new TitleScreen());
			} else if (phase == 4
					&& mc.getSingleplayerServer() == null
					&& CharacterSession.current() == null) {
				if (!WORLDS.containsKey(CHEATS[step])) openCreation(mc);
				else {
					ClientProfiles.setSelected(character);
					String folder = FOLDERS.get(CHEATS[step]);
					CharacterSession.arm(
							ClientProfiles.store(),
							character,
							ClientProfiles.saves().resolve(folder),
							WORLDS.get(CHEATS[step]));
					phase = 2;
					ticks = 0;
					mc.createWorldOpenFlows()
							.openWorld(
									folder, () -> finish(mc, "FAIL: reopen cancelled\n" + REPORT));
				}
			}
		} catch (Throwable ex) {
			TerraCharacter.LOGGER.error("Permission smoke failed", ex);
			finish(mc, "FAIL: " + ex + "\n" + REPORT);
		}
	}

	private static void openCreation(Minecraft mc) {
		ClientProfiles.setSelected(character);
		ClientProfiles.clearWorldOptions();
		phase = 1;
		ticks = 0;
		CreateWorldScreen.openFresh(mc, new TitleScreen());
	}

	private static void verifyChoices(CreateWorldScreen creation) throws Exception {
		var state = creation.getUiState();
		state.setGameMode(SelectedGameMode.CREATIVE);
		require(state.isAllowCommands(), "Vanilla creative default enables cheats");
		WorldCreationCoordinator.configure(creation);
		require(state.isAllowCommands(), "Implicit creative choice survives mode normalization");
		for (var mode :
				new SelectedGameMode[] {SelectedGameMode.SURVIVAL, SelectedGameMode.CREATIVE}) {
			for (boolean expected : new boolean[] {false, true}) {
				state.setGameMode(mode);
				state.setAllowCommands(expected);
				WorldCreationCoordinator.configure(creation);
				require(
						state.isAllowCommands() == expected,
						"Explicit world choice preserved for " + mode);
			}
		}
	}

	private static String probe(ServerPlayer player, boolean expected) {
		var server = player.server;
		var session = CharacterSession.of(player);
		require(session != null && !session.failed(), "Character session active");
		WORLDS.put(expected, session.world());
		require(server.getWorldData().isAllowCommands() == expected, "Saved world cheat setting");
		require(
				server.isSingleplayerOwner(player.getGameProfile()),
				"Account still owns local server");
		int permissions = server.getProfilePermissions(player.getGameProfile());
		require(permissions == (expected ? 4 : 0), "Vanilla computes owner permissions");
		var source = player.createCommandSourceStack();
		var commands = server.getCommands().getDispatcher().getRoot();
		require(commands.getChild("help").canUse(source), "Server help remains allowed");
		require(
				commands.getChild("gamemode").canUse(source) == expected,
				"Server gamemode authorization");
		require(commands.getChild("give").canUse(source) == expected, "Server give authorization");
		var guest = new GameProfile(UUID.randomUUID(), "PermissionGuest");
		require(
				server.getProfilePermissions(guest) == 0,
				"Guest does not inherit host authorization");
		return "allowCommands=" + expected + ", permissions=" + permissions + ", guest=0";
	}

	private static void finish(Minecraft mc, String result) {
		if (done) return;
		done = true;
		TerraCharacter.LOGGER.info(result);
		try {
			Files.writeString(mc.gameDirectory.toPath().resolve("permission-result.txt"), result);
		} catch (Exception ex) {
			TerraCharacter.LOGGER.error("Cannot write permission result", ex);
		}
		if (mc.level != null) {
			mc.level.disconnect();
			mc.disconnect();
		}
		mc.stop();
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
