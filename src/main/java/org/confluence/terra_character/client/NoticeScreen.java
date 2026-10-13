package org.confluence.terra_character.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class NoticeScreen extends ProfileScreen {
	private final Component message;
	private int scroll, maxScroll;

	public NoticeScreen(Screen back, Component title, Component message) {
		super(back, title);
		this.message = message;
	}

	@Override
	protected void init() {
		button(width / 2 - 75, height - 32, 150, ClientProfiles.text("back"), this::onClose);
	}

	@Override
	public void render(GuiGraphics g, int x, int y, float d) {
		super.render(g, x, y, d);
		int span = Math.min(456, width - 24),
				left = (width - span) / 2,
				top = 36,
				bottom = height - 44;
		EditorUi.panel(g, left, top, span, bottom - top);
		var lines = font.split(message, span - 32);
		int area = Math.max(1, bottom - top - 16);
		maxScroll = Math.max(0, lines.size() * 12 - area);
		scroll = Math.min(scroll, maxScroll);
		g.enableScissor(left + 8, top + 8, left + span - 8, bottom - 8);
		try {
			int row = top + 8 - scroll;
			for (var line : lines) {
				g.drawString(font, line, width / 2 - font.width(line) / 2, row, 0xFFFFDD99, false);
				row += 12;
			}
		} finally {
			g.disableScissor();
		}
		if (maxScroll > 0) {
			int thumb = Math.max(8, area * area / (lines.size() * 12)),
					thumbY = top + 8 + (area - thumb) * scroll / maxScroll;
			EditorUi.thumb(g, left + span - 12, thumbY, 4, thumb, false);
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		scroll = Mth.clamp(scroll - (int) (vertical * 24), 0, maxScroll);
		return true;
	}
}
