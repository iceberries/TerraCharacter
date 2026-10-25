package org.confluence.terra_character.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.tooltip.BelowOrAboveWidgetTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.MenuTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.service.CharacterPlayTime;
import org.confluence.terra_character.service.CharacterStatisticsService;
import org.confluence.terra_character.storage.SessionCheckpoint;
import org.confluence.terra_character.storage.StarQueue;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public final class CharacterSelectionScreen extends ProfileScreen {
	private static final int FOOTER_HEIGHT = 22, STAR_SIZE = 18;
	private static final int CARD_ASPECT_WIDTH = 7, CARD_ASPECT_HEIGHT = 6;
	private static final int CARD_GAP = 10, MIN_CARD_WIDTH = 100;
	private List<CharacterProfile> profiles = List.of();
	private UUID selected;
	private StarQueue stars;
	private int page;
	private int pageCount = 1;
	private int gridTop, gridBottom;
	private double pageScroll;
	private String selectedAction;
	private ActionCarousel actionCarousel;
	private final boolean trash;
	private final Map<UUID, CharacterPortraits.PreviewPlayer> portraits = new HashMap<>();
	private final Map<UUID, CharacterStatistics> statistics = new HashMap<>();

	private record CardLine(Component text, int color) {}

	private final Map<UUID, Float> rotations = new HashMap<>();
	private UUID rotatingCharacter;

	@Override
	public void removed() {
		rotatingCharacter = null;
		portraits.clear();
		statistics.clear();
		super.removed();
	}

	private final DoubleClickSelection<UUID> clicks = new DoubleClickSelection<>();

	public CharacterSelectionScreen(Screen back) {
		this(back, false);
	}

	private CharacterSelectionScreen(Screen back, boolean trash) {
		super(back, ClientProfiles.text(trash ? "trash" : "characters"));
		this.trash = trash;
	}

	private CharacterProfile chosen() {
		return profiles.stream().filter(p -> p.id().equals(selected)).findFirst().orElse(null);
	}

	@Override
	protected void init() {
		rotatingCharacter = null;
		try {
			profiles = trash ? ClientProfiles.store().archived() : ClientProfiles.store().list();
		} catch (Exception ex) {
			ClientProfiles.error(getBack(), ex);
			return;
		}
		if (!trash) {
			try {
				stars = ClientStars.characters();
				profiles = stars.order(profiles, profile -> profile.id().toString());
			} catch (IOException ex) {
				stars = null;
			}
		}
		int span = Math.min(900, width - 24), left = (width - span) / 2;
		gridTop = 34;
		int dockY = height - 54;
		gridBottom = dockY - 12;
		int gridWidth = span - 64, gridLeft = left + 32;
		int columns = Math.clamp((gridWidth + CARD_GAP) / (MIN_CARD_WIDTH + CARD_GAP), 1, 3);
		int gridHeight = Math.max(64, gridBottom - gridTop);
		// Scale the original three-card proportions together, bounded by both available axes.
		int cardWidth =
				Math.min(
						(gridWidth - (columns - 1) * CARD_GAP) / columns,
						gridHeight * CARD_ASPECT_WIDTH / CARD_ASPECT_HEIGHT);
		int cardHeight = cardWidth * CARD_ASPECT_HEIGHT / CARD_ASPECT_WIDTH;
		int cardTop = gridTop + (gridHeight - cardHeight) / 2;
		int perPage = columns;
		pageCount = Math.max(1, Math.ceilDiv(profiles.size(), perPage));
		page = Math.clamp(page, 0, pageCount - 1);
		for (int i = page * perPage; i < Math.min(profiles.size(), (page + 1) * perPage); i++) {
			var profile = profiles.get(i);
			int index = i - page * perPage;
			int rowItems = Math.min(columns, profiles.size() - page * perPage);
			int rowLeft =
					gridLeft + (gridWidth - (rowItems * cardWidth + (rowItems - 1) * CARD_GAP)) / 2;
			var card =
					new CharacterCard(
							rowLeft + index * (cardWidth + CARD_GAP),
							cardTop,
							cardWidth,
							cardHeight,
							profile);
			addRenderableWidget(card);
			if (!trash)
				addRenderableWidget(
						new StarButton(
								card.starX(),
								card.starY(),
								stars != null && stars.contains(profile.id().toString()),
								() ->
										run(
												() -> {
													ClientStars.characters()
															.toggle(profile.id().toString());
													clicks.reset();
													page = 0;
													rebuildWidgets();
												})));
		}
		int arrowY = cardTop + (cardHeight - 28) / 2;
		var previous =
				addRenderableWidget(
						EditorUi.button(
								left, arrowY, 24, 28, Component.literal("◀"), () -> turnPage(-1)));
		previous.active = page > 0;
		previous.setTooltip(Tooltip.create(ClientProfiles.text("characters.previous_page")));
		var next =
				addRenderableWidget(
						EditorUi.button(
								left + span - 24,
								arrowY,
								24,
								28,
								Component.literal("▶"),
								() -> turnPage(1)));
		next.active = page + 1 < pageCount;
		next.setTooltip(Tooltip.create(ClientProfiles.text("characters.next_page")));
		boolean interrupted =
				selected != null
						&& Files.exists(SessionCheckpoint.marker(ClientProfiles.store(), selected));
		var profile = chosen();
		var actions = new LinkedHashMap<String, ActionCarousel.Action>();
		if (trash) {
			actions.put(
					"restore",
					new ActionCarousel.Action(
							"restore",
							ClientProfiles.text("restore"),
							ActionCarousel.Icon.RESTORE,
							profile != null,
							() ->
									run(
											() -> {
												ClientProfiles.store().unarchive(selected);
												clearSelection();
												rebuildWidgets();
											})));
			actions.put(
					"delete",
					new ActionCarousel.Action(
							"delete",
							ClientProfiles.text("delete.permanent"),
							ActionCarousel.Icon.DELETE,
							profile != null,
							this::confirmPermanentDelete));
		} else {
			actions.put(
					"create",
					new ActionCarousel.Action(
							"create",
							ClientProfiles.text("create"),
							ActionCarousel.Icon.CREATE,
							true,
							() ->
									minecraft.setScreen(
											new CharacterEditorScreen(this, null, null))));
			actions.put(
					"play",
					new ActionCarousel.Action(
							"play",
							ClientProfiles.text("play"),
							ActionCarousel.Icon.PLAY,
							profile != null && profile.status() == CharacterProfile.Status.ACTIVE,
							this::confirmSelection));
			actions.put(
					"trash",
					new ActionCarousel.Action(
							"trash",
							ClientProfiles.text("trash"),
							ActionCarousel.Icon.TRASH,
							true,
							() -> minecraft.setScreen(new CharacterSelectionScreen(this, true))));
			actions.put(
					"delete",
					new ActionCarousel.Action(
							"delete",
							ClientProfiles.text("delete"),
							ActionCarousel.Icon.DELETE,
							profile != null,
							this::confirmArchive));
			if (interrupted)
				actions.put(
						"recover",
						new ActionCarousel.Action(
								"recover",
								ClientProfiles.text("recover.session"),
								ActionCarousel.Icon.RECOVER,
								true,
								this::confirmRecovery));
		}
		actions.put(
				"back",
				new ActionCarousel.Action(
						"back",
						ClientProfiles.text("back"),
						ActionCarousel.Icon.BACK,
						true,
						this::onClose));
		if (selectedAction == null || !actions.containsKey(selectedAction))
			selectedAction =
					trash
							? "restore"
							: interrupted ? "recover" : profiles.isEmpty() ? "create" : "play";
		actionCarousel =
				addRenderableWidget(
						new ActionCarousel(
								left,
								dockY,
								span,
								actions.values(),
								selectedAction,
								id -> selectedAction = id));
		if (!trash && !ClientProfiles.store().problems().isEmpty())
			button(width - 26, 8, 20, Component.literal("!"), this::confirmBackupRecovery);
	}

	private void turnPage(int direction) {
		int next = Math.clamp(page + direction, 0, pageCount - 1);
		if (next == page) return;
		clicks.reset();
		rotatingCharacter = null;
		page = next;
		rebuildWidgets();
	}

	private void confirmArchive() {
		var profile = chosen();
		if (trash || profile == null) return;
		UUID id = profile.id();
		minecraft.setScreen(
				new ConfirmScreen(
						ok -> {
							if (ok)
								run(
										() -> {
											ClientProfiles.store().archive(id);
											clearSelection();
											minecraft.setScreen(this);
										});
							else minecraft.setScreen(this);
						},
						ClientProfiles.text("delete.confirm"),
						ClientProfiles.text("delete.help")));
	}

	private void confirmRecovery() {
		var profile = chosen();
		if (profile == null) return;
		UUID id = profile.id();
		minecraft.setScreen(
				new ConfirmScreen(
						ok -> {
							if (ok)
								run(
										() -> {
											SessionCheckpoint.recover(
													ClientProfiles.store(),
													id,
													ClientProfiles.saves());
											minecraft.setScreen(this);
										});
							else minecraft.setScreen(this);
						},
						ClientProfiles.text("recover.session"),
						ClientProfiles.text("recover.help")));
	}

	private void confirmBackupRecovery() {
		var problems = ClientProfiles.store().problems();
		if (problems.isEmpty()) return;
		var first = problems.entrySet().iterator().next();
		minecraft.setScreen(
				new ConfirmScreen(
						ok -> {
							if (ok)
								run(
										() -> {
											ClientProfiles.store().recover(first.getKey());
											minecraft.setScreen(this);
										});
							else minecraft.setScreen(this);
						},
						ClientProfiles.text("recover.backup"),
						Component.literal(first.getKey() + ": " + first.getValue())));
	}

	private void clearSelection() {
		portraits.remove(selected);
		statistics.remove(selected);
		rotations.remove(selected);
		selected = null;
		clicks.reset();
	}

	private void confirmPermanentDelete() {
		var profile = chosen();
		if (!trash || profile == null) return;
		UUID id = profile.id();
		minecraft.setScreen(
				new ConfirmScreen(
						ok -> {
							if (ok)
								run(
										() -> {
											ClientProfiles.store().deletePermanently(id);
											clearSelection();
											minecraft.setScreen(this);
										});
							else minecraft.setScreen(this);
						},
						Component.translatable(
								"terra_character.delete.permanent.confirm", profile.name()),
						ClientProfiles.text("delete.permanent.help")));
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		if (actionCarousel != null && actionCarousel.isMouseOver(x, y))
			return actionCarousel.mouseScrolled(x, y, horizontal, vertical);
		if (y >= gridTop && y < gridBottom) {
			pageScroll += Math.clamp(vertical != 0 ? -vertical : horizontal, -4, 4);
			int steps = (int) pageScroll;
			pageScroll -= steps;
			turnPage(steps);
			return true;
		}
		return super.mouseScrolled(x, y, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(int key, int scan, int modifiers) {
		if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
			turnPage(key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1);
			return true;
		}
		return super.keyPressed(key, scan, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 1) {
			for (var child : children())
				if (child instanceof CharacterCard card && card.isMouseOver(mouseX, mouseY)) {
					rotatingCharacter = card.profile.id();
					clicks.reset();
					return true;
				}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(
			double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button == 1 && rotatingCharacter != null) {
			// Entity portraits use the opposite yaw convention from the raw skin preview.
			rotations.compute(
					rotatingCharacter,
					(id, yaw) -> Mth.wrapDegrees((yaw == null ? 0 : yaw) + (float) dragX));
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 1 && rotatingCharacter != null) {
			rotatingCharacter = null;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private void confirmSelection() {
		var profile = chosen();
		if (trash || profile == null || profile.status() != CharacterProfile.Status.ACTIVE) return;
		run(
				() -> {
					ClientProfiles.setSelected(selected);
					ClientProfiles.clearWorldOptions();
					minecraft.setScreen(new PlayDestinationScreen(this));
				});
	}

	private void select(CharacterProfile profile, boolean mouse) {
		selected = profile.id();
		if (mouse && clicks.click(selected, Util.getMillis())) {
			confirmSelection();
			return;
		}
		if (!mouse) clicks.reset();
		// Rebuild only the controls; the click tracker belongs to the screen, not a recycled card.
		rebuildWidgets();
	}

	private List<CardLine> cardLines(CharacterProfile profile, CharacterStatistics summary) {
		var lines = new ArrayList<CardLine>();
		lines.add(new CardLine(Component.literal(profile.name()), EditorUi.TEXT));
		var info = profileInfo(profile);
		if (!info.getString().isEmpty())
			lines.add(
					new CardLine(
							info,
							profile.status() == CharacterProfile.Status.DEAD
									? 0xFFFF8888
									: EditorUi.MUTED));
		lines.add(
				new CardLine(
						ClientProfiles.text(
								"characters.health",
								number(summary.health()),
								number(summary.maxHealth())),
						0xFFFF9999));
		if (summary.hasMana())
			lines.add(
					new CardLine(
							ClientProfiles.text(
									"characters.mana",
									number(summary.mana()),
									number(summary.maxMana())),
							0xFF99BBFF));
		lines.add(
				new CardLine(
						ClientProfiles.text(
								"characters.play_time",
								CharacterPlayTime.format(summary.playTicks())),
						EditorUi.MUTED));
		return List.copyOf(lines);
	}

	private static Component profileInfo(CharacterProfile profile) {
		var info =
				ModList.get().isLoaded("confluence")
						? ClientProfiles.difficulty(profile.difficulty()).copy()
						: Component.empty();
		if (profile.status() == CharacterProfile.Status.DEAD)
			info.append(ClientProfiles.text("dead"));
		return info;
	}

	private static String number(float value) {
		return value == Math.floor(value)
				? String.format(Locale.ROOT, "%.0f", value)
				: String.format(Locale.ROOT, "%.1f", value);
	}

	private final class CharacterCard extends AbstractButton {
		private final CharacterProfile profile;
		private CharacterPortraits.PreviewPlayer portrait;
		private final List<CardLine> lines;
		private final CharacterStatistics summary;
		private final Component details;

		CharacterCard(int x, int y, int w, int h, CharacterProfile profile) {
			super(x, y, w, h, Component.literal(profile.name()));
			this.profile = profile;
			try {
				if (!portraits.containsKey(profile.id()) || !statistics.containsKey(profile.id())) {
					var document = ClientProfiles.store().read(profile.id());
					statistics.put(profile.id(), CharacterStatisticsService.summary(document));
					if (!portraits.containsKey(profile.id()))
						portraits.put(profile.id(), CharacterPortraits.load(profile, document));
				}
				portrait = portraits.get(profile.id());
			} catch (Exception ex) {
				TerraCharacter.LOGGER.warn("Cannot load character card {}", profile.id(), ex);
			}
			summary = statistics.getOrDefault(profile.id(), CharacterStatistics.DEFAULT);
			lines = cardLines(profile, summary);
			var info = profileInfo(profile);
			details =
					Component.literal(
							(info.getString().isEmpty() ? "" : info.getString() + " · ")
									+ CharacterPlayTime.format(summary.playTicks()));
			var tooltip = Component.empty();
			for (var line : lines) tooltip.append(line.text()).append("\n");
			tooltip.append(ClientProfiles.text("characters.rotate"));
			setTooltip(Tooltip.create(tooltip));
		}

		@Override
		public boolean mouseClicked(double x, double y, int button) {
			// The star is a separate focusable widget; do not consume its click as card selection.
			if (!trash
					&& x >= starX()
					&& x < starX() + STAR_SIZE
					&& y >= starY()
					&& y < starY() + STAR_SIZE) return false;
			return super.mouseClicked(x, y, button);
		}

		private int footerY() {
			return getY() + getHeight() - FOOTER_HEIGHT - 8;
		}

		private int starX() {
			return getX() + getWidth() - STAR_SIZE - 10;
		}

		private int starY() {
			return footerY() + (FOOTER_HEIGHT - STAR_SIZE) / 2;
		}

		@Override
		public void onPress() {
			select(profile, false);
		}

		@Override
		public void onClick(double x, double y) {
			select(profile, true);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			defaultButtonNarrationText(out);
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			boolean chosen = profile.id().equals(selected);
			EditorUi.frame(g, x, y, w, h, chosen, isHoveredOrFocused());
			int footerY = footerY();
			int modelTop = y + 10, modelBottom = footerY - 4;
			EditorUi.row(g, x + 8, footerY, w - 16, FOOTER_HEIGHT, chosen, false);
			// Reserve matching side gutters for vitals, leaving the enlarged portrait unobstructed.
			g.enableScissor(x + 30, modelTop, x + w - 30, modelBottom);
			try {
				int scale =
						Math.max(
								1,
								Math.min(
										96,
										Math.min(
												(int) ((modelBottom - modelTop) / 2.1F),
												(w - 60) * 10 / 13)));
				float yaw = rotations.getOrDefault(profile.id(), 0f);
				if (portrait != null)
					CharacterPortraits.render(g, portrait, x + w / 2, modelBottom, scale, yaw);
				// Convert entity portrait yaw at the raw-model rendering boundary.
				else
					CharacterSkins.preview(
							g, profile.appearance(), x + w / 2, modelBottom, scale, 180 - yaw);
			} catch (Exception ex) {
				portrait = null;
				portraits.put(profile.id(), null);
				TerraCharacter.LOGGER.warn("Cannot render character portrait {}", profile.id(), ex);
			} finally {
				g.disableScissor();
			}
			CharacterVitals.render(
					g, summary, x + 10, x + w - 19, modelTop, modelBottom - modelTop);
			int textWidth = w - (trash ? 28 : 50);
			EditorUi.text(g, getMessage(), x + 14, footerY + 2, textWidth, 9, EditorUi.TEXT, true);
			EditorUi.text(
					g,
					details,
					x + 14,
					footerY + 11,
					textWidth,
					9,
					profile.status() == CharacterProfile.Status.DEAD ? 0xFFFF8888 : EditorUi.MUTED,
					true);
		}
	}

	@Override
	public void render(GuiGraphics g, int x, int y, float d) {
		super.render(g, x, y, d);
		keepTooltipOnScreen();
		if (pageCount > 1)
			EditorUi.text(
					g,
					Component.translatable("terra_character.page", page + 1, pageCount),
					width / 2 - 40,
					gridBottom + 1,
					80,
					9,
					EditorUi.MUTED,
					true);
		if (profiles.isEmpty())
			EditorUi.text(
					g,
					ClientProfiles.text(trash ? "trash.empty" : "empty"),
					12,
					48,
					width - 24,
					20,
					EditorUi.MUTED,
					true);
	}

	private void keepTooltipOnScreen() {
		AbstractWidget target = null;
		boolean keyboard = minecraft.getLastInputType().isKeyboard();
		for (var child : children()) {
			if (!(child instanceof AbstractWidget widget)
					|| !widget.visible
					|| widget.getTooltip() == null) continue;
			if (keyboard && widget.isFocused()) {
				target = widget;
				break;
			}
			// Later widgets (such as the star) take precedence over the card underneath.
			if (widget.isHovered()) target = widget;
		}
		if (target == null) return;
		ClientTooltipPositioner delegate =
				keyboard && !target.isHovered()
						? new BelowOrAboveWidgetTooltipPositioner(target.getRectangle())
						: new MenuTooltipPositioner(target.getRectangle());
		setTooltipForNextRenderPass(
				target.getTooltip(),
				(screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight) -> {
					var position =
							delegate.positionTooltip(
									screenWidth,
									screenHeight,
									mouseX,
									mouseY,
									tooltipWidth,
									tooltipHeight);
					// Include the tooltip background and border in the safe inset.
					return new Vector2i(
							Mth.clamp(position.x(), 5, Math.max(5, screenWidth - tooltipWidth - 5)),
							Mth.clamp(
									position.y(),
									5,
									Math.max(5, screenHeight - tooltipHeight - 5)));
				},
				true);
	}
}
