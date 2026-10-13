package org.confluence.terra_character.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.Difficulty;

public final class CharacterWorldOptionsScreen extends ProfileScreen {
	private final CreateWorldScreen creation;

	public CharacterWorldOptionsScreen(CreateWorldScreen creation) {
		super(creation, ClientProfiles.text("world.options"));
		this.creation = creation;
	}

	@Override
	protected void init() {
		var state = creation.getUiState();
		button(
								width / 2 - 120,
								60,
								240,
								ClientProfiles.text(
										"world.difficulty", state.getDifficulty().getDisplayName()),
								() -> {
									state.setDifficulty(
											Difficulty.byId(
													(state.getDifficulty().getId() + 1) % 4));
									rebuildWidgets();
								})
						.active =
				WorldEditors.getIntegration() == null;
		if (WorldEditors.getIntegration() != null)
			button(
					width / 2 - 120,
					88,
					240,
					ClientProfiles.text("world.integration"),
					() ->
							minecraft.setScreen(
									WorldEditors.getIntegration().create(this, creation)));
		button(width / 2 - 75, height - 32, 150, ClientProfiles.text("back"), this::onClose);
	}

	@Override
	public void render(GuiGraphics g, int x, int y, float d) {
		super.render(g, x, y, d);
		EditorUi.wrapped(
				g,
				ClientProfiles.text("world.options.help"),
				12,
				34,
				width - 24,
				22,
				EditorUi.MUTED,
				true);
	}
}
