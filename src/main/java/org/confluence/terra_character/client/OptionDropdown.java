package org.confluence.terra_character.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** A modal dropdown keeps expanded choices above other widgets and captures outside clicks. */
final class OptionDropdown<T> extends Screen {
	private final Screen back;
	private final int anchorX, anchorY, span;
	private final List<T> values;
	private final T selected;
	private final Function<T, Component> label;
	private final Consumer<T> changed;
	private int popupX, popupY, popupWidth, popupHeight;

	private OptionDropdown(
			Screen back,
			int x,
			int y,
			int width,
			List<T> values,
			T selected,
			Function<T, Component> label,
			Consumer<T> changed) {
		super(label.apply(selected));
		this.back = back;
		anchorX = x;
		anchorY = y;
		span = width;
		this.values = List.copyOf(values);
		this.selected = selected;
		this.label = label;
		this.changed = changed;
	}

	static <T> Button button(
			Screen back,
			int x,
			int y,
			int width,
			List<T> values,
			T selected,
			Function<T, Component> label,
			Consumer<T> changed) {
		return EditorUi.button(
				x,
				y,
				width,
				18,
				label.apply(selected).copy().append(" ▾"),
				() ->
						Minecraft.getInstance()
								.setScreen(
										new OptionDropdown<>(
												back, x, y, width, values, selected, label,
												changed)));
	}

	@Override
	protected void init() {
		// Opening a Screen calls the parent's removed(); recreate its preview before drawing it.
		back.init(minecraft, width, height);
		// Parent widgets are recreated on resize. Follow the new trigger, rather than old pixel
		// coordinates.
		String trigger = label.apply(selected).copy().append(" ▾").getString();
		var anchor =
				back.children().stream()
						.filter(
								w ->
										w instanceof Button b
												&& b.getMessage().getString().equals(trigger))
						.map(Button.class::cast)
						.findFirst()
						.orElse(null);
		int currentX = anchor == null ? anchorX : anchor.getX(),
				currentY = anchor == null ? anchorY : anchor.getY();
		int h = Math.min(values.size() * 24 + 12, height - 24),
				top = Math.max(12, Math.min(currentY + 20, height - h - 12));
		popupWidth = Math.min(anchor == null ? span : anchor.getWidth(), width - 24);
		popupX = Math.max(12, Math.min(currentX, width - popupWidth - 12));
		popupY = top;
		popupHeight = h;
		var list = new ChoiceList(popupX, top, popupWidth, h);
		for (T value : values)
			list.choice(
					label.apply(value),
					() -> {
						changed.accept(value);
						onClose();
					},
					() -> value.equals(selected));
		addRenderableWidget(list);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(back);
	}

	@Override
	public void renderBackground(GuiGraphics g, int x, int y, float delta) {}

	@Override
	public void render(GuiGraphics g, int x, int y, float delta) {
		back.render(g, -1, -1, delta);
		g.fill(0, 0, width, height, 0x40000000);
		g.pose().pushPose();
		g.pose().translate(0, 0, 400);
		EditorUi.panel(g, popupX, popupY, popupWidth, popupHeight);
		super.render(g, x, y, delta);
		g.flush();
		g.pose().popPose();
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if (super.mouseClicked(x, y, button)) return true;
		onClose();
		return true;
	}
}
