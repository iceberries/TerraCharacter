package org.confluence.terra_character.smoke;

import net.minecraft.SharedConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.client.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Menu, dye and portrait regression using an isolated save directory. */
@EventBusSubscriber(modid = TerraCharacter.MODID, value = Dist.CLIENT)
public final class MenuSmokeTest {
	private static final boolean POPUP_ONLY =
			Boolean.parseBoolean(System.getenv("TERRA_CHARACTER_POPUP_ONLY"));
	private static int stage, delay, ticks;
	private static boolean done;
	private static CompletableFuture<Void> reload;
	private static CharacterProfile person;
	private static Screen galleryParent;
	private static CharacterAppearance galleryBefore;

	private static void require(boolean value, String text) {
		if (!value) throw new AssertionError(text);
	}

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		if (!Boolean.getBoolean("terra_character.menu_smoke") || done) return;
		var mc = Minecraft.getInstance();
		try {
			if (++ticks > 3600) throw new AssertionError("Menu test timed out at " + stage);
			if (stage == 0 && mc.screen instanceof TitleScreen) {
				mc.options.languageCode = "zh_cn";
				mc.getLanguageManager().setSelected("zh_cn");
				reload = mc.reloadResourcePacks();
				stage = -1;
			} else if (stage == -1 && reload.isDone() && mc.getOverlay() == null) {
				mc.getWindow().setWindowed(1280, 800);
				mc.options.guiScale().set(2);
				mc.resizeDisplay();
				if (POPUP_ONLY) {
					mc.setScreen(new WardrobeScreen(new TitleScreen(), legacySkin(), value -> {}));
					press("wardrobe.slot.eyes");
					var dropdown =
							mc.screen.children().stream()
									.filter(
											w ->
													w instanceof Button b
															&& b.getMessage()
																	.getString()
																	.endsWith(" ▾"))
									.map(Button.class::cast)
									.findFirst()
									.orElseThrow();
					dropdown.onPress();
					stage = 13;
					delay = 0;
					return;
				}
				setup();
				mc.setScreen(new CharacterSelectionScreen(new TitleScreen()));
				stage = 1;
				delay = 0;
			} else if (stage == 1 && ++delay > 20) {
				checkPortraits();
				bounds();
				shot("character-cards.png");
				var card =
						mc.screen.children().stream()
								.filter(w -> w.getClass().getSimpleName().equals("CharacterCard"))
								.map(w -> (AbstractWidget) w)
								.findFirst()
								.orElseThrow();
				int x = card.getX() + 8, y = card.getY() + 8;
				mc.screen.mouseClicked(x, y, 0);
				require(mc.screen instanceof CharacterSelectionScreen, "First click selects");
				mc.screen.mouseClicked(x, y, 0);
				require(
						mc.screen instanceof PlayDestinationScreen,
						"Double click confirms character");
				press("destination.local");
				require(mc.screen instanceof SelectWorldScreen, "Native world screen retained");
				stage = 2;
				delay = 0;
			} else if (stage == 2 && ++delay > 30) {
				require(mc.screen instanceof SelectWorldScreen, "Native world screen remains");
				shot("native-world-selection.png");
				mc.setScreen(
						new CharacterEditorScreen(
								new TitleScreen(),
								new CharacterProfile(
										UUID.randomUUID(),
										"UI fixture",
										CharacterDifficulty.SOFTCORE,
										legacySkin(),
										CharacterProfile.Status.ACTIVE),
								null));
				stage = 3;
				delay = 0;
			} else if (stage == 3 && ++delay > 15) {
				verifyDyes();
				verifyColorControls();
				bounds();
				shot("character-editor.png");
				press("wardrobe.title");
				stage = 4;
				delay = 0;
			} else if (stage == 4 && ++delay > 15) {
				verifyColorControls();
				verifyGalleryEntry();
				stage = 40;
				delay = 0;
			} else if (stage == 40 && ++delay > 15) {
				bounds();
				shot("preset-gallery.png");
				for (var child : mc.screen.children())
					if (child.getClass().getSimpleName().equals("Card")) {
						var f = child.getClass().getDeclaredField("preview");
						f.setAccessible(true);
						var preview = f.get(child);
						var skin = preview.getClass().getDeclaredField("skin");
						skin.setAccessible(true);
						require(
								skin.get(preview) != null,
								"Gallery models rendered actual skin textures");
					}
				press("cancel");
				var field = galleryParent.getClass().getDeclaredField("draft");
				field.setAccessible(true);
				require(
						field.get(galleryParent).equals(galleryBefore),
						"Gallery cancel preserves original draft");
				galleryEntry(galleryParent).onPress();
				mc.screen.children().stream()
						.filter(
								w ->
										w instanceof AbstractButton b
												&& b.getMessage()
														.getString()
														.equals(
																ClientProfiles.text(
																				"wardrobe.preset.default")
																		.getString()))
						.map(AbstractButton.class::cast)
						.findFirst()
						.orElseThrow()
						.onPress();
				press("appearance.apply");
				require(
						!((CharacterAppearance) field.get(galleryParent))
								.dyeMasks()
								.regions()
								.isEmpty(),
						"Applying preset includes pixel regions");
				bounds();
				shot("wardrobe-presets.png");
				press("wardrobe.slot.hair");
				verifyColorControls();
				require(
						mc.screen.children().stream()
								.anyMatch(
										w ->
												w instanceof Button b
														&& b.getMessage()
																.getString()
																.equals(
																		ClientProfiles.text(
																						"wardrobe.slot.shoes")
																				.getString())),
						"Direct category buttons");
				shot("wardrobe-categories.png");
				mc.getWindow().setWindowed(960, 720);
				mc.options.guiScale().set(3);
				mc.resizeDisplay();
				stage = 5;
				delay = 0;
			} else if (stage == 5 && ++delay > 15) {
				require(mc.screen.width == 320 && mc.screen.height == 240, "Narrow GUI size");
				bounds();
				shot("wardrobe-narrow.png");
				verifyColorControls();
				press("color.reset");
				bounds();
				press("cancel");
				bounds();
				stage = 51;
				delay = 0;
			} else if (stage == 51 && ++delay > 15) {
				shot("editor-narrow.png");
				mc.setScreen(
						new SkinEditorScreen(new TitleScreen(), person.appearance(), value -> {}));
				stage = 52;
				delay = 0;
			} else if (stage == 52 && ++delay > 15) {
				bounds();
				shot("skin-editor-narrow.png");
				verifyRegionEditor();
				mc.setScreen(new CharacterSelectionScreen(new TitleScreen()));
				stage = 6;
				delay = 0;
			} else if (stage == 6 && ++delay > 15) {
				bounds();
				checkPortraits();
				shot("cards-narrow.png");
				press("trash");
				stage = 7;
				delay = 0;
			} else if (stage == 7 && ++delay > 15) {
				bounds();
				shot("trash-narrow.png");
				mc.setScreen(new JourneyScreen(new TitleScreen()));
				stage = 8;
				delay = 0;
			} else if (stage == 8 && ++delay > 15) {
				bounds();
				shot("journey-narrow.png");
				mc.setScreen(
						new NoticeScreen(
								new TitleScreen(),
								Component.literal("Long notice"),
								Component.literal(
										"A long message to check wrapping and scrolling. "
												.repeat(60))));
				stage = 9;
				delay = 0;
			} else if (stage == 9 && ++delay > 15) {
				bounds();
				shot("notice-narrow.png");
				mc.screen.mouseScrolled(160, 100, 0, -3);
				var scroll = mc.screen.getClass().getDeclaredField("scroll");
				scroll.setAccessible(true);
				require(
						(Integer) scroll.get(mc.screen) > 0,
						"Long notice scrolls within its panel");
				mc.setScreen(
						new WardrobeScreen(new TitleScreen(), person.appearance(), value -> {}));
				press("wardrobe.slot.eyes");
				galleryEntry(mc.screen).onPress();
				stage = 10;
				delay = 0;
			} else if (stage == 10 && ++delay > 15) {
				bounds();
				shot("eyes-gallery-narrow.png");
				mc.getWindow().setWindowed(1280, 800);
				mc.options.guiScale().set(2);
				mc.resizeDisplay();
				mc.setScreen(
						new SkinEditorScreen(new TitleScreen(), person.appearance(), value -> {}));
				stage = 11;
				delay = 0;
			} else if (stage == 11 && ++delay > 15) {
				bounds();
				shot("skin-editor-normal.png");
				mc.setScreen(
						new WardrobeScreen(new TitleScreen(), person.appearance(), value -> {}));
				press("wardrobe.slot.eyes");
				galleryEntry(mc.screen).onPress();
				stage = 12;
				delay = 0;
			} else if (stage == 12 && ++delay > 15) {
				bounds();
				shot("eyes-gallery-normal.png");
				press("cancel");
				var dropdown =
						mc.screen.children().stream()
								.filter(
										w ->
												w instanceof Button b
														&& b.getMessage()
																.getString()
																.endsWith(" ▾"))
								.map(Button.class::cast)
								.findFirst()
								.orElseThrow();
				dropdown.onPress();
				stage = 13;
				delay = 0;
			} else if (stage == 13 && ++delay > 15) {
				bounds();
				shot("dropdown-normal.png");
				mc.getWindow().setWindowed(960, 720);
				mc.options.guiScale().set(3);
				mc.resizeDisplay();
				stage = 14;
				delay = 0;
			} else if (stage == 14 && ++delay > 15) {
				bounds();
				shot("dropdown-resized.png");
				var back = mc.screen.getClass().getDeclaredField("back");
				back.setAccessible(true);
				var parent = (Screen) back.get(mc.screen);
				var anchor =
						parent.children().stream()
								.filter(
										w ->
												w instanceof Button b
														&& b.getMessage()
																.getString()
																.endsWith(" ▾"))
								.map(Button.class::cast)
								.findFirst()
								.orElseThrow();
				var popup =
						mc.screen.children().stream()
								.filter(AbstractWidget.class::isInstance)
								.map(AbstractWidget.class::cast)
								.findFirst()
								.orElseThrow();
				require(
						popup.getX() == anchor.getX()
								&& popup.getWidth() == anchor.getWidth()
								&& popup.getY() == anchor.getY() + 20,
						"Resized dropdown remains attached to its trigger");
				mc.screen.onClose();
				bounds();
				press("wardrobe.slot.hair");
				stage = 15;
				delay = 0;
			} else if (stage == 15 && ++delay > 15) {
				bounds();
				shot("highlight-narrow.png");
				finish(
						POPUP_ONLY
								? "PASS: standalone dropdown aligns with its trigger after"
									  + " resizing, list padding, clean highlight and widget bounds"
								: "PASS: updated menu layouts, dye controls, merged entry and"
									  + " adaptive gallery, pixel-region editing, portraits, notice"
									  + " scrolling, widget bounds and non-overlap at 640x400 and"
									  + " 320x240");
			}
		} catch (Throwable ex) {
			TerraCharacter.LOGGER.error("Menu smoke failed at {}", stage, ex);
			finish("FAIL stage " + stage + ": " + ex);
		}
	}

	private static void chooseColorIndex(int index) throws Exception {
		var mc = Minecraft.getInstance();
		var screen = mc.screen;
		var dropdown =
				screen.children().stream()
						.filter(
								w ->
										w instanceof Button b
												&& b.getMessage().getString().endsWith(" ▾"))
						.findFirst()
						.orElseThrow();
		((Button) dropdown).onPress();
		var list =
				mc.screen.children().stream()
						.filter(w -> w.getClass().getSimpleName().equals("ChoiceList"))
						.map(AbstractWidget.class::cast)
						.findFirst()
						.orElseThrow();
		mc.screen.mouseClicked(list.getX() + 8, list.getY() + index * 24 + 12, 0);
		require(mc.screen == screen, "Dye dropdown returns to wardrobe");
	}

	private static Button galleryEntry(Screen screen) {
		return screen.children().stream()
				.filter(
						w ->
								w instanceof Button b
										&& b.getMessage().getContents()
												instanceof TranslatableContents c
										&& c.getKey().equals("terra_character.wardrobe.entry"))
				.map(Button.class::cast)
				.findFirst()
				.orElseThrow();
	}

	private static void verifyGalleryEntry() throws Exception {
		var mc = Minecraft.getInstance();
		var screen = mc.screen;
		chooseColorIndex(0);
		var field = screen.getClass().getDeclaredField("draft");
		field.setAccessible(true);
		var channel = screen.getClass().getDeclaredField("colorChannel");
		channel.setAccessible(true);
		require(
				!channel.get(screen).equals("terra_character:all_colors"),
				"Dropdown only selects actual dye channels");
		var hex =
				screen.children().stream()
						.filter(EditBox.class::isInstance)
						.map(EditBox.class::cast)
						.findFirst()
						.orElseThrow();
		hex.setValue("#3366CC");
		var before = (CharacterAppearance) field.get(screen);
		press("random");
		var after = (CharacterAppearance) field.get(screen);
		for (var entry : before.colors().entrySet())
			if (!entry.getKey().equals(channel.get(screen)))
				require(
						Objects.equals(after.colors().get(entry.getKey()), entry.getValue()),
						"Random changes only the selected channel");
		galleryParent = screen;
		galleryBefore = after;
		galleryEntry(screen).onPress();
		require(
				mc.screen.getClass().getSimpleName().equals("WardrobeGalleryScreen"),
				"Merged preset row opens 3D gallery");
		bounds();
		require(
				mc.screen.children().stream()
						.anyMatch(w -> w.getClass().getSimpleName().equals("Card")),
				"Gallery creates 3D preset cards");
	}

	private static void verifyRegionEditor() throws Exception {
		var mc = Minecraft.getInstance();
		mc.setScreen(new CharacterEditorScreen(new TitleScreen(), person, null));
		press("skin.edit");
		var editor = mc.screen;
		var tools =
				editor.children().stream()
						.filter(
								w ->
										w instanceof Button b
												&& b.getMessage()
														.getString()
														.startsWith(
																ClientProfiles.text(
																				"skin.tool.brush")
																		.getString()))
						.findFirst()
						.orElseThrow();
		((Button) tools).onPress();
		var list =
				mc.screen.children().stream()
						.filter(w -> w.getClass().getSimpleName().equals("ChoiceList"))
						.map(AbstractWidget.class::cast)
						.findFirst()
						.orElseThrow();
		mc.screen.mouseClicked(list.getX() + 8, list.getY() + 3 * 24 + 12, 0);
		require(mc.screen == editor, "Region tool dropdown returns to skin editor");
		int[] position = new int[3];
		String[] names = {"canvasX", "canvasY", "zoom"};
		for (int i = 0; i < 3; i++) {
			var method = editor.getClass().getDeclaredMethod(names[i]);
			method.setAccessible(true);
			position[i] = (Integer) method.invoke(editor);
		}
		double x = position[0] + position[2] * .5, y = position[1] + position[2] * .5;
		editor.mouseClicked(x, y, 0);
		editor.mouseDragged(x + position[2], y + position[2], 0, position[2], position[2]);
		editor.mouseReleased(x + position[2], y + position[2], 0);
		var field = editor.getClass().getDeclaredField("masks");
		field.setAccessible(true);
		var masks = (DyeMasks) field.get(editor);
		require(
				masks.regions().get(WardrobeSlot.EYES).containsAll(List.of(520, 521, 584, 585)),
				"Rectangle assigns exact eye pixels");
		press("skin.undo");
		require(
				((DyeMasks) field.get(editor)).equals(person.appearance().dyeMasks()),
				"Region undo restores defaults");
		press("skin.redo");
		require(field.get(editor).equals(masks), "Region redo restores rectangle");
		press("skin.apply");
		press("save");
		person = ClientProfiles.store().find(person.id()).orElseThrow();
		require(
				person.appearance().dyeMasks().equals(masks),
				"Region selection persists through character save");
	}

	private static void verifyColorControls() throws Exception {
		var screen = Minecraft.getInstance().screen;
		var field = screen.getClass().getDeclaredField("colorChannel");
		field.setAccessible(true);
		require(field.get(screen) != null, "First-open dye selection must be initialized");
		var hex =
				screen.children().stream()
						.filter(EditBox.class::isInstance)
						.map(EditBox.class::cast)
						.filter(
								w ->
										w.getMessage()
												.getString()
												.equals(
														ClientProfiles.text("color.code")
																.getString()))
						.findFirst()
						.orElseThrow();
		hex.setValue("#E34C55");
		var draft = screen.getClass().getDeclaredField("draft");
		draft.setAccessible(true);
		require(
				((CharacterAppearance) draft.get(screen)).colors().get(field.get(screen))
						== 0xE34C55,
				"Inline dye updates appearance draft");
	}

	private static void verifyDyes() throws Exception {
		var ui = Class.forName("org.confluence.terra_character.client.EditorUi");
		var channels =
				ui.getDeclaredMethod("channels", CharacterAppearance.class, WardrobeSlot.class);
		channels.setAccessible(true);
		for (String body : List.of("terra_character:slim", "terra_character:wide")) {
			var original = new CharacterAppearance(body, "terra_character:fire_berry", Map.of());
			try (var source = CharacterSkins.image(original)) {
				for (var slot : WardrobeSlot.values()) {
					@SuppressWarnings("unchecked")
					var ids = (List<String>) channels.invoke(null, original, slot);
					require(!ids.isEmpty(), "Base dye available for " + slot);
					var colors = new HashMap<String, Integer>();
					ids.forEach(id -> colors.put(id, 0xE34C55));
					try (var dyed =
							CharacterSkins.image(
									new CharacterAppearance(body, original.hairStyle(), colors))) {
						int changes = 0;
						for (int y = 0; y < 64; y++)
							for (int x = 0; x < 64; x++) {
								int a = source.getPixelRGBA(x, y), b = dyed.getPixelRGBA(x, y);
								require((a >>> 24) == (b >>> 24), "Dye preserves base/outer alpha");
								if (a != b) changes++;
							}
						require(changes > 0, "Dye changes pixels for " + body + " / " + slot);
					}
				}
			}
		}
	}

	private static CharacterAppearance legacySkin() {
		return new CharacterAppearance(
				"terra_character:slim", "terra_character:fire_berry", Map.of());
	}

	private static CompoundTag item(String id, int slot) {
		var tag = new CompoundTag();
		tag.putString("id", id);
		tag.putInt("count", 1);
		tag.putByte("Slot", (byte) slot);
		return tag;
	}

	private static void setup() throws Exception {
		var directory = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
		require(
				directory.endsWith(Path.of("build", "menu-smoke-run")),
				"Isolated menu fixture directory");
		var store = ClientProfiles.store();
		for (var previous : store.list()) store.archive(previous.id());
		person =
				new CharacterProfile(
						UUID.randomUUID(),
						"Equipped",
						CharacterDifficulty.SOFTCORE,
						legacySkin(),
						CharacterProfile.Status.ACTIVE);
		store.create(person);
		try (var lock = store.lock(person.id())) {
			var doc = store.read(person.id());
			var vanilla = new CompoundTag();
			var inventory = new ListTag();
			inventory.add(item("minecraft:diamond_sword", 0));
			inventory.add(item("minecraft:shield", 150));
			inventory.add(item("minecraft:diamond_chestplate", 102));
			inventory.add(item("minecraft:diamond_boots", 100));
			vanilla.put("Inventory", inventory);
			var snapshot = new CompoundTag();
			snapshot.putInt("Version", 1);
			snapshot.put("Data", vanilla);
			doc.getCompound("Adapters").put("terra_character:vanilla", snapshot);
			if (ModList.get().isLoaded("confluence")) {
				var curios = new CompoundTag();
				var list = new ListTag();
				var entry = new CompoundTag();
				entry.putString("Identifier", "accessory");
				var stacks = new CompoundTag();
				stacks.putInt("SavedBaseSize", 1);
				var items = new CompoundTag();
				items.putInt("Size", 1);
				var equipped = new ListTag();
				var wings = item("terra_curio:worm_scarf", 0);
				wings.putInt("Slot", 0);
				equipped.add(wings);
				items.put("Items", equipped);
				stacks.put("Stacks", items);
				entry.put("StacksHandler", stacks);
				list.add(entry);
				curios.put("Curios", list);
				var c = new CompoundTag();
				c.putInt("Version", 1);
				c.put("Data", curios);
				doc.getCompound("Adapters").put("terra_character_confluence:curios", c);
			}
			store.commit(person.id(), doc, doc.getLong("Revision"));
		}
		var second =
				new CharacterProfile(
						UUID.randomUUID(),
						"Classic",
						CharacterDifficulty.MEDIUMCORE,
						new CharacterAppearance(
								"terra_character:wide",
								"terra_character:short",
								ClientProfiles.defaults().colors()),
						CharacterProfile.Status.ACTIVE);
		store.create(second);
		var root = new CompoundTag();
		var data = new CompoundTag();
		root.put("Data", data);
		data.putInt("version", 19133);
		data.putInt(
				"DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		data.putString("LevelName", "Native world UI");
		data.putLong("LastPlayed", System.currentTimeMillis());
		data.putInt("GameType", 0);
		data.putByte("Difficulty", (byte) 2);
		var version = new CompoundTag();
		version.putString("Name", SharedConstants.getCurrentVersion().getName());
		version.putInt("Id", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		version.putBoolean("Snapshot", false);
		data.put("Version", version);
		var folder = ClientProfiles.saves().resolve("menu-preview-world");
		Files.createDirectories(folder);
		NbtIo.writeCompressed(root, folder.resolve("level.dat"));
	}

	private static void checkPortraits() throws Exception {
		for (var child : Minecraft.getInstance().screen.children())
			if (child.getClass().getSimpleName().equals("CharacterCard")) {
				var field = child.getClass().getDeclaredField("portrait");
				field.setAccessible(true);
				var value = (CharacterPortraits.PreviewPlayer) field.get(child);
				require(value != null, "Full player portrait renders without fallback");
				if (value.getGameProfile().getName().equals("Equipped")) {
					require(
							!value.getMainHandItem().isEmpty() && !value.getOffhandItem().isEmpty(),
							"Both hands loaded");
					require(!value.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "Armor loaded");
					if (ModList.get().isLoaded("curios")) {
						var api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
						var optional =
								(Optional<?>)
										api.getMethod("getCuriosInventory", LivingEntity.class)
												.invoke(null, value);
						var handler = optional.orElseThrow();
						var slots =
								(Map<?, ?>)
										handler.getClass().getMethod("getCurios").invoke(handler);
						require(
								slots.containsKey("accessory"),
								"Saved accessory slots restored for menu render");
					}
				}
			}
	}

	private static void bounds() {
		var s = Minecraft.getInstance().screen;
		var widgets =
				s.children().stream()
						.filter(AbstractWidget.class::isInstance)
						.map(AbstractWidget.class::cast)
						.filter(w -> w.visible)
						.toList();
		for (var w : widgets)
			require(
					w.getWidth() > 0
							&& w.getHeight() > 0
							&& w.getX() >= 0
							&& w.getY() >= 0
							&& w.getX() + w.getWidth() <= s.width
							&& w.getY() + w.getHeight() <= s.height,
					"Widget bounds: " + w.getMessage());
		// Native world lists contain their own children; only audit Terra Character sibling
		// widgets.
		if (!s.getClass().getPackageName().startsWith("org.confluence.terra_character")) return;
		for (int i = 0; i < widgets.size(); i++)
			for (int j = i + 1; j < widgets.size(); j++) {
				var a = widgets.get(i);
				var b = widgets.get(j);
				require(
						a.getX() + a.getWidth() <= b.getX()
								|| b.getX() + b.getWidth() <= a.getX()
								|| a.getY() + a.getHeight() <= b.getY()
								|| b.getY() + b.getHeight() <= a.getY(),
						"Overlapping widgets: " + a.getMessage() + " / " + b.getMessage());
			}
	}

	private static void press(String key) {
		var s = Minecraft.getInstance().screen;
		var label = ClientProfiles.text(key).getString();
		for (var child : s.children())
			if (child instanceof Button b && b.getMessage().getString().equals(label)) {
				b.onPress();
				return;
			}
		throw new AssertionError("Missing button " + key);
	}

	private static void shot(String name) {
		var mc = Minecraft.getInstance();
		Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> {});
	}

	private static void finish(String result) {
		done = true;
		try {
			Files.writeString(
					Minecraft.getInstance().gameDirectory.toPath().resolve("menu-result.txt"),
					result);
		} catch (Exception ignored) {
		}
		Minecraft.getInstance().stop();
	}
}
