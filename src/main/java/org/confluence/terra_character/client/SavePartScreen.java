package org.confluence.terra_character.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.confluence.terra_character.api.model.WardrobeSlot;

import java.util.List;
import java.util.Locale;

/** Slot choices are prevalidated by the editor; saving validates the captured pixels again. */
final class SavePartScreen extends ProfileScreen {
	@FunctionalInterface
	interface SaveAction {
		void save(String name, WardrobeSlot slot) throws Exception;
	}

	private final List<WardrobeSlot> slots;
	private final SaveAction save;
	private WardrobeSlot slot;
	private String name = "";
	private Component error = Component.empty();

	SavePartScreen(Screen back, List<WardrobeSlot> slots, SaveAction save) {
		super(back, ClientProfiles.text("skin.selection.save"));
		this.slots = List.copyOf(slots);
		if (slots.isEmpty()) throw new IllegalArgumentException("No eligible part slot");
		this.slot = slots.getFirst();
		this.save = save;
	}

	@Override
	protected void init() {
		int span = Math.min(260, width - 32), left = (width - span) / 2, top = height / 2 - 55;
		var input =
				new EditBox(font, left, top, span, 18, ClientProfiles.text("skin.selection.name"));
		input.setMaxLength(80);
		input.setHint(ClientProfiles.text("skin.selection.name"));
		input.setValue(name);
		input.setResponder(value -> name = value);
		addRenderableWidget(input);
		addRenderableWidget(
				OptionDropdown.button(
						this,
						left,
						top + 26,
						span,
						slots,
						slot,
						value ->
								ClientProfiles.text(
										"wardrobe.slot." + value.name().toLowerCase(Locale.ROOT)),
						value -> slot = value));
		button(
				left,
				top + 80,
				(span - 8) / 2,
				ClientProfiles.text("save"),
				() -> {
					if (name.isBlank()) {
						error = ClientProfiles.text("skin.selection.name");
						return;
					}
					try {
						save.save(name, slot);
						onClose();
					} catch (Exception ex) {
						error = ClientProfiles.text("skin.selection.save_failed");
						WardrobeResources.warn("save_selection", ex);
					}
				});
		button(
				left + (span + 8) / 2,
				top + 80,
				(span - 8) / 2,
				ClientProfiles.text("cancel"),
				this::onClose);
	}

	@Override
	public void render(GuiGraphics g, int x, int y, float delta) {
		super.render(g, x, y, delta);
		EditorUi.text(g, error, 16, height / 2 + 2, width - 32, 18, 0xFFFF7777, true);
	}
}
