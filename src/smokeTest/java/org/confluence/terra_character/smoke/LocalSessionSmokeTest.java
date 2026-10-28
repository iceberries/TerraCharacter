package org.confluence.terra_character.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
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
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;

import org.confluence.mod.common.attachment.PlayerSpecialData;
import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.client.*;
import org.confluence.terra_character.compat.confluence.integration.InitialItemsState;
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
	private static final boolean INITIAL_ITEMS_ONLY =
			"true".equals(System.getenv("TERRA_CHARACTER_INITIAL_ITEMS_ONLY"));
	private static int phase, ticks;
	private static boolean done;
	private static UUID identity;
	private static CompletableFuture<Void> check;
	private static int initialAwards;
	private static boolean checkingInitialItems;

	@SubscribeEvent
	public static void initialAward(AdvancementEvent.AdvancementEarnEvent event) {
		if (!checkingInitialItems) return;
		var session = CharacterSession.of(event.getEntity());
		if (session != null
				&& session.profile().id().equals(identity)
				&& event.getAdvancement()
						.id()
						.equals(ResourceLocation.parse("confluence:achievements/new_world")))
			initialAwards++;
	}

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
				mc.options.pauseOnLostFocus = false;
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
				if (INITIAL_ITEMS_ONLY) {
					finish(
							mc,
							"PASS: initial rewards once per character across fresh worlds, separate"
								+ " characters, portable save, legacy migration and death copy",
							null);
					return;
				}
				phase = 3;
				mc.setScreen(null);
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
								+ " disconnect return, initial rewards once per character across"
								+ " worlds",
						null);
			}
		} catch (Throwable ex) {
			finish(mc, "FAIL: " + ex, ex);
		}
	}

	private static void verify(ServerPlayer player) throws Exception {
		var session = CharacterSession.of(player);
		require(session != null && !session.failed(), "Character session joined successfully");
		verifyInitialItems(player);
		if (INITIAL_ITEMS_ONLY) {
			session.save(player);
			var saved = ClientProfiles.store().read(identity);
			require(
					saved.getCompound("Adapters")
							.getCompound("terra_character_confluence:quests")
							.getCompound("Data")
							.getBoolean(InitialItemsState.NBT_KEY),
					"Committed character keeps the initial-reward receipt");
			return;
		}
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

	private static void verifyInitialItems(ServerPlayer player) throws Exception {
		var adapter =
				AdapterRegistry.all().stream()
						.filter(a -> a.id().toString().equals("terra_character_confluence:quests"))
						.findFirst()
						.orElseThrow();
		var original = adapter.capture(player);
		require(
				original.getBoolean(InitialItemsState.NBT_KEY),
				"Fresh world's welcome reward was recorded on the character");
		checkingInitialItems = true;
		initialAwards = 0;
		try {
			adapter.initialize(player);
			require(
					!adapter.capture(player).getBoolean(InitialItemsState.NBT_KEY),
					"New character starts unclaimed");
			awardInFreshWorld(player);
			require(initialAwards == 1, "First world runs the original initial-reward event once");
			var characterA = adapter.capture(player);
			require(
					characterA.getBoolean(InitialItemsState.NBT_KEY),
					"Receipt is part of the portable character adapter");
			adapter.initialize(player);
			adapter.restore(player, characterA, 1);
			awardInFreshWorld(player);
			require(
					initialAwards == 1,
					"Restored character entering a new world does not replay initial rewards");
			adapter.initialize(player);
			awardInFreshWorld(player);
			require(
					initialAwards == 2,
					"Another fresh character still receives its own initial reward");
			adapter.restore(player, characterA, 1);
			var clone = new PlayerSpecialData();
			clone.deserializeNBT(
					player.registryAccess(),
					PlayerSpecialData.of(player).serializeNBT(player.registryAccess()));
			require(
					((InitialItemsState) clone).terraCharacter$hasInitialItems(),
					"Death-copy serialization preserves the receipt");
			var legacy = characterA.copy();
			legacy.remove(InitialItemsState.NBT_KEY);
			adapter.restore(player, legacy, 1);
			awardInFreshWorld(player);
			require(
					initialAwards == 2,
					"Legacy character without the new field does not receive a duplicate upgrade"
							+ " gift");
			var unclaimed = characterA.copy();
			unclaimed.putBoolean(InitialItemsState.NBT_KEY, false);
			adapter.restore(player, unclaimed, 1);
			awardInFreshWorld(player);
			require(
					initialAwards == 3,
					"Explicit unclaimed state survives saving before the first reward");
		} finally {
			checkingInitialItems = false;
			adapter.restore(player, original, 1);
		}
	}

	private static void awardInFreshWorld(ServerPlayer player) {
		var server = player.server;
		var advancement =
				server.getAdvancements()
						.get(ResourceLocation.parse("confluence:achievements/new_world"));
		require(advancement != null, "Main mod's actual initial-reward advancement exists");
		var progress =
				new PlayerAdvancements(
						server.getFixerUpper(),
						server.getPlayerList(),
						server.getAdvancements(),
						server.getWorldPath(LevelResource.ROOT)
								.resolve("initial-reward-" + UUID.randomUUID() + ".json"),
						player);
		try {
			for (String criterion : advancement.value().criteria().keySet())
				progress.award(advancement, criterion);
			require(
					progress.getOrStartProgress(advancement).isDone(),
					"New world's advancement still completes for the UI");
			for (String criterion : advancement.value().criteria().keySet())
				progress.award(advancement, criterion);
		} finally {
			progress.stopListening();
		}
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
