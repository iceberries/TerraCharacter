package org.confluence.terra_character.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import org.confluence.terra_character.network.JourneyActionPayload;

public final class JourneyScreen extends ProfileScreen {
	private EditBox item;

	public JourneyScreen(Screen back) {
		super(back, ClientProfiles.text("journey"));
	}

	private void send(String action) {
		PacketDistributor.sendToServer(
				new JourneyActionPayload(action, item == null ? "" : item.getValue()));
	}

	@Override
	protected void init() {
		var state = ClientState.state();
		var journey = state.getCompound("Journey");
		int span = Math.min(290, width - 24), left = (width - span) / 2, half = (span - 8) / 2;
		String[] actions = {
			"god", "flight", "noon", "midnight", "time", "weather", "clear", "research"
		};
		for (int i = 0; i < actions.length; i++) {
			String action = actions[i];
			String flag =
					switch (action) {
						case "god" -> journey.getBoolean("God") ? " ✓" : "";
						case "flight" -> journey.getBoolean("Flight") ? " ✓" : "";
						case "time" -> state.getBoolean("TimeFrozen") ? " ✓" : "";
						case "weather" -> state.getBoolean("WeatherFrozen") ? " ✓" : "";
						default -> "";
					};
			button(
					left + (i % 2) * (half + 8),
					36 + (i / 2) * 24,
					i % 2 == 0 ? half : span - half - 8,
					ClientProfiles.text("journey." + action).copy().append(flag),
					() -> send(action));
		}
		int duplicateWidth = Math.min(85, span / 3);
		item =
				new EditBox(
						font,
						left,
						142,
						span - duplicateWidth - 8,
						20,
						ClientProfiles.text("journey.item"));
		item.setMaxLength(256);
		item.setValue("minecraft:stone");
		addRenderableWidget(item);
		button(
				left + span - duplicateWidth,
				142,
				duplicateWidth,
				ClientProfiles.text("journey.duplicate"),
				() -> send("duplicate"));
		button(width / 2 - 75, height - 28, 150, ClientProfiles.text("back"), this::onClose);
	}

	@Override
	public void render(GuiGraphics g, int x, int y, float d) {
		super.render(g, x, y, d);
		var research = ClientState.state().getCompound("Journey").getCompound("Research");
		int span = Math.min(290, width - 24), row = 170;
		for (String key : research.getAllKeys().stream().sorted().toList()) {
			if (row + 10 > height - 42) break;
			EditorUi.text(
					g,
					Component.literal(key + "  " + research.getInt(key)),
					(width - span) / 2,
					row,
					span,
					10,
					EditorUi.MUTED,
					false);
			row += 12;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
