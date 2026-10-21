package org.confluence.terra_character.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;

/** Same small pixel star on character cards and the existing world-list rows. */
public final class StarButton extends AbstractButton {
	private static final int[] SHAPE = {8, 8, 127, 62, 28, 54, 65};
	private final boolean starred;
	private final Runnable action;

	StarButton(int x, int y, boolean starred, Runnable action) {
		super(x, y, 18, 18, ClientProfiles.text(starred ? "star.remove" : "star.add"));
		this.starred = starred;
		this.action = action;
		setTooltip(Tooltip.create(getMessage()));
	}

	public static void draw(GuiGraphics g, int x, int y, boolean starred, boolean hovered) {
		EditorUi.row(g, x, y, 18, 18, false, hovered);
		for (int row = 0; row < SHAPE.length; row++)
			for (int column = 0; column < 7; column++)
				if ((SHAPE[row] & (1 << column)) != 0)
					g.fill(
							x + 5 + column,
							y + 5 + row,
							x + 6 + column,
							y + 6 + row,
							starred ? EditorUi.GOLD : EditorUi.MUTED);
	}

	@Override
	protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
		draw(g, getX(), getY(), starred, isHoveredOrFocused());
	}

	@Override
	public void onPress() {
		action.run();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		defaultButtonNarrationText(out);
	}
}
