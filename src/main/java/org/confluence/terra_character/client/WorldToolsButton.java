package org.confluence.terra_character.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;

/** Compact world utilities entry, with a pixel toolbox icon. */
final class WorldToolsButton extends AbstractButton {
	private final Runnable action;

	WorldToolsButton(int x, int y, Runnable action) {
		super(x, y, 20, 20, ClientProfiles.text("world.tools"));
		this.action = action;
		setTooltip(Tooltip.create(getMessage()));
	}

	@Override
	public void onPress() {
		action.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		int x = getX(), y = getY();
		EditorUi.row(graphics, x, y, 20, 20, false, isHoveredOrFocused());
		// Handle, metal rim, red case and central latch.
		graphics.fill(x + 7, y + 4, x + 13, y + 8, 0xFFD9E0E6);
		graphics.fill(x + 8, y + 5, x + 12, y + 8, 0xFF444444);
		graphics.fill(x + 3, y + 7, x + 17, y + 16, 0xFF25262B);
		graphics.fill(x + 4, y + 8, x + 16, y + 15, 0xFFB94B3C);
		graphics.fill(x + 4, y + 10, x + 16, y + 11, 0xFF6F302C);
		graphics.fill(x + 9, y + 9, x + 11, y + 12, 0xFFF0CE76);
	}
}
