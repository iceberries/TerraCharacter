package org.confluence.terra_character.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class ProfileScreen extends Screen {
	private final Screen back;

	protected final Screen getBack() {
		return back;
	}

	protected ProfileScreen(Screen back, Component title) {
		super(title);
		this.back = back;
	}

	protected Button button(int x, int y, int w, Component text, Runnable action) {
		return addRenderableWidget(EditorUi.button(x, y, w, 20, text, action));
	}

	protected void paragraph(
			GuiGraphics graphics, Component text, int x, int y, int width, int height) {
		EditorUi.wrapped(graphics, text, x, y, width, height, EditorUi.MUTED, true);
	}

	protected void run(IoAction action) {
		try {
			action.run();
		} catch (Exception ex) {
			ClientProfiles.error(this, ex);
		}
	}

	@FunctionalInterface
	protected interface IoAction {
		void run() throws Exception;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(back);
	}

	@Override
	public void render(GuiGraphics graphics, int x, int y, float delta) {
		super.render(graphics, x, y, delta);
		EditorUi.title(graphics, title, width);
	}
}
