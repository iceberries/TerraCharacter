package org.confluence.terra_character.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.client.*;
import org.confluence.terra_character.integration.AdapterRegistry;
import org.confluence.terra_character.runtime.CharacterSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** One isolated join/save/disconnect regression; never opens an existing player world. */
@EventBusSubscriber(modid = TerraCharacter.MODID, value = Dist.CLIENT)
public final class LocalSessionSmokeTest {
	private static final long START = System.currentTimeMillis();
	private static int phase, ticks;
	private static boolean done;
	private static UUID identity;
	private static CompletableFuture<Void> check;

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		if (done || !Boolean.getBoolean("terra_character.local_session_smoke")) return;
		var mc = Minecraft.getInstance();
		try {
			if (System.currentTimeMillis() - START > 300_000)
				throw new AssertionError(
						"Local session timed out at phase " + phase + ": " + mc.screen);
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
								.endsWith(Path.of("build", "local-session-smoke-run")),
						"Isolated test directory");
				identity = UUID.randomUUID();
				var profile =
						new CharacterProfile(
								identity,
								"Local regression",
								CharacterDifficulty.SOFTCORE,
								ClientProfiles.defaults(),
								CharacterProfile.Status.ACTIVE);
				ClientProfiles.store().create(profile);
				ClientProfiles.setSelected(identity);
				var world =
						new WorldProfile(
								UUID.randomUUID(),
								"Local regression",
								new WorldSettings(
										"12345", "minecraft:flat", WorldDifficulty.NORMAL));
				String folder = "local-regression-" + identity;
				CharacterSession.arm(
						ClientProfiles.store(),
						identity,
						ClientProfiles.saves().resolve(folder),
						world);
				mc.options.renderDistance().set(2);
				mc.options.simulationDistance().set(5);
				var rules = new GameRules();
				rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
				phase = 1;
				mc.createWorldOpenFlows()
						.createFreshLevel(
								folder,
								new LevelSettings(
										world.name(),
										GameType.SURVIVAL,
										false,
										Difficulty.NORMAL,
										false,
										rules,
										WorldDataConfiguration.DEFAULT),
								new WorldOptions(12345, false, false),
								r ->
										r.registryOrThrow(Registries.WORLD_PRESET)
												.getHolderOrThrow(WorldPresets.FLAT)
												.value()
												.createWorldDimensions(),
								new TitleScreen());
			} else if (phase == 1) {
				if (mc.screen instanceof DisconnectedScreen)
					throw new AssertionError(
							"Singleplayer disconnected before saving could be verified");
				if (mc.player == null || ClientState.profile() == null || ++ticks < 40) return;
				var server = mc.getSingleplayerServer();
				check =
						CompletableFuture.runAsync(
								() -> {
									try {
										verify(server.getPlayerList().getPlayers().getFirst());
									} catch (Exception ex) {
										throw new RuntimeException(ex);
									}
								},
								server);
				phase = 2;
			} else if (phase == 2 && check.isDone()) {
				check.join();
				phase = 3;
				mc.getConnection()
						.getConnection()
						.disconnect(Component.literal("Local disconnect regression"));
			} else if (phase == 3
					&& mc.screen instanceof DisconnectedScreen screen
					&& mc.getSingleplayerServer() == null
					&& CharacterSession.current() == null) {
				var back =
						screen.children().stream()
								.filter(
										c ->
												c instanceof Button button
														&& button.getMessage()
																.getString()
																.equals(
																		ClientProfiles.text(
																						"characters")
																				.getString()))
								.map(c -> (Button) c)
								.findFirst()
								.orElseThrow(
										() ->
												new AssertionError(
														"Local disconnect must offer character"
															+ " selection"));
				back.onPress();
				require(
						mc.screen instanceof CharacterSelectionScreen,
						"Return to character selection instead of multiplayer");
				finish(
						mc,
						"PASS: singleplayer join/save, soul/skills/void-vault roundtrip, local"
							+ " disconnect return",
						null);
			}
		} catch (Throwable ex) {
			finish(mc, "FAIL: " + ex, ex);
		}
	}

	private static void verify(ServerPlayer player) throws Exception {
		var session = CharacterSession.of(player);
		require(session != null && !session.failed(), "Character session joined successfully");
		for (String name : new String[] {"soul", "soulcerer_skill", "void_vault"}) {
			var adapter =
					AdapterRegistry.all().stream()
							.filter(
									a ->
											a.id().toString()
													.equals("terra_character_confluence:" + name))
							.findFirst()
							.orElseThrow(() -> new AssertionError("Missing adapter " + name));
			require(
					adapter.legacyAttachmentIds().contains("confluence:" + name),
					"Attachment ownership " + name);
			var data = adapter.capture(player);
			if (name.equals("soul")) {
				data.putInt("ritualCount", 1);
				data.putFloat("currentSoul", 7);
			} else if (name.equals("void_vault")) {
				var items = new ListTag();
				var stack =
						(CompoundTag) new ItemStack(Items.DIAMOND, 3).save(player.registryAccess());
				stack.putByte("Slot", (byte) 0);
				items.add(stack);
				data.put("Items", items);
			}
			adapter.initialize(player);
			adapter.restore(player, data, adapter.schemaVersion());
			require(adapter.capture(player).equals(data), "Preserved character data " + name);
		}
		session.save(player);
		var document = ClientProfiles.store().read(identity);
		for (String name : new String[] {"soul", "soulcerer_skill", "void_vault"})
			require(
					document.getCompound("Adapters").contains("terra_character_confluence:" + name),
					"Saved adapter " + name);
	}

	private static void finish(Minecraft mc, String result, Throwable error) {
		done = true;
		if (error != null) TerraCharacter.LOGGER.error(result, error);
		else TerraCharacter.LOGGER.info(result);
		try {
			Files.writeString(
					mc.gameDirectory.toPath().resolve("local-session-result.txt"), result);
		} catch (Exception ex) {
			TerraCharacter.LOGGER.error("Cannot write local regression result", ex);
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
