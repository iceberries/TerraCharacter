package org.confluence.terra_character.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** A keyboard-accessible slider that snaps to a finite set of choices. */
final class DiscreteSlider extends AbstractSliderButton {
	private final int count;
	private final IntFunction<Component> label;
	private final IntConsumer changed;

	DiscreteSlider(
			int x,
			int y,
			int width,
			int count,
			int selected,
			IntFunction<Component> label,
			IntConsumer changed) {
		super(x, y, width, 18, Component.empty(), selected / (double) (count - 1));
		this.count = count;
		this.label = label;
		this.changed = changed;
		updateMessage();
	}

	private int index() {
		return (int) Math.round(value * (count - 1));
	}

	@Override
	protected void updateMessage() {
		if (label != null) setMessage(label.apply(index()));
	}

	@Override
	protected void applyValue() {
		int index = index();
		value = index / (double) (count - 1);
		changed.accept(index);
		updateMessage();
	}

	@Override
	public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		EditorUi.row(g, x, y, w, h, false, isHoveredOrFocused());
		g.fill(x + 7, y + h - 5, x + w - 7, y + h - 3, 0xFF1D1D1D);
		for (int i = 0; i < count; i++) {
			int tick = x + 7 + i * (w - 14) / (count - 1);
			g.fill(tick, y + h - 6, tick + 1, y + h - 2, active ? EditorUi.GOLD : 0xFF777777);
		}
		int knob = x + 4 + (int) (value * (w - 14));
		EditorUi.thumb(g, knob, y + h - 9, 7, 8, active && isHoveredOrFocused());
		EditorUi.text(
				g,
				getMessage(),
				x + 6,
				y + 2,
				w - 12,
				10,
				active ? EditorUi.TEXT : EditorUi.MUTED,
				true);
	}

	@Override
	public boolean keyPressed(int key, int scan, int modifiers) {
		if (key == 262 || key == 263) {
			value = Mth.clamp(index() + (key == 262 ? 1 : -1), 0, count - 1) / (double) (count - 1);
			applyValue();
			return true;
		}
		return super.keyPressed(key, scan, modifiers);
	}
}
