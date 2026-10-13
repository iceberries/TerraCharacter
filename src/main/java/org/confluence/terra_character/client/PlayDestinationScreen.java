package org.confluence.terra_character.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/** The selected identity is fixed before either local or network world selection. */
public final class PlayDestinationScreen extends ProfileScreen {
	public PlayDestinationScreen(Screen back) {
		super(back, ClientProfiles.text("destination"));
	}

	@Override
	protected void init() {
		int margin = 12, gap = 8, top = 52;
		int span = width - margin * 2, available = height - top - margin;
		int upperHeight = (available - gap) * 3 / 4, lowerHeight = available - gap - upperHeight;
		int leftWidth = (span - gap) / 2, rightX = margin + leftWidth + gap;
		addRenderableWidget(
				EditorUi.button(
						margin,
						top,
						leftWidth,
						upperHeight,
						ClientProfiles.text("destination.local"),
						() -> {
							MultiplayerClient.setSelection(null);
							minecraft.setScreen(new SelectWorldScreen(this));
						}));
		addRenderableWidget(
								EditorUi.button(
										rightX,
										top,
										span - leftWidth - gap,
										upperHeight,
										ClientProfiles.text("destination.remote"),
										() ->
												run(
														() -> {
															MultiplayerClient.setSelection(
																	ClientProfiles
																			.selectedProfile());
															minecraft.setScreen(
																	minecraft
																					.options
																					.skipMultiplayerWarning
																			? new JoinMultiplayerScreen(
																					this)
																			: new SafetyScreen(
																					this));
														})))
						.active =
				minecraft.allowsMultiplayer();
		addRenderableWidget(
				EditorUi.button(
						margin,
						top + upperHeight + gap,
						span,
						lowerHeight,
						ClientProfiles.text("back"),
						this::onClose));
	}

	@Override
	public void render(GuiGraphics g, int x, int y, float delta) {
		super.render(g, x, y, delta);
		try {
			EditorUi.text(
					g,
					Component.literal(ClientProfiles.selectedProfile().name()),
					12,
					32,
					width - 24,
					12,
					EditorUi.MUTED,
					true);
		} catch (Exception ignored) {
		}
	}
}
