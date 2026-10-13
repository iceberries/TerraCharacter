package org.confluence.terra_character_confluence.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;

import org.confluence.terra_character.client.*;
import org.confluence.terra_character_confluence.world.ConfluenceWorldOptions;

import java.util.Locale;

public final class ConfluenceOptionsScreen extends ProfileScreen {
	private ConfluenceWorldOptions.Difficulty difficulty;
	private ConfluenceWorldOptions.Evil evil;
	private final CreateWorldScreen creation;

	public ConfluenceOptionsScreen(Screen back, CreateWorldScreen creation) {
		super(back, text("options"));
		this.creation = creation;
		difficulty =
				ConfluenceWorldOptions.Difficulty.valueOf(
						ClientProfiles.getWorldOption(
										ConfluenceWorldOptions.DIFFICULTY_KEY, "classic")
								.toUpperCase(Locale.ROOT));
		evil =
				ConfluenceWorldOptions.Evil.valueOf(
						ClientProfiles.getWorldOption(ConfluenceWorldOptions.EVIL_KEY, "random")
								.toUpperCase(Locale.ROOT));
	}

	private static Component text(String key) {
		return Component.translatable("terra_character_confluence." + key);
	}

	@Override
	protected void init() {
		button(
				width / 2 - 120,
				60,
				240,
				text("difficulty." + difficulty.name().toLowerCase(Locale.ROOT)),
				() -> {
					difficulty =
							ConfluenceWorldOptions.Difficulty.values()[
									(difficulty.ordinal() + 1) % 3];
					rebuildWidgets();
				});
		button(
				width / 2 - 120,
				88,
				240,
				text("evil." + evil.name().toLowerCase(Locale.ROOT)),
				() -> {
					evil = ConfluenceWorldOptions.Evil.values()[(evil.ordinal() + 1) % 3];
					rebuildWidgets();
				});
		button(
				width / 2 - 75,
				height - 32,
				150,
				ClientProfiles.text("save"),
				() -> {
					ClientProfiles.setWorldOption(
							ConfluenceWorldOptions.DIFFICULTY_KEY,
							difficulty.name().toLowerCase(Locale.ROOT));
					ClientProfiles.setWorldOption(
							ConfluenceWorldOptions.EVIL_KEY, evil.name().toLowerCase(Locale.ROOT));
					creation.getUiState()
							.setDifficulty(
									switch (difficulty) {
										case CLASSIC -> Difficulty.EASY;
										case EXPERT -> Difficulty.NORMAL;
										case MASTER -> Difficulty.HARD;
									});
					minecraft.setScreen(getBack());
				});
	}

	@Override
	public void render(GuiGraphics g, int x, int y, float d) {
		super.render(g, x, y, d);
		paragraph(g, text("seed.help"), 12, 124, width - 24, Math.max(0, height - 168));
	}
}
