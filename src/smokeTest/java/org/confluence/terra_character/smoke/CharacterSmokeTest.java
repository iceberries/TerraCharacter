package org.confluence.terra_character.smoke;

import net.minecraft.SharedConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.integration.*;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.api.session.LaunchSelection;
import org.confluence.terra_character.client.*;
import org.confluence.terra_character.integration.*;
import org.confluence.terra_character.runtime.*;
import org.confluence.terra_character.service.*;
import org.confluence.terra_character.storage.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Development-only integration runner. Not compiled into either distributable JAR. */
@EventBusSubscriber(modid = TerraCharacter.MODID, value = Dist.CLIENT)
public final class CharacterSmokeTest {
	private static final List<CharacterProfile> PEOPLE = new ArrayList<>();
	private static final Map<String, WorldProfile> WORLDS = new HashMap<>();
	private static final String SUITE =
			"smoke-" + UUID.randomUUID().toString().substring(0, 8) + "-";
	private static final int[] PEOPLE_ORDER = {0, 1, 0, 0, 1, 0, 2, 3, 4, 0};
	private static final String[] WORLD_ORDER = {"X", "X", "Y", "X", "Y", "X", "X", "X", "T", "X"};
	private static int phase, ticks, step = -1, waitTicks;
	private static CompletableFuture<Void> check;
	private static boolean busy, finished;
	private static boolean nativeCreated;
	private static String nativeFolder;
	private static final List<String> PASSED = new ArrayList<>();
	private static final long START = System.currentTimeMillis();

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		if (!Boolean.getBoolean("terra_character.smoke") || finished) return;
		var mc = Minecraft.getInstance();
		ticks++;
		try {
			if (System.currentTimeMillis() - START > 900_000)
				throw new AssertionError(
						"Smoke test timed out at step " + step + " screen " + mc.screen);
			if (phase == 0 && mc.getOverlay() == null && ticks > 60) {
				Files.writeString(
						mc.gameDirectory.toPath().resolve("smoke-result.txt"),
						"RUNNING " + SUITE + "\n");
				for (var difficulty :
						List.of(
								CharacterDifficulty.SOFTCORE,
								CharacterDifficulty.SOFTCORE,
								CharacterDifficulty.MEDIUMCORE,
								CharacterDifficulty.HARDCORE,
								CharacterDifficulty.JOURNEY)) {
					var p =
							new CharacterProfile(
									UUID.randomUUID(),
									"Smoke " + PEOPLE.size(),
									difficulty,
									ClientProfiles.defaults(),
									CharacterProfile.Status.ACTIVE);
					ClientProfiles.store().create(p);
					PEOPLE.add(p);
				}
				mc.options.renderDistance().set(2);
				mc.options.simulationDistance().set(5);
				mc.setScreen(new CharacterEditorScreen(new TitleScreen(), PEOPLE.getFirst(), null));
				phase = 1;
				waitTicks = 0;
			} else if (phase == 1 && ++waitTicks > 30) {
				Screenshot.grab(
						mc.gameDirectory,
						"character-editor.png",
						mc.getMainRenderTarget(),
						c -> {});
				mc.setScreen(
						new Screen(Component.literal("Preview angles")) {
							@Override
							public void render(GuiGraphics g, int x, int y, float partial) {
								super.render(g, x, y, partial);
								int index = 0;
								for (int angle : new int[] {0, 90, 180, 270}) {
									int px = width * (++index) / 5;
									CharacterSkins.preview(
											g,
											ClientProfiles.defaults(),
											px,
											height - 60,
											60,
											angle);
									g.drawCenteredString(
											font,
											Integer.toString(angle),
											px,
											height - 35,
											0xFFFFFF);
								}
							}
						});
				phase = 2;
				waitTicks = 0;
			} else if (phase == 2 && ++waitTicks == 15) {
				Screenshot.grab(
						mc.gameDirectory, "preview-angles.png", mc.getMainRenderTarget(), c -> {});
				mc.setScreen(new CharacterSelectionScreen(new TitleScreen()));
			} else if (phase == 2 && waitTicks > 30) {
				Screenshot.grab(
						mc.gameDirectory, "character-list.png", mc.getMainRenderTarget(), c -> {});
				phase = 3;
				next(mc);
			} else if (phase == 3) {
				if (busy && check != null && check.isDone()) {
					check.join();
					PASSED.add(
							"step " + step + ": " + PEOPLE_ORDER[step] + " / " + WORLD_ORDER[step]);
					TerraCharacter.LOGGER.info("CHARACTER SMOKE PASS {}", PASSED.getLast());
					check = null;
					busy = false;
					mc.level.disconnect();
					mc.disconnect();
					mc.setScreen(new TitleScreen());
					waitTicks = 0;
					phase = 4;
				} else if (!busy
						&& mc.player != null
						&& ClientState.profile() != null
						&& ++waitTicks > 40) {
					var server = mc.getSingleplayerServer();
					int current = step;
					check =
							CompletableFuture.runAsync(
									() -> {
										try {
											verify(
													server.getPlayerList().getPlayers().getFirst(),
													current);
										} catch (Exception ex) {
											throw new RuntimeException(ex);
										}
									},
									server);
					busy = true;
				}
			} else if (phase == 4
					&& mc.getSingleplayerServer() == null
					&& CharacterSession.current() == null
					&& ++waitTicks > 10) {
				if (step + 1 == PEOPLE_ORDER.length && !nativeCreated) {
					ClientProfiles.setSelected(PEOPLE.getFirst().id());
					ClientProfiles.clearWorldOptions();
					if (ModList.get().isLoaded("confluence"))
						ClientProfiles.putWorldOptions(
								Map.of(
										"terra_character_confluence:world_difficulty",
										"master",
										"terra_character_confluence:evil",
										"crimson"));
					CreateWorldScreen.openFresh(mc, new TitleScreen());
					phase = 5;
					waitTicks = 0;
				} else if (step + 1 == PEOPLE_ORDER.length) {
					migrateFixture();
					var hard = ClientProfiles.store().find(PEOPLE.get(3).id()).orElseThrow();
					require(
							hard.status() == CharacterProfile.Status.DEAD,
							"Hardcore death survives shutdown");
					try {
						new LaunchSelection(hard, WORLDS.get("Y"));
						throw new AssertionError("Dead hardcore was selectable");
					} catch (IllegalArgumentException expected) {
					}
					for (var p : PEOPLE)
						require(
								!Files.exists(
										SessionCheckpoint.marker(ClientProfiles.store(), p.id())),
								"Clean shutdown clears checkpoint");
					Files.writeString(
							mc.gameDirectory.toPath().resolve("smoke-result.txt"),
							"PASS\n"
									+ String.join("\n", PASSED)
									+ "\n"
									+ "import idempotence and source backup\n"
									+ "hardcore reload gate\n"
									+ "clean checkpoints\n");
					finished = true;
					mc.setScreen(new CharacterSelectionScreen(new TitleScreen()));
					Screenshot.grab(
							mc.gameDirectory, "smoke-final.png", mc.getMainRenderTarget(), c -> {});
					TerraCharacter.LOGGER.info("CHARACTER SMOKE ALL PASSED");
					mc.stop();
				} else {
					phase = 3;
					next(mc);
				}
			} else if (phase == 5
					&& mc.screen instanceof CreateWorldScreen creation
					&& ++waitTicks > 30) {
				var state = creation.getUiState();
				state.setName("Native creation " + UUID.randomUUID().toString().substring(0, 8));
				state.getNormalPresetList().stream()
						.filter(e -> e.preset() != null && e.preset().is(WorldPresets.FLAT))
						.findFirst()
						.ifPresent(state::setWorldType);
				state.setSeed("for the worthy");
				state.setDifficulty(Difficulty.HARD);
				state.setGameMode(WorldCreationUiState.SelectedGameMode.HARDCORE);
				nativeFolder = state.getTargetFolder();
				Screenshot.grab(
						mc.gameDirectory, "native-create.png", mc.getMainRenderTarget(), c -> {});
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
								.findFirst()
								.orElseThrow();
				((Button) create).onPress();
				phase = 6;
				waitTicks = 0;
			} else if (phase == 6
					&& mc.player != null
					&& ClientState.profile() != null
					&& ++waitTicks > 30) {
				var server = mc.getSingleplayerServer();
				check =
						CompletableFuture.runAsync(
								() -> {
									var p = server.getPlayerList().getPlayers().getFirst();
									var session = CharacterSession.of(p);
									require(
											session != null && !session.failed(),
											"Native create binds character");
									require(
											!server.isHardcore(),
											"Native hardcore toggle cannot override character"
													+ " policy");
									require(
											session.world()
													.settings()
													.seed()
													.equals("for the worthy"),
											"Raw secret seed preserved");
									require(
											session.world()
													.settings()
													.preset()
													.equals("minecraft:flat"),
											"Native preset preserved");
									if (adapter("ever_beneficial") != null) {
										require(
												Math.abs(
																p.serverLevel()
																				.getCurrentDifficultyAt(
																						p
																								.blockPosition())
																				.getEffectiveDifficulty()
																		- 3.25F)
														< 0.001F,
												"Master + worthy uses existing Legendary"
														+ " threshold");
										try {
											var type =
													Class.forName(
															"org.confluence.mod.mixed.IWorldOptions");
											long flag =
													((Number)
																	type.getMethod(
																					"confluence$getSecretFlag")
																			.invoke(
																					server.getWorldData()
																							.worldGenOptions()))
															.longValue();
											require(
													(flag & 3) == 2,
													"Crimson is exclusive after seed parsing");
										} catch (ReflectiveOperationException ex) {
											throw new RuntimeException(ex);
										}
									}
								},
								server);
				phase = 7;
			} else if (phase == 7 && check.isDone()) {
				check.join();
				PASSED.add("native UI creation, raw seed, preset and world difficulty");
				nativeCreated = true;
				check = null;
				mc.level.disconnect();
				mc.disconnect();
				mc.setScreen(new TitleScreen());
				phase = 4;
				waitTicks = 0;
			}
		} catch (Throwable ex) {
			finished = true;
			TerraCharacter.LOGGER.error("CHARACTER SMOKE FAILED at step " + step, ex);
			try {
				Files.writeString(
						mc.gameDirectory.toPath().resolve("smoke-result.txt"),
						"FAIL step " + step + "\n" + ex);
			} catch (Exception ignored) {
			}
			if (mc.level != null) {
				mc.level.disconnect();
				mc.disconnect();
			}
			mc.stop();
		}
	}

	private static void next(Minecraft mc) throws Exception {
		step++;
		waitTicks = 0;
		var person = PEOPLE.get(PEOPLE_ORDER[step]);
		String name = WORLD_ORDER[step];
		var world =
				WORLDS.computeIfAbsent(
						name,
						n ->
								new WorldProfile(
										UUID.randomUUID(),
										"Smoke " + n,
										new WorldSettings(
												"12345",
												"minecraft:flat",
												n.equals("T")
														? WorldDifficulty.JOURNEY
														: WorldDifficulty.NORMAL,
												options())));
		Path folder = ClientProfiles.saves().resolve(SUITE + name);
		ClientProfiles.setSelected(person.id());
		CharacterSession.arm(ClientProfiles.store(), person.id(), folder, world);
		if (Files.exists(folder.resolve("level.dat")))
			mc.createWorldOpenFlows()
					.openWorld(
							folder.getFileName().toString(),
							() -> {
								throw new AssertionError("Failed to open smoke world");
							});
		else {
			var rules = new GameRules();
			rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
			rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
			rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
			var settings =
					new LevelSettings(
							world.name(),
							GameType.SURVIVAL,
							false,
							Difficulty.NORMAL,
							false,
							rules,
							WorldDataConfiguration.DEFAULT);
			var event =
					new ConfigureWorldEvent(
							world.settings(),
							new WorldOptions(12345, false, false),
							Difficulty.NORMAL);
			NeoForge.EVENT_BUS.post(event);
			mc.createWorldOpenFlows()
					.createFreshLevel(
							folder.getFileName().toString(),
							settings,
							event.options(),
							r ->
									r.registryOrThrow(Registries.WORLD_PRESET)
											.getHolderOrThrow(WorldPresets.FLAT)
											.value()
											.createWorldDimensions(),
							new TitleScreen());
		}
	}

	private static Map<String, String> options() {
		return ModList.get().isLoaded("confluence")
				? Map.of(
						"terra_character_confluence:world_difficulty",
						"expert",
						"terra_character_confluence:evil",
						"corruption")
				: Map.of();
	}

	private static int count(ServerPlayer p, Item item) {
		return p.getInventory().countItem(item);
	}

	private static void verify(ServerPlayer p, int step) throws Exception {
		var session = CharacterSession.of(p);
		require(session != null && !session.failed(), "Healthy bound session");
		require(
				p.getUUID().equals(Minecraft.getInstance().getUser().getProfileId()),
				"Account identity preserved");
		require(!p.getUUID().equals(session.profile().id()), "Independent character UUID");
		p.getAbilities().invulnerable =
				true; // keep the test actor safe until the deliberate death tests
		if (step == 0) {
			var missing = new CompoundTag();
			missing.putString("Dimension", "missing_mod:gone");
			try {
				VanillaDataAdapter.validateReferences(p, new CompoundTag(), missing);
				throw new AssertionError("Missing dimension was accepted");
			} catch (IOException expected) {
			}
			var book = new CompoundTag();
			var recipes = new ListTag();
			recipes.add(StringTag.valueOf("missing_mod:gone"));
			book.put("recipes", recipes);
			var recipeState = new CompoundTag();
			recipeState.put("recipeBook", book);
			try {
				VanillaDataAdapter.validateReferences(p, recipeState, new CompoundTag());
				throw new AssertionError("Missing recipe was accepted");
			} catch (IOException expected) {
			}
			require(count(p, Items.DIAMOND) == 0, "Fresh inventory");
			p.getInventory().add(new ItemStack(Items.DIAMOND, 7));
			p.giveExperiencePoints(123);
			p.setRespawnPosition(Level.OVERWORLD, new BlockPos(3, 5, 4), 0, true, false);
			p.awardStat(Stats.JUMP, 5);
			p.serverLevel()
					.setBlock(new BlockPos(0, 90, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
			p.serverLevel().setDayTime(1111);
			extension(p, "ever_beneficial", "lifeCrystals", 3);
			extension(p, "mana", "stars", 3);
			extension(p, "world_quests", "CompletedAnglerQuestDay", 101L);
			extension(p, "quests", "AnglerQuestCount", 9);
			seedEquipment(p);
			var mana = adapter("mana");
			if (mana != null) {
				var state = mana.capture(p);
				state.putFloat("currentMana", 12.75F);
				mana.restore(p, state, 1);
				require(
						mana.capture(p).getFloat("currentMana") == 12.75F,
						"Fractional mana restored without truncation");
			}
			var bank = adapter("piggy_bank");
			if (bank != null) {
				var tag = new CompoundTag();
				var list = new ListTag();
				var item = (CompoundTag) new ItemStack(Items.DIAMOND, 23).save(p.registryAccess());
				item.putByte("Slot", (byte) 0);
				list.add(item);
				tag.put("Items", list);
				bank.restore(p, tag, 1);
			}
		} else if (step == 1) {
			require(
					count(p, Items.DIAMOND) == 0 && p.totalExperience == 0,
					"B does not inherit A assets");
			require(p.getRespawnPosition() == null, "B local spawn isolated");
			require(
					p.serverLevel().getBlockState(new BlockPos(0, 90, 0)).is(Blocks.DIAMOND_BLOCK),
					"World X block remains shared");
			checkExtension(p, "ever_beneficial", "lifeCrystals", 0);
			checkExtension(p, "quests", "AnglerQuestCount", 0);
			p.getInventory().add(new ItemStack(Items.GOLD_INGOT, 11));
			checkEquipment(p, 0);
			if (adapter("ever_beneficial") != null)
				require(p.getMaxHealth() == 20, "B does not inherit permanent health modifiers");
		} else if (step == 2) {
			require(
					count(p, Items.DIAMOND) == 7 && p.totalExperience == 123,
					"A assets travel to Y");
			require(p.getRespawnPosition() == null, "X spawn does not leak into Y");
			require(
					p.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)) == 0,
					"World-specific statistics");
			checkExtension(p, "ever_beneficial", "lifeCrystals", 3);
			checkExtension(p, "quests", "AnglerQuestCount", 9);
			checkExtension(p, "world_quests", "CompletedAnglerQuestDay", -1L);
			checkEquipment(p, 1);
			checkExtension(p, "mana", "stars", 3);
			if (adapter("ever_beneficial") != null)
				require(p.getMaxHealth() == 32, "Permanent health recomputed from crystals");
			p.getInventory().add(new ItemStack(Items.EMERALD, 2));
			p.setRespawnPosition(Level.OVERWORLD, new BlockPos(30, 5, 40), 0, true, false);
			p.serverLevel().setDayTime(2222);
			require(
					!p.serverLevel().getBlockState(new BlockPos(0, 90, 0)).is(Blocks.DIAMOND_BLOCK),
					"Y world blocks isolated");
			var bank = adapter("piggy_bank");
			if (bank != null)
				require(
						bank.capture(p)
										.getList("Items", Tag.TAG_COMPOUND)
										.getCompound(0)
										.getInt("count")
								== 23,
						"Private bank travels");
		} else if (step == 3) {
			require(
					count(p, Items.DIAMOND) == 7 && count(p, Items.EMERALD) == 2,
					"Return to X preserves portable inventory");
			require(new BlockPos(3, 5, 4).equals(p.getRespawnPosition()), "X spawn restored");
			require(p.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)) == 5, "X stats restored");
			checkExtension(p, "world_quests", "CompletedAnglerQuestDay", 101L);
			var before = adapter("quests");
			if (before != null) {
				Class<?> type =
						Class.forName("org.confluence.mod.common.attachment.PlayerSpecialData");
				var data = type.getMethod("of", Player.class).invoke(null, p);
				type.getMethod("flushArmorSetBonus", Player.class).invoke(data, p);
				checkExtension(p, "quests", "AnglerQuestCount", 9);
			}
		} else if (step == 4) {
			require(
					count(p, Items.GOLD_INGOT) == 11 && count(p, Items.DIAMOND) == 0,
					"B carries only B assets to Y");
			checkExtension(p, "quests", "AnglerQuestCount", 0);
		} else if (step == 5) {
			p.level().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, p.server);
			int xp = p.totalExperience;
			p.getAbilities().invulnerable = false;
			p.hurt(p.damageSources().genericKill(), 10000);
			require(count(p, Items.DIAMOND) == 7, "Softcore keeps inventory despite world rule");
			var respawn = p.server.getPlayerList().respawn(p, false, Entity.RemovalReason.KILLED);
			require(
					count(respawn, Items.DIAMOND) == 7 && respawn.totalExperience == xp,
					"Softcore respawn keeps XP and inventory");
			checkEquipment(respawn, 1);
			session.save(respawn);
			return;
		} else if (step == 6) {
			p.getInventory().clearContent();
			p.getInventory().add(new ItemStack(Items.IRON_INGOT, 13));
			p.giveExperiencePoints(90);
			p.level().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, p.server);
			p.getAbilities().invulnerable = false;
			seedEquipment(p);
			p.hurt(p.damageSources().genericKill(), 10000);
			require(
					count(p, Items.IRON_INGOT) == 0,
					"Mediumcore drops despite world keepInventory");
			var respawn = p.server.getPlayerList().respawn(p, false, Entity.RemovalReason.KILLED);
			require(
					count(respawn, Items.IRON_INGOT) == 0 && respawn.totalExperience == 0,
					"Mediumcore respawn does not duplicate items");
			checkEquipment(respawn, 0);
			session.save(respawn);
			return;
		} else if (step == 7) {
			p.getAbilities().invulnerable = false;
			p.hurt(p.damageSources().genericKill(), 10000);
			require(
					session.profile().status() == CharacterProfile.Status.DEAD,
					"Hardcore tombstone committed");
		} else if (step == 8) {
			p.getAbilities().invulnerable = false;
			JourneyController.handle(p, "god", "");
			JourneyController.handle(p, "flight", "");
			require(
					p.getAbilities().mayfly && p.getAbilities().invulnerable,
					"Journey abilities server-authorized");
			p.getInventory().clearContent();
			p.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
			p.getInventory().selected = 0;
			JourneyController.handle(p, "research", "");
			require(count(p, Items.STONE) == 0, "Research consumes input");
			JourneyController.handle(p, "duplicate", "minecraft:stone");
			require(count(p, Items.STONE) == 64, "Researched item duplicates");
			JourneyController.handle(p, "duplicate", "minecraft:diamond");
			require(count(p, Items.DIAMOND) == 0, "Unresearched item refused");
			JourneyController.handle(p, "noon", "");
			require(p.serverLevel().getDayTime() % 24000 == 6000, "Journey world controls");
		} else if (step == 9) {
			require(
					count(p, Items.DIAMOND) == 7 && count(p, Items.EMERALD) == 2,
					"A intact after other characters die");
			JourneyController.handle(p, "god", "");
			require(!session.journey().getBoolean("God"), "Non-journey action ignored");
		}
		session.save(p);
	}

	static void seedEquipment(ServerPlayer player) {
		var extra = adapter("extra_inventory");
		if (extra == null) return;
		var state = extra.capture(player);
		state.getList("Ammo", Tag.TAG_COMPOUND)
				.set(0, new ItemStack(Items.ARROW, 6).save(player.registryAccess()));
		var dyes = new ListTag();
		dyes.add(new ItemStack(Items.RED_DYE, 2).save(player.registryAccess()));
		state.put("AccessoryDye", dyes);
		extra.restore(player, state, 1);
		var curios = adapter("curios");
		var tag = curios.capture(player);
		var slots = tag.getList("Curios", Tag.TAG_COMPOUND);
		var slot =
				slots.stream()
						.map(e -> (CompoundTag) e)
						.filter(e -> e.getString("Identifier").equals("accessory"))
						.findFirst()
						.orElseThrow(
								() ->
										new AssertionError(
												"Expected Confluence accessory slot: " + tag));
		var stack = new CompoundTag();
		stack.putString("id", "terra_curio:lucky_horseshoe");
		stack.putInt("count", 1);
		stack.putInt("Slot", 0);
		var items = new ListTag();
		items.add(stack);
		slot.getCompound("StacksHandler").getCompound("Stacks").put("Items", items);
		curios.restore(player, tag, 1);
		curios.afterRestore(player);
	}

	private static int itemCount(Tag tag, String id) {
		if (tag instanceof CompoundTag compound) {
			int count = compound.getString("id").equals(id) ? compound.getInt("count") : 0;
			for (String key : compound.getAllKeys()) count += itemCount(compound.get(key), id);
			return count;
		}
		if (tag instanceof ListTag list) {
			int count = 0;
			for (Tag entry : list) count += itemCount(entry, id);
			return count;
		}
		return 0;
	}

	static void checkEquipment(ServerPlayer player, int multiplier) {
		var extra = adapter("extra_inventory");
		if (extra == null) return;
		var tag = extra.capture(player);
		require(
				itemCount(tag, "minecraft:arrow") == 6 * multiplier,
				"Extended ammo belongs to character");
		require(
				itemCount(tag, "minecraft:red_dye") == 2 * multiplier,
				"Accessory dye decoder preserves items");
		require(
				itemCount(adapter("curios").capture(player), "terra_curio:lucky_horseshoe")
						== multiplier,
				"Curios equipment belongs to character");
	}

	static CharacterDataAdapter adapter(String name) {
		return AdapterRegistry.all().stream()
				.filter(a -> a.id().toString().equals("terra_character_confluence:" + name))
				.findFirst()
				.orElse(null);
	}

	static void extension(ServerPlayer player, String adapter, String key, Number value) {
		var a = adapter(adapter);
		if (a == null) return;
		var tag = a.capture(player);
		if (value instanceof Long) tag.putLong(key, value.longValue());
		else tag.putInt(key, value.intValue());
		a.restore(player, tag, 1);
		a.afterRestore(player);
	}

	static void checkExtension(ServerPlayer player, String adapter, String key, Number value) {
		var a = adapter(adapter);
		if (a != null)
			require(
					a.capture(player).getLong(key) == value.longValue(),
					adapter + " " + key + " expected " + value + " was " + a.capture(player));
	}

	private static void migrateFixture() throws Exception {
		Path world = ClientProfiles.saves().resolve(SUITE + "legacy-import-fixture");
		Files.createDirectories(world);
		var root = AtomicNbt.read(ClientProfiles.saves().resolve(SUITE + "X/level.dat"));
		var data = root.getCompound("Data");
		data.putString("LevelName", "Legacy import fixture");
		data.putInt(
				"DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		var player = new CompoundTag();
		player.putUUID("UUID", UUID.randomUUID());
		player.putFloat("Health", 20);
		var inv = new ListTag();
		var diamond = new CompoundTag();
		diamond.putString("id", "minecraft:diamond");
		diamond.putInt("count", 5);
		diamond.putByte("Slot", (byte) 0);
		inv.add(diamond);
		player.put("Inventory", inv);
		data.put("Player", player);
		AtomicNbt.write(world.resolve("level.dat"), root, false);
		var entry =
				WorldIndex.scan(ClientProfiles.saves()).stream()
						.filter(e -> e.directory().equals(world))
						.findFirst()
						.orElseThrow();
		var source = LegacyImporter.sources(world).getFirst();
		var first =
				LegacyImporter.importCharacter(
						ClientProfiles.store(),
						entry,
						source,
						"Imported",
						CharacterDifficulty.SOFTCORE,
						ClientProfiles.defaults());
		var second =
				LegacyImporter.importCharacter(
						ClientProfiles.store(),
						entry,
						source,
						"Again",
						CharacterDifficulty.SOFTCORE,
						ClientProfiles.defaults());
		require(first.id().equals(second.id()), "Import is idempotent");
		require(
				Files.exists(
						ClientProfiles.store()
								.directory(first.id())
								.resolve("import-source/level.dat")),
				"Original source backed up");
		require(
				AtomicNbt.read(world.resolve("level.dat"))
						.getCompound("Data")
						.getCompound("Player")
						.equals(player),
				"Legacy source unchanged");
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
