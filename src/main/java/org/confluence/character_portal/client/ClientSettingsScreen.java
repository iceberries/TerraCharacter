package org.confluence.character_portal.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class ClientSettingsScreen extends ProfileScreen {
	private static final int ROW_STEP = 26;
	private static final int COLUMN_GAP = 8;

	ClientSettingsScreen(Screen parent) {
		super(parent, ClientProfiles.text("settings.title"));
	}

	@Override
	protected void init() {
		var options = minecraft.options;
		int columnWidth = Math.min(150, (width - 32) / 2);
		int left = (width - 2 * columnWidth - COLUMN_GAP) / 2;
		int right = left + columnWidth + COLUMN_GAP;
		int top = (height - 3 * ROW_STEP) / 2;

		button(left, top, columnWidth, Component.translatable("options.video"),
				() -> minecraft.setScreen(new VideoSettingsScreen(this, minecraft, options)));
		button(right, top, columnWidth, Component.translatable("controls.keybinds"),
				() -> minecraft.setScreen(new KeyBindsScreen(this, options)));
		button(left, top + ROW_STEP, columnWidth, Component.translatable("options.mouse_settings"),
				() -> minecraft.setScreen(new MouseSettingsScreen(this, options)));
		button(right, top + ROW_STEP, columnWidth, Component.translatable("options.language"),
				() -> minecraft.setScreen(
						new LanguageSelectScreen(this, options, minecraft.getLanguageManager())));
		button(left, top + 2 * ROW_STEP, columnWidth,
				Component.translatable("options.accessibility"),
				() -> minecraft.setScreen(new AccessibilityOptionsScreen(this, options)));
		button(right, top + 2 * ROW_STEP, columnWidth,
				Component.translatable("options.title"),
				() -> minecraft.setScreen(new OptionsScreen(this, options)));
		button((width - columnWidth) / 2, height - 28, columnWidth,
				CommonComponents.GUI_DONE, this::onClose);
	}
}
