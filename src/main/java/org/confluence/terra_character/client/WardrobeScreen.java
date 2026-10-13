package org.confluence.terra_character.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.confluence.terra_character.api.model.*;

import java.util.*;
import java.util.function.Consumer;

/** Categories, part choices, preview and inline dye controls edit a local draft. */
public final class WardrobeScreen extends ProfileScreen {
	private CharacterAppearance draft;
	private final Consumer<CharacterAppearance> apply;
	private WardrobeSlot slot = WardrobeSlot.HAIR;
	private boolean presets = true;
	private String selectedPreset, colorChannel;
	private ColorControls colors;
	private final SkinPreview preview = new SkinPreview();

	private record Layout(
			int left, int span, int category, int middle, int listWidth, int right, int portrait) {}

	private Layout layout() {
		int span = Math.min(860, width - 24),
				left = (width - span) / 2,
				category = Math.min(132, Math.max(84, span / 4)),
				portrait = Math.min(208, Math.max(110, span / 3));
		return new Layout(
				left,
				span,
				category,
				left + category + 8,
				span - category - portrait - 16,
				left + span - portrait,
				portrait);
	}

	public WardrobeScreen(
			Screen back, CharacterAppearance appearance, Consumer<CharacterAppearance> apply) {
		super(back, ClientProfiles.text("wardrobe.title"));
		draft = appearance;
		this.apply = apply;
	}

	private void category(WardrobeSlot value) {
		slot = value;
		presets = false;
		rebuildWidgets();
	}

	private void openGallery(String selected) {
		minecraft.setScreen(
				new WardrobeGalleryScreen(
						this,
						draft,
						presets ? null : slot,
						selected,
						(id, value) -> {
							draft = value;
							selectedPreset = presets ? id : null;
						}));
	}

	@Override
	protected void init() {
		var l = layout();
		var channels = EditorUi.channels(draft, presets ? null : slot);
		int colorHeight = Math.min(112, Math.max(70, (height - 100) / 2)),
				colorTop = height - 44 - colorHeight;
		var presetButton =
				addRenderableWidget(
						EditorUi.button(
								l.left() + 8,
								40,
								l.category() - 16,
								18,
								ClientProfiles.text("wardrobe.presets"),
								() -> {
									presets = true;
									rebuildWidgets();
								}));
		presetButton.setTooltip(Tooltip.create(ClientProfiles.text("wardrobe.preset_hint")));
		EditorUi.selected(presetButton, presets);
		var slots = WardrobeSlot.values();
		int cell = (l.category() - 20) / 2;
		for (int i = 0; i < slots.length; i++) {
			var value = slots[i];
			boolean last = i == slots.length - 1 && slots.length % 2 == 1;
			var widget =
					addRenderableWidget(
							EditorUi.button(
									l.left() + 8 + (i % 2) * (cell + 4),
									62 + (i / 2) * 19,
									last ? l.category() - 16 : cell,
									17,
									ClientProfiles.text(
											"wardrobe.slot."
													+ value.name().toLowerCase(Locale.ROOT)),
									() -> category(value)));
			widget.active = presets || slot != value;
			widget.setTooltip(Tooltip.create(widget.getMessage()));
			EditorUi.selected(widget, !presets && slot == value);
		}
		Component categoryName =
				presets
						? ClientProfiles.text("wardrobe.presets")
						: ClientProfiles.text(
								"wardrobe.slot." + slot.name().toLowerCase(Locale.ROOT));
		String current = presets ? selectedPreset : draft.wardrobe().parts().get(slot);
		Component partName = ClientProfiles.text(presets ? "wardrobe.custom" : "wardrobe.none");
		if (presets) {
			for (var preset : WardrobeResources.presets())
				if (preset.id().equals(current)
						|| current == null
								&& AccountSkins.preset(preset.appearance()).equals(draft)) {
					current = preset.id();
					partName = Component.translatable(preset.name());
					break;
				}
		} else {
			var part = current == null ? null : WardrobeResources.part(current);
			if (part != null) partName = Component.translatable(part.name());
		}
		String gallerySelection = current == null ? WardrobeGalleryScreen.NONE : current;
		Component entryLabel =
				Component.translatable("terra_character.wardrobe.entry", categoryName, partName);
		var entry =
				addRenderableWidget(
						EditorUi.button(
								l.middle() + 8,
								40,
								l.listWidth() - 16,
								28,
								entryLabel,
								() -> openGallery(gallerySelection)));
		entry.setTooltip(Tooltip.create(entryLabel));
		colors = null;
		if (!channels.isEmpty()) {
			if (colorChannel == null || !channels.contains(colorChannel))
				colorChannel = channels.getFirst();
			addRenderableWidget(
					OptionDropdown.button(
							this,
							l.middle() + 8,
							colorTop - 44,
							l.listWidth() - 16,
							channels,
							colorChannel,
							EditorUi::channel,
							value -> {
								colorChannel = value;
								if (colors != null)
									colors.loadSilently(EditorUi.colorValue(draft, value));
							}));
			int half = (l.listWidth() - 20) / 2;
			addRenderableWidget(
					EditorUi.button(
							l.middle() + 8,
							colorTop - 22,
							half,
							18,
							ClientProfiles.text("color.reset"),
							() -> {
								draft = EditorUi.resetColor(draft, colorChannel);
								selectedPreset = null;
								rebuildWidgets();
							}));
			addRenderableWidget(
					EditorUi.button(
							l.middle() + 12 + half,
							colorTop - 22,
							half,
							18,
							ClientProfiles.text("random"),
							() -> {
								draft =
										EditorUi.color(
												draft,
												colorChannel,
												new Random().nextInt(0x1000000));
								colors.loadSilently(EditorUi.colorValue(draft, colorChannel));
								selectedPreset = null;
							}));
			colors =
					new ColorControls(
							l.middle() + 8,
							colorTop,
							l.listWidth() - 16,
							colorHeight,
							EditorUi.colorValue(draft, colorChannel),
							this::addRenderableWidget,
							rgb -> {
								draft = EditorUi.color(draft, colorChannel, rgb);
								selectedPreset = null;
							});
		}
		addRenderableWidget(
				EditorUi.button(
						width / 2 - 122,
						height - 28,
						118,
						18,
						ClientProfiles.text("appearance.apply"),
						() -> {
							if (colors != null && !colors.valid()) return;
							apply.accept(draft);
							onClose();
						}));
		addRenderableWidget(
				EditorUi.button(
						width / 2 + 4,
						height - 28,
						118,
						18,
						ClientProfiles.text("cancel"),
						this::onClose));
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if (preview.click(x, y, button)) {
			var hit = preview.takeConfirmed();
			if (hit != null) category(SkinPreview.slot(hit));
			return true;
		}
		return super.mouseClicked(x, y, button);
	}

	@Override
	public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		return preview.drag(button, dx) || super.mouseDragged(x, y, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		return preview.release(button) || super.mouseReleased(x, y, button);
	}

	@Override
	public void removed() {
		preview.close();
		super.removed();
	}

	@Override
	public void renderBackground(GuiGraphics g, int x, int y, float delta) {
		super.renderBackground(g, x, y, delta);
		var l = layout();
		EditorUi.panel(g, l.left(), 32, l.category(), height - 68);
		EditorUi.panel(g, l.middle(), 32, l.listWidth(), height - 68);
		EditorUi.panel(g, l.right(), 32, l.portrait(), height - 68);
	}

	@Override
	public void render(GuiGraphics g, int x, int y, float delta) {
		super.render(g, x, y, delta);
		var l = layout();
		int modelBottom = colors == null ? height - 92 : height - 56;
		int scale = Math.min(60, Math.max(12, (modelBottom - 50) / 2));
		preview.bounds(l.right() + l.portrait() / 2, modelBottom, scale);
		preview.interactionArea(
				l.right() + 8, 40, l.portrait() - 16, Math.max(1, modelBottom - 40));
		g.enableScissor(l.right() + 8, 40, l.right() + l.portrait() - 8, modelBottom);
		try {
			preview.render(g, draft, x, true, presets ? null : slot, null, null, presets);
		} finally {
			g.disableScissor();
		}
		if (colors == null)
			EditorUi.wrapped(
					g,
					ClientProfiles.text("color.no_part_channels"),
					l.right() + 8,
					height - 80,
					l.portrait() - 16,
					36,
					EditorUi.MUTED,
					false);
	}
}
