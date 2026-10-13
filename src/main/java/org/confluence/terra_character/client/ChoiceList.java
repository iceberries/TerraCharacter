package org.confluence.terra_character.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** Scrollable, keyboard-accessible choices; every row is directly selectable. */
final class ChoiceList extends ObjectSelectionList<ChoiceList.Entry> {
	ChoiceList(int x, int top, int width, int height) {
		super(Minecraft.getInstance(), width, height, top, 24);
		setX(x);
	}

	void choice(Component label, Runnable action, BooleanSupplier selected) {
		addEntry(new Entry(label, action, selected));
	}

	@Override
	public int getRowWidth() {
		return Math.max(1, getWidth() - (getMaxScroll() > 0 ? 28 : 16));
	}

	@Override
	public int getRowLeft() {
		return getX() + 8;
	}

	@Override
	public int getRowTop(int index) {
		return super.getRowTop(index) + 4;
	}

	@Override
	protected int getMaxPosition() {
		return super.getMaxPosition() + 8;
	}

	@Override
	protected int getScrollbarPosition() {
		return getX() + width - 14;
	}

	@Override
	protected void renderListBackground(GuiGraphics g) {
		EditorUi.panel(g, getX(), getY(), getWidth(), getHeight());
	}

	@Override
	protected void renderListSeparators(GuiGraphics g) {}

	@Override
	protected void renderSelection(
			GuiGraphics g, int top, int width, int height, int outerColor, int innerColor) {}

	final class Entry extends ObjectSelectionList.Entry<Entry> {
		private final Component label;
		private final Runnable action;
		private final BooleanSupplier selected;

		Entry(Component label, Runnable action, BooleanSupplier selected) {
			this.label = label;
			this.action = action;
			this.selected = selected;
		}

		@Override
		public Component getNarration() {
			return label;
		}

		private void choose() {
			setSelected(this);
			action.run();
		}

		@Override
		public boolean mouseClicked(double x, double y, int button) {
			if (button != 0) return false;
			choose();
			return true;
		}

		@Override
		public boolean keyPressed(int key, int scan, int modifiers) {
			if (key == 257 || key == 335 || key == 32) {
				choose();
				return true;
			}
			return false;
		}

		@Override
		public void render(
				GuiGraphics g,
				int index,
				int top,
				int left,
				int width,
				int height,
				int mouseX,
				int mouseY,
				boolean hovered,
				float delta) {
			EditorUi.row(
					g,
					left,
					top,
					width,
					height,
					selected.getAsBoolean(),
					hovered || (ChoiceList.this.isFocused() && getSelected() == this));
			EditorUi.text(
					g, label, left + 8, top + 3, width - 16, height - 6, EditorUi.TEXT, false);
		}
	}
}
