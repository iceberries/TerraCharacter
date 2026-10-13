package org.confluence.terra_character.smoke;

import net.minecraft.client.*;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
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
import org.confluence.terra_character.runtime.*;
import org.confluence.terra_character.storage.*;

import java.net.ServerSocket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Two real clients over TCP, isolated saves and a test-only offline LAN server. Excluded from
 * release jars.
 */
@EventBusSubscriber(modid = TerraCharacter.MODID, value = Dist.CLIENT)
public final class MultiplayerSmokeTest {
	private static final String ROLE = System.getProperty("terra_character.mp_smoke", "");
	private static final long START = System.currentTimeMillis();
	private static int phase, ticks, wait, stage = 1, lastProcessed;
	private static Path exchange;
	private static Process guest;
	private static CharacterProfile host, a, b, hard, medium;
	private static UUID hostAccount, guestAccount;
	private static CompletableFuture<Void> work;
	private static final List<String> passed = new ArrayList<>();
	private static final Set<UUID> testedCharacters = new HashSet<>();
	private static boolean finished, soloDone;

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		if (ROLE.isEmpty() || finished) return;
		var mc = Minecraft.getInstance();
		ticks++;
		try {
			if (System.currentTimeMillis() - START > 600_000)
				throw new AssertionError(
						"MP smoke timeout role="
								+ ROLE
								+ " phase="
								+ phase
								+ " stage="
								+ stage
								+ " screen="
								+ mc.screen);
			if (work != null && work.isDone()) {
				work.join();
				work = null;
			}
			if (phase == 0
					&& mc.getOverlay() == null
					&& ticks > 60
					&& !(mc.screen instanceof TitleScreen)) mc.setScreen(new TitleScreen());
			if (ROLE.equals("host")) hostTick(mc);
			else guestTick(mc);
		} catch (Throwable ex) {
			finished = true;
			TerraCharacter.LOGGER.error("MULTIPLAYER SMOKE FAILED " + ROLE, ex);
			try {
				Files.writeString(mc.gameDirectory.toPath().resolve("mp-result.txt"), "FAIL " + ex);
				if (exchange != null)
					Files.writeString(exchange.resolve(ROLE + "-failed"), ex.toString());
			} catch (Exception ignored) {
			}
			if (guest != null) guest.destroy();
			if (mc.level != null) {
				mc.level.disconnect();
				mc.disconnect();
			}
			mc.stop();
		}
	}

	private static CharacterProfile person(UUID id, String name, CharacterDifficulty d) {
		return new CharacterProfile(
				id, name, d, ClientProfiles.defaults(), CharacterProfile.Status.ACTIVE);
	}

	private static void hostTick(Minecraft mc) throws Exception {
		if (phase == 0
				&& mc.getOverlay() == null
				&& mc.screen instanceof TitleScreen title
				&& ticks > 60) {
			exchange =
					mc.gameDirectory
							.toPath()
							.resolve("exchange-" + UUID.randomUUID())
							.toAbsolutePath();
			Files.createDirectories(exchange);
			for (var child : title.children())
				if (child instanceof Button button)
					require(
							!Set.of("menu.singleplayer", "menu.multiplayer", "menu.online")
									.contains(key(button)),
							"Native play buttons removed");
			press(title, "terra_character.start");
			require(
					mc.screen instanceof CharacterSelectionScreen,
					"Start opens character selection");
			host =
					person(
							UUID.randomUUID(),
							"LAN Host " + UUID.randomUUID().toString().substring(0, 8),
							CharacterDifficulty.SOFTCORE);
			ClientProfiles.store().create(host);
			mc.setScreen(new CharacterSelectionScreen(title));
			selectCharacter(mc.screen, host.name());
			press(mc.screen, "terra_character.play");
			require(
					mc.screen instanceof PlayDestinationScreen,
					"Character selected before destination");
			require(
					mc.screen.children().stream()
									.filter(
											w ->
													w instanceof Button bt
															&& Set.of(
																			"terra_character.destination.local",
																			"terra_character.destination.remote")
																	.contains(key(bt)))
									.count()
							== 2,
					"Both destinations available");
			Screenshot.grab(
					mc.gameDirectory, "play-destination.png", mc.getMainRenderTarget(), c -> {});
			passed.add("unified menu and character-first destinations");
			mc.options.renderDistance().set(2);
			mc.options.simulationDistance().set(5);
			mc.options.pauseOnLostFocus = false;
			var world =
					new WorldProfile(
							UUID.randomUUID(),
							"MP smoke",
							new WorldSettings("12345", "minecraft:flat", WorldDifficulty.NORMAL));
			Path folder = ClientProfiles.saves().resolve("mp-" + UUID.randomUUID());
			CharacterSession.arm(ClientProfiles.store(), host.id(), folder, world);
			var rules = new GameRules();
			rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
			mc.createWorldOpenFlows()
					.createFreshLevel(
							folder.getFileName().toString(),
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
			phase = 1;
			wait = 0;
		} else if (phase == 1
				&& mc.player != null
				&& ClientState.profile() != null
				&& ++wait > 30
				&& work == null) {
			var server = mc.getSingleplayerServer();
			hostAccount = mc.player.getUUID();
			work =
					CompletableFuture.runAsync(
							() -> {
								var p = server.getPlayerList().getPlayer(hostAccount);
								p.getInventory().add(new ItemStack(Items.DIAMOND, 3));
								server.setUsesAuthentication(false);
							},
							server);
			phase = 2;
		} else if (phase == 2 && work == null) {
			int port;
			try (var socket = new ServerSocket(0)) {
				port = socket.getLocalPort();
			}
			require(
					mc.getSingleplayerServer().publishServer(GameType.SURVIVAL, false, port),
					"LAN publication succeeds");
			require(
					!Files.exists(SessionCheckpoint.marker(ClientProfiles.store(), host.id())),
					"Sharing commits offline checkpoint");
			Files.writeString(exchange.resolve("host-id"), host.id().toString());
			Files.writeString(exchange.resolve("host-account"), hostAccount.toString());
			Files.writeString(exchange.resolve("port"), Integer.toString(port));
			spawnGuest(mc);
			phase = 3;
		} else if (phase == 3 && work == null) {
			if (guest != null && !guest.isAlive() && !Files.exists(exchange.resolve("guest-pass")))
				throw new AssertionError("Guest process exited: " + guest.exitValue());
			if (Files.exists(exchange.resolve("guest-failed")))
				throw new AssertionError(Files.readString(exchange.resolve("guest-failed")));
			if (lastProcessed == 8
					&& guestAccount != null
					&& MultiplayerClient.avatar(guestAccount) != null
					&& MultiplayerClient.avatar(guestAccount).appearance().paint().pixel(40, 8)
							== 0xFF11DD66)
				Files.writeString(exchange.resolve("avatar-checked"), "PASS");
			if (Files.exists(exchange.resolve("stage"))) {
				int current = Integer.parseInt(Files.readString(exchange.resolve("stage")));
				if (current != lastProcessed && current != 5 && current != 9) {
					var server = mc.getSingleplayerServer();
					var players = server.getPlayerList().getPlayers();
					var remote =
							players.stream()
									.filter(
											p ->
													!p.getUUID().equals(hostAccount)
															&& CharacterSession.of(p) != null)
									.findFirst()
									.orElse(null);
					if (remote != null && remote.tickCount > 25) {
						lastProcessed = current;
						guestAccount = remote.getUUID();
						work =
								CompletableFuture.runAsync(
										() -> {
											try {
												verify(remote, current);
												Files.writeString(
														exchange.resolve("checked-" + current),
														"PASS");
											} catch (Exception ex) {
												throw new RuntimeException(ex);
											}
										},
										server);
					}
				}
			}
			if (Files.exists(exchange.resolve("guest-pass"))) {
				var server = mc.getSingleplayerServer();
				work =
						CompletableFuture.runAsync(
								() -> {
									var p = server.getPlayerList().getPlayer(hostAccount);
									require(
											p.getInventory().countItem(Items.DIAMOND) == 3,
											"Guest never changes host character");
									try {
										var store =
												RemoteCharacters.store(
														server.getServerDirectory(), guestAccount);
										require(
												testedCharacters.size() == 4
														&& store.list().stream()
																		.filter(
																				profile ->
																						testedCharacters
																								.contains(
																										profile
																												.id()))
																		.count()
																== 4,
												"This run's four server characters persist");
									} catch (Exception ex) {
										throw new RuntimeException(ex);
									}
								},
								server);
				phase = 4;
			}
		} else if (phase == 4 && work == null) {
			passed.add(
					"TCP configuration handshake; A/B/A reconnect isolation; same UUID on host and"
						+ " guest; client-authoritative portable data; multiplayer/solo/multiplayer"
						+ " round trip; final local save; shared stats; skins;"
						+ " softcore/mediumcore/hardcore; dead reconnect rejection");
			Files.writeString(
					mc.gameDirectory.toPath().resolve("mp-result.txt"),
					"PASS\n" + String.join("\n", passed) + "\n");
			TerraCharacter.LOGGER.info("MULTIPLAYER SMOKE ALL PASSED");
			finished = true;
			mc.level.disconnect();
			mc.disconnect();
			mc.stop();
		}
	}

	private static void spawnGuest(Minecraft mc) throws Exception {
		Path dir =
				mc.gameDirectory
						.toPath()
						.toAbsolutePath()
						.normalize()
						.resolveSibling("multiplayer-smoke-guest");
		Files.createDirectories(dir);
		var command = new ArrayList<String>();
		command.add(Path.of(System.getProperty("java.home"), "bin/java.exe").toString());
		Path extra = exchange.resolve("guest-classpath.txt");
		Files.writeString(
				extra,
				"-cp\n"
						+ argQuote(System.getProperty("java.class.path"))
						+ "\n"
						+ argQuote("-Dfml.modFolders=" + System.getProperty("fml.modFolders"))
						+ "\n"
						+ argQuote("-Dterra_character.mp_exchange=" + exchange)
						+ "\n");
		command.add("@" + extra);
		Path launch = Path.of(System.getProperty("terra_character.mp_launch"));
		for (String name : List.of("clientRunVmArgs.txt", "clientRunProgramArgs.txt")) {
			String content =
					Files.readString(launch.resolve(name))
							.replace(
									"-Dterra_character.mp_smoke=host",
									"-Dterra_character.mp_smoke=guest");
			if (name.contains("ProgramArgs"))
				content =
						content.replace(
								"\nConfluence",
								"\nSmokeGuest" + host.id().toString().substring(0, 6));
			Path copy = exchange.resolve("guest-" + name);
			Files.writeString(copy, content);
			command.add("@" + copy);
		}
		guest =
				new ProcessBuilder(command)
						.directory(dir.toFile())
						.redirectErrorStream(true)
						.redirectOutput(dir.resolve("guest-console.log").toFile())
						.start();
	}

	private static String argQuote(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static void guestTick(Minecraft mc) throws Exception {
		if (phase == 0
				&& mc.getOverlay() == null
				&& mc.screen instanceof TitleScreen
				&& ticks > 60) {
			exchange = Path.of(System.getProperty("terra_character.mp_exchange"));
			hostAccount = UUID.fromString(Files.readString(exchange.resolve("host-account")));
			a =
					person(
							UUID.fromString(Files.readString(exchange.resolve("host-id"))),
							"Guest A",
							CharacterDifficulty.SOFTCORE);
			b = person(UUID.randomUUID(), "Guest B", CharacterDifficulty.SOFTCORE);
			hard = person(UUID.randomUUID(), "Guest Hard", CharacterDifficulty.HARDCORE);
			medium = person(UUID.randomUUID(), "Guest Medium", CharacterDifficulty.MEDIUMCORE);
			for (var profile : List.of(a, b, hard, medium)) ClientProfiles.store().create(profile);
			try (var lock = ClientProfiles.store().lock(a.id())) {
				var d = ClientProfiles.store().read(a.id());
				byte[] noise = new byte[100000];
				new Random(73).nextBytes(noise);
				d.getCompound("Journey").putByteArray("SmokeLargeTransfer", noise);
				ClientProfiles.store().commit(a.id(), d, d.getLong("Revision"));
			}
			mc.options.renderDistance().set(2);
			mc.options.simulationDistance().set(5);
			mc.options.pauseOnLostFocus = false;
			connect(mc);
			phase = 1;
			wait = 0;
		} else if (phase == 1) {
			if (stage != 5 && stage != 9 && mc.screen instanceof DisconnectedScreen)
				throw new AssertionError("Unexpected server rejection at stage " + stage);
			if (stage == 5 || stage == 9) {
				if (mc.screen instanceof DisconnectedScreen) {
					require(mc.player == null, "Rejected connection never enters play");
					stage++;
					phase = 2;
					wait = 0;
				}
			} else if (mc.player != null
					&& ClientState.profile() != null
					&& Files.exists(exchange.resolve("checked-" + stage))
					&& (stage != 8 || Files.exists(exchange.resolve("avatar-checked")))
					&& ++wait > 30) {
				var profile = ClientState.profile();
				require(
						profile.id().equals(MultiplayerClient.getSelection().id()),
						"Selected character returned by server");
				require(MultiplayerClient.avatar(hostAccount) != null, "Host avatar synchronized");
				if (stage == 1 || stage == 3 || stage == 8)
					require(
							mc.player.getInventory().countItem(Items.DIAMOND)
									== (stage == 8 ? 19 : 7),
							"A inventory synchronized from portable data");
				if (stage == 3)
					require(
							profile.difficulty() == CharacterDifficulty.SOFTCORE,
							"Local character difficulty restored");
				if (stage == 4)
					require(
							profile.status() == CharacterProfile.Status.DEAD,
							"Hardcore death synchronized");
				if (stage == 6)
					require(
							mc.player.getInventory().countItem(Items.IRON_INGOT) == 0,
							"Mediumcore drops survive respawn");
				if (stage == 7)
					require(
							mc.player.getInventory().countItem(Items.GOLD_INGOT) == 11,
							"B inventory restores");
				if (phase != 12) {
					if (stage == 1) {
						phase = 13;
						mc.setScreen(new PauseScreen(true));
						press(mc.screen, "menu.disconnect");
						return;
					}
					phase = 12;
					require(
							MultiplayerClient.leave(
									() -> {
										mc.level.disconnect();
										mc.disconnect();
										mc.setScreen(new TitleScreen());
										stage++;
										phase = 2;
										wait = 0;
									}),
							"Remote disconnect waits for durable local character save");
				}
			}
		} else if (phase == 2 && ++wait > 30) {
			if (stage == 10) {
				Files.writeString(exchange.resolve("guest-pass"), "PASS");
				Files.writeString(
						mc.gameDirectory.toPath().resolve("mp-result.txt"),
						"PASS\n"
							+ "7 successful joins; multiplayer -> solo -> multiplayer"
							+ " client-authoritative assets and world-local isolation; final-save"
							+ " handshake; dead and missing selection rejection\n");
				finished = true;
				mc.stop();
			} else if (stage == 8 && !soloDone) {
				var world =
						new WorldProfile(
								UUID.randomUUID(),
								"Portable solo",
								new WorldSettings(
										"12345", "minecraft:flat", WorldDifficulty.NORMAL));
				Path folder = ClientProfiles.saves().resolve("solo-roundtrip-" + UUID.randomUUID());
				CharacterSession.arm(ClientProfiles.store(), a.id(), folder, world);
				var rules = new GameRules();
				rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
				mc.createWorldOpenFlows()
						.createFreshLevel(
								folder.getFileName().toString(),
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
				phase = 10;
				wait = 0;
			} else {
				connect(mc);
				phase = 1;
				wait = 0;
			}
		} else if (phase == 13 && mc.player == null && mc.getConnection() == null) {
			mc.setScreen(new TitleScreen());
			stage++;
			phase = 2;
			wait = 0;
		} else if (phase == 10
				&& mc.player != null
				&& ClientState.profile() != null
				&& ++wait > 30) {
			var server = mc.getSingleplayerServer();
			work =
					CompletableFuture.runAsync(
							() -> {
								try {
									var p = server.getPlayerList().getPlayers().getFirst();
									require(
											p.getInventory().countItem(Items.DIAMOND) == 7
													&& p.totalExperience == 123,
											"Multiplayer assets return to solo");
									CharacterSmokeTest.checkExtension(
											p, "quests", "AnglerQuestCount", 9);
									CharacterSmokeTest.checkExtension(
											p, "world_quests", "CompletedAnglerQuestDay", -1L);
									CharacterSmokeTest.checkEquipment(p, 1);
									for (var stack : p.getInventory().items)
										if (stack.is(Items.DIAMOND)) stack.setCount(19);
									p.giveExperiencePoints(200);
									CharacterSmokeTest.extension(
											p, "ever_beneficial", "lifeCrystals", 4);
									CharacterSmokeTest.extension(
											p, "quests", "AnglerQuestCount", 13);
									CharacterSmokeTest.extension(
											p, "world_quests", "CompletedAnglerQuestDay", 999L);
									p.teleportTo(120, -60, 120);
									CharacterSession.of(p).save(p);
								} catch (Exception ex) {
									throw new RuntimeException(ex);
								}
							},
							server);
			phase = 11;
		} else if (phase == 11 && work == null) {
			mc.level.disconnect();
			mc.disconnect();
			mc.setScreen(new TitleScreen());
			soloDone = true;
			phase = 2;
			wait = 0;
		}
	}

	private static void connect(Minecraft mc) throws Exception {
		mc.setScreen(new TitleScreen());
		CharacterProfile selected =
				switch (stage) {
					case 1, 3, 8 -> a;
					case 2, 7 -> b;
					case 4, 5 -> hard;
					case 6 -> medium;
					default -> null;
				};

		if (stage == 8) {
			int[] paint = new int[SkinPaint.PIXELS];
			paint[8 * 64 + 8] = 0xFF3366FF;
			paint[8 * 64 + 40] = 0xFF11DD66;
			selected =
					new CharacterProfile(
							a.id(),
							"Guest A painted",
							a.difficulty(),
							new CharacterAppearance(
									a.appearance().bodyStyle(),
									a.appearance().hairStyle(),
									a.appearance().colors(),
									new SkinPaint(paint)),
							a.status());
		}
		if (stage == 8) ClientProfiles.store().edit(selected);
		MultiplayerClient.setSelection(selected);
		Path stageDraft = exchange.resolve("stage-draft");
		Files.writeString(stageDraft, Integer.toString(stage));
		Files.move(
				stageDraft,
				exchange.resolve("stage"),
				StandardCopyOption.ATOMIC_MOVE,
				StandardCopyOption.REPLACE_EXISTING);
		String address = "127.0.0.1:" + Files.readString(exchange.resolve("port"));
		ConnectScreen.startConnecting(
				new TitleScreen(),
				mc,
				ServerAddress.parseString(address),
				new ServerData("Smoke", address, ServerData.Type.OTHER),
				false,
				null);
	}

	private static void verify(ServerPlayer p, int current) throws Exception {
		var session = CharacterSession.of(p);
		require(session != null && !session.failed(), "Healthy remote session");
		testedCharacters.add(session.profile().id());
		require(!p.getUUID().equals(hostAccount), "Guest authentication is independent");
		int diamonds = p.getInventory().countItem(Items.DIAMOND),
				gold = p.getInventory().countItem(Items.GOLD_INGOT);
		if (current == 1) {
			CharacterSmokeTest.extension(p, "ever_beneficial", "lifeCrystals", 3);
			CharacterSmokeTest.extension(p, "mana", "stars", 3);
			CharacterSmokeTest.extension(p, "quests", "AnglerQuestCount", 9);
			CharacterSmokeTest.extension(p, "world_quests", "CompletedAnglerQuestDay", 101L);
			CharacterSmokeTest.seedEquipment(p);
			var mana = CharacterSmokeTest.adapter("mana");
			if (mana != null) {
				var d = mana.capture(p);
				d.putFloat("currentMana", 12.75F);
				mana.restore(p, d, 1);
				require(
						mana.capture(p).getFloat("currentMana") == 12.75F,
						"Remote fractional mana preserved");
			}
		} else if (current == 2) {
			CharacterSmokeTest.checkExtension(p, "ever_beneficial", "lifeCrystals", 0);
			CharacterSmokeTest.checkExtension(p, "quests", "AnglerQuestCount", 0);
			CharacterSmokeTest.checkEquipment(p, 0);
		} else if (current == 3 || current == 8) {
			CharacterSmokeTest.checkExtension(
					p, "ever_beneficial", "lifeCrystals", current == 8 ? 4 : 3);
			CharacterSmokeTest.checkExtension(p, "mana", "stars", 3);
			CharacterSmokeTest.checkExtension(
					p, "quests", "AnglerQuestCount", current == 8 ? 13 : 9);
			CharacterSmokeTest.checkExtension(p, "world_quests", "CompletedAnglerQuestDay", 101L);
			CharacterSmokeTest.checkEquipment(p, 1);
		} else if (current == 6) CharacterSmokeTest.seedEquipment(p);
		switch (current) {
			case 1 -> {
				require(
						session.journey().getByteArray("SmokeLargeTransfer").length == 100000,
						"Large multipart character upload succeeds");
				require(
						session.profile().id().equals(host.id()),
						"Host and guest intentionally share character UUID");
				require(diamonds == 0, "Guest cannot inherit host inventory");
				p.getInventory().add(new ItemStack(Items.DIAMOND, 7));
				p.giveExperiencePoints(123);
				p.awardStat(Stats.JUMP, 5);
			}
			case 2 -> {
				require(diamonds == 0 && p.totalExperience == 0, "B does not inherit A");
				require(
						p.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)) == 0,
						"B statistics isolated");
				p.getInventory().add(new ItemStack(Items.GOLD_INGOT, 11));
				p.containerMenu.setCarried(new ItemStack(Items.EMERALD, 4));
			}
			case 3 -> {
				require(
						diamonds == 7 && gold == 0 && p.totalExperience == 123,
						"A restores portable assets");
				require(
						p.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)) == 5,
						"A statistics restore");
				require(
						session.profile().difficulty() == CharacterDifficulty.SOFTCORE,
						"Local difficulty retained");
				p.hurt(p.damageSources().genericKill(), 10000);
				var r = p.server.getPlayerList().respawn(p, false, Entity.RemovalReason.KILLED);
				require(
						r.getInventory().countItem(Items.DIAMOND) == 7,
						"Remote softcore respawn retains inventory");
				r.connection.player = r;
				p = r;
			}
			case 4 -> {
				p.hurt(p.damageSources().genericKill(), 10000);
				require(
						session.profile().status() == CharacterProfile.Status.DEAD,
						"Remote hardcore tombstone saved");
			}
			case 6 -> {
				p.getInventory().add(new ItemStack(Items.IRON_INGOT, 13));
				p.hurt(p.damageSources().genericKill(), 10000);
				var r = p.server.getPlayerList().respawn(p, false, Entity.RemovalReason.KILLED);
				require(
						r.getInventory().countItem(Items.IRON_INGOT) == 0,
						"Remote mediumcore drops inventory");
				r.connection.player = r;
				p = r;
			}
			case 7 -> {
				require(gold == 11 && diamonds == 0, "B restores own assets");
				require(
						p.getInventory().countItem(Items.EMERALD) == 4,
						"Cursor items returned before final portable capture");
			}
			case 8 -> {
				require(
						diamonds == 19 && p.totalExperience == 323,
						"New solo assets replace stale server assets");
				require(
						p.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)) == 5,
						"Server world statistics remain local");
				require(
						Math.abs(p.getX() - 120) > 1,
						"Solo position never overwrites server position");
				require(
						!session.profile().appearance().paint().isEmpty(),
						"Painted double-layer skin metadata synchronized");
			}
			default -> throw new AssertionError("Unexpected stage " + current);
		}
		if (current == 3) CharacterSmokeTest.checkEquipment(p, 1);
		if (current == 6) CharacterSmokeTest.checkEquipment(p, 0);
		session.save(p);
		p.inventoryMenu.broadcastChanges();
		TerraCharacter.LOGGER.info("MULTIPLAYER SMOKE PASS stage {}", current);
	}

	private static String key(Button button) {
		return button.getMessage().getContents() instanceof TranslatableContents c
				? c.getKey()
				: "";
	}

	private static void selectCharacter(Screen screen, String name) {
		for (int page = 0; page < 1000; page++) {
			var card =
					screen.children().stream()
							.filter(
									w ->
											w instanceof AbstractButton button
													&& button.getMessage().getString().equals(name))
							.map(AbstractButton.class::cast)
							.findFirst();
			if (card.isPresent()) {
				card.get().onPress();
				return;
			}
			var next =
					screen.children().stream()
							.filter(
									w ->
											w instanceof Button button
													&& button.active
													&& button.getMessage().getString().equals("▶"))
							.map(Button.class::cast)
							.findFirst()
							.orElseThrow(
									() -> new AssertionError("Character card not found: " + name));
			next.onPress();
		}
		throw new AssertionError("Character pagination did not finish");
	}

	private static void press(Screen screen, String key) {
		((Button)
						screen.children().stream()
								.filter(w -> w instanceof Button b && key(b).equals(key))
								.findFirst()
								.orElseThrow())
				.onPress();
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
