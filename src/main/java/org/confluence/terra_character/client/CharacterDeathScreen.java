package org.confluence.terra_character.client;

import net.minecraft.client.gui.screens.TitleScreen;

public final class CharacterDeathScreen extends ProfileScreen {
	public CharacterDeathScreen() {
		super(null, ClientProfiles.text("hardcore.dead"));
	}

	@Override
	protected void init() {
		button(
				width / 2 - 100,
				height / 2,
				200,
				ClientProfiles.text("hardcore.leave"),
				this::leave);
	}

	private void leave() {
		if (MultiplayerClient.leave(this::leave)) return;
		if (minecraft.level != null) minecraft.level.disconnect();
		minecraft.disconnect();
		minecraft.setScreen(new CharacterSelectionScreen(new TitleScreen()));
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
