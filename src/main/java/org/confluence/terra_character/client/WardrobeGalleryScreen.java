package org.confluence.terra_character.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.confluence.terra_character.api.model.*;

import java.util.*;
import java.util.function.BiConsumer;

/** Resource-backed gallery. Every tile previews a complete flattened vanilla skin. */
final class WardrobeGalleryScreen extends ProfileScreen {
	static final String NONE = "terra_character:none";

	private record Candidate(String id, Component name, CharacterAppearance appearance) {}

	private record Tile(
			Candidate candidate, int x, int y, int width, int height, SkinPreview.Focus focus) {}

	private final CharacterAppearance original;
	private final WardrobeSlot slot;
	private final BiConsumer<String, CharacterAppearance> apply;
	private final List<Card> cards = new ArrayList<>();
	private List<Candidate> candidates = List.of();
	private final DoubleClickSelection<String> clicks = new DoubleClickSelection<>();
	private String selected;
	private int page, pageCount;
	private boolean firstPage = true;
	private long generation;
	private Card rotating;

	WardrobeGalleryScreen(
			Screen back,
			CharacterAppearance original,
			WardrobeSlot slot,
			String selected,
			BiConsumer<String, CharacterAppearance> apply) {
		super(back, ClientProfiles.text("wardrobe.gallery"));
		this.original = original;
		this.slot = slot;
		this.selected = selected;
		this.apply = apply;
	}

	private void closePreviews() {
		cards.forEach(c -> c.preview.close());
		cards.clear();
		rotating = null;
	}

	@Override
	protected void init() {
		closePreviews();
		generation = WardrobeResources.generation();
		var entries = new ArrayList<Candidate>();
		if (slot == null) {
			for (var preset : WardrobeResources.presets())
				entries.add(
						new Candidate(
								preset.id(),
								Component.translatable(preset.name()),
								AccountSkins.preset(preset.appearance())));
		} else {
			entries.add(
					new Candidate(
							NONE,
							ClientProfiles.text("wardrobe.none"),
							original.withWardrobe(original.wardrobe().equip(slot, null))));
			for (var part : WardrobeResources.parts(slot))
				entries.add(
						new Candidate(
								part.id(),
								Component.translatable(part.name()),
								original.withWardrobe(original.wardrobe().equip(slot, part.id()))));
		}
		candidates = List.copyOf(entries);
		if (candidates.stream().noneMatch(c -> c.id().equals(selected)))
			selected = candidates.isEmpty() ? null : candidates.getFirst().id();
		int span = Math.min(900, width - 24),
				left = (width - span) / 2,
				area = Math.max(60, height - 103);
		var pages = new ArrayList<List<Tile>>();
		var tiles = new ArrayList<Tile>();
		pages.add(tiles);
		int tx = 0, ty = 0, rowHeight = 0;
		for (var candidate : candidates) {
			var focus = SkinPreview.focus(candidate.appearance(), slot);
			int preferredScale = slot == null ? 54 : 96;
			int cw =
					Math.min(
							span,
							Math.max(
									96,
									Math.min(
											176,
											(int) Math.ceil(focus.width() * preferredScale / 16)
													+ 20)));
			int ch =
					Math.min(
							area,
							Math.max(
									64,
									Math.min(
											192,
											(int) Math.ceil(focus.height() * preferredScale / 16)
													+ 36)));
			if (tx > 0 && tx + cw > span) {
				tx = 0;
				ty += rowHeight + 8;
				rowHeight = 0;
			}
			if (ty + ch > area) {
				tiles = new ArrayList<>();
				pages.add(tiles);
				tx = 0;
				ty = 0;
				rowHeight = 0;
			}
			tiles.add(new Tile(candidate, left + tx, 35 + ty, cw, ch, focus));
			tx += cw + 8;
			rowHeight = Math.max(rowHeight, ch);
		}
		pageCount = pages.size();
		if (firstPage) {
			for (int i = 0; i < pages.size(); i++)
				if (pages.get(i).stream().anyMatch(t -> t.candidate().id().equals(selected))) {
					page = i;
					break;
				}
			firstPage = false;
		}
		page = Math.min(page, pageCount - 1);
		for (var tile : pages.get(page)) {
			int end =
					pages.get(page).stream()
							.filter(t -> t.y() == tile.y())
							.mapToInt(t -> t.x() + t.width())
							.max()
							.orElse(left + span);
			int offset = (left + span - end) / 2;
			var card =
					new Card(
							new Tile(
									tile.candidate(),
									tile.x() + offset,
									tile.y(),
									tile.width(),
									tile.height(),
									tile.focus()));
			cards.add(card);
			addRenderableWidget(card);
		}
		button(
								width / 2 - 72,
								height - 56,
								28,
								Component.literal("◀"),
								() -> {
									page--;
									clicks.reset();
									rebuildWidgets();
								})
						.active =
				page > 0;
		button(
								width / 2 + 44,
								height - 56,
								28,
								Component.literal("▶"),
								() -> {
									page++;
									clicks.reset();
									rebuildWidgets();
								})
						.active =
				page + 1 < pageCount;
		button(
								width / 2 - 122,
								height - 28,
								118,
								ClientProfiles.text("appearance.apply"),
								this::confirm)
						.active =
				!candidates.isEmpty();
		button(width / 2 + 4, height - 28, 118, ClientProfiles.text("cancel"), this::onClose);
	}

	private void confirm() {
		candidates.stream()
				.filter(c -> c.id().equals(selected))
				.findFirst()
				.ifPresent(
						c -> {
							apply.accept(c.id(), c.appearance());
							onClose();
						});
	}

	private final class Card extends AbstractButton {
		private final Candidate candidate;
		private final SkinPreview.Focus focus;
		private final SkinPreview preview = new SkinPreview();

		Card(Tile tile) {
			super(tile.x(), tile.y(), tile.width(), tile.height(), tile.candidate().name());
			candidate = tile.candidate();
			focus = tile.focus();
			setTooltip(
					Tooltip.create(
							candidate
									.name()
									.copy()
									.append("\n")
									.append(ClientProfiles.text("characters.rotate"))));
		}

		@Override
		public void onPress() {
			selected = candidate.id();
			clicks.reset();
		}

		@Override
		public void onClick(double x, double y) {
			selected = candidate.id();
			preview.followMouse();
			if (clicks.click(selected, Util.getMillis())) confirm();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			defaultButtonNarrationText(out);
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			EditorUi.frame(g, x, y, w, h, candidate.id().equals(selected), isHoveredOrFocused());
			g.enableScissor(x + 8, y + 8, x + w - 8, y + h - 29);
			try {
				int scale =
						Math.max(
								1,
								(int)
										Math.min(
												(h - 41) * 16 / focus.height(),
												(w - 20) * 16 / focus.width()));
				int modelCenter = y + 8 + (h - 37) / 2;
				preview.bounds(
						x + w / 2,
						Math.round(modelCenter + scale * 1.5f - focus.centerY() * scale / 16),
						scale);
				preview.interactionArea(x + 8, y + 8, w - 16, h - 37);
				preview.render(
						g, candidate.appearance(), mouseX, true, slot, null, null, slot == null);
			} finally {
				g.disableScissor();
			}
			EditorUi.row(g, x + 8, y + h - 25, w - 16, 17, candidate.id().equals(selected), false);
			EditorUi.text(g, candidate.name(), x + 14, y + h - 23, w - 28, 13, EditorUi.TEXT, true);
		}
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if (button == 1)
			for (var card : cards)
				if (card.isMouseOver(x, y) && card.preview.click(x, y, button)) {
					rotating = card;
					clicks.reset();
					return true;
				}
		return super.mouseClicked(x, y, button);
	}

	@Override
	public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		return rotating != null && rotating.preview.drag(button, dx)
				|| super.mouseDragged(x, y, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		if (rotating != null && rotating.preview.release(button)) {
			rotating = null;
			return true;
		}
		return super.mouseReleased(x, y, button);
	}

	@Override
	public void render(GuiGraphics g, int x, int y, float delta) {
		if (generation != WardrobeResources.generation()) {
			clicks.reset();
			rebuildWidgets();
		}
		super.render(g, x, y, delta);
		EditorUi.text(
				g,
				Component.translatable("terra_character.page", page + 1, pageCount),
				width / 2 - 40,
				height - 52,
				80,
				12,
				EditorUi.MUTED,
				true);
		if (candidates.isEmpty())
			EditorUi.text(
					g, ClientProfiles.text("empty"), 12, 58, width - 24, 20, EditorUi.MUTED, true);
	}

	@Override
	public void removed() {
		closePreviews();
		super.removed();
	}
}
