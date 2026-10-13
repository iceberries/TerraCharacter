package org.confluence.terra_character.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** One shared RGB/HSV/hex editor for every appearance and brush color. */
final class ColorControls {
	private int rgb;
	private float hue;
	private final IntConsumer apply;
	private EditBox hex;
	private final Channel[] channels = new Channel[3];
	private boolean syncing;

	private final int x, y, width, height;

	ColorControls(
			int x,
			int y,
			int width,
			int height,
			int rgb,
			Consumer<AbstractWidget> mount,
			IntConsumer apply) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = Math.max(70, height);
		this.rgb = rgb & 0xFFFFFF;
		this.apply = apply;
		hue = Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null)[0];
		boolean compact = width < 156;
		int boardWidth = compact ? Math.max(1, width - 12) : Math.min(104, (width - 18) / 2);
		int boardHeight = compact ? this.height - 54 : this.height;
		mount.accept(new Board(x, y, boardWidth, boardHeight, false));
		mount.accept(new Board(x + boardWidth + 4, y, 8, boardHeight, true));
		int right = compact ? x : x + boardWidth + 16,
				w = compact ? width : width - boardWidth - 16;
		int channelTop = compact ? y + boardHeight + 4 : y,
				step = compact ? 11 : (this.height - 19) / 3;
		for (int i = 0; i < 3; i++)
			mount.accept(
					channels[i] =
							new Channel(right, channelTop + i * step, w, compact ? 11 : 15, i));
		hex =
				new EditBox(
						Minecraft.getInstance().font,
						right,
						y + this.height - 16,
						w,
						16,
						ClientProfiles.text("color.code"));
		hex.setMaxLength(7);
		hex.setFilter(value -> value.matches("#?[0-9a-fA-F]{0,6}"));
		hex.setValue(String.format("#%06X", rgb));
		hex.setTooltip(Tooltip.create(ClientProfiles.text("color.code")));
		hex.setResponder(
				value -> {
					String code = value.replace("#", "");
					if (!syncing && code.length() == 6)
						setColor(Integer.parseInt(code, 16), true, false);
				});
		mount.accept(hex);
	}

	boolean valid() {
		return hex.getValue().matches("#?[0-9a-fA-F]{6}");
	}

	boolean editingCode() {
		return hex.isFocused();
	}

	void blurCode() {
		hex.setFocused(false);
	}

	void load(int rgb) {
		setColor(rgb, true, true);
	}

	void loadSilently(int rgb) {
		setColor(rgb, true, true, false);
	}

	private void setColor(int value, boolean updateHue, boolean updateHex) {
		setColor(value, updateHue, updateHex, true);
	}

	private void setColor(int value, boolean updateHue, boolean updateHex, boolean notify) {
		rgb = value & 0xFFFFFF;
		if (updateHue) {
			var hsv = Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
			if (hsv[1] > 0) hue = hsv[0];
		}
		syncing = true;
		for (var slider : channels) if (slider != null) slider.sync();
		if (updateHex && hex != null) hex.setValue(String.format("#%06X", rgb));
		syncing = false;
		if (notify) apply.accept(rgb);
	}

	private final class Channel extends AbstractSliderButton {
		private final int index;

		Channel(int x, int y, int w, int h, int index) {
			super(x, y, w, h, Component.empty(), (rgb >> (16 - index * 8) & 255) / 255.0);
			this.index = index;
			updateMessage();
		}

		void sync() {
			value = (rgb >> (16 - index * 8) & 255) / 255.0;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal("RGB".charAt(index) + ": " + Math.round(value * 255)));
		}

		@Override
		protected void applyValue() {
			int shift = 16 - index * 8;
			setColor(
					(rgb & ~(255 << shift)) | ((int) Math.round(value * 255) << shift), true, true);
		}

		@Override
		public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			g.fill(x, y, x + w, y + h, 0xFF323232);
			boolean compact = h < 15;
			EditorUi.text(
					g,
					getMessage(),
					x + 3,
					y + 1,
					compact ? w / 2 - 4 : w - 6,
					9,
					EditorUi.TEXT,
					false);
			int color = index == 0 ? 0xFFE57B86 : index == 1 ? 0xFF83D2AC : 0xFF85B9EE;
			int trackY = compact ? y + h / 2 : y + h - 3,
					trackLeft = compact ? x + w / 2 + 2 : x + 3;
			g.fill(trackLeft, trackY, x + w - 3, trackY + 2, 0xFF666666);
			int knob = trackLeft + (int) (value * Math.max(1, x + w - 3 - trackLeft));
			g.fill(trackLeft, trackY, knob, trackY + 2, color);
			g.fill(knob - 1, trackY - 1, knob + 1, trackY + 3, 0xFFF4F7FA);
			if (isFocused()) g.renderOutline(x, y, w, h, EditorUi.GOLD);
		}
	}

	private final class Board extends AbstractWidget {
		private final boolean hueBar;

		Board(int x, int y, int w, int h, boolean hueBar) {
			super(x, y, w, h, ClientProfiles.text(hueBar ? "color.hue" : "color.board"));
			this.hueBar = hueBar;
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			defaultButtonNarrationText(out);
		}

		@Override
		public void onClick(double x, double y) {
			pick(x, y);
		}

		@Override
		protected void onDrag(double x, double y, double dx, double dy) {
			pick(x, y);
		}

		private void pick(double x, double y) {
			var hsv = Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
			if (hueBar) {
				hue = (float) Mth.clamp((y - getY()) / (getHeight() - 1), 0, 1);
			} else {
				hsv[1] = (float) Mth.clamp((x - getX()) / (getWidth() - 1), 0, 1);
				hsv[2] = 1 - (float) Mth.clamp((y - getY()) / (getHeight() - 1), 0, 1);
			}
			setColor(Color.HSBtoRGB(hue, hsv[1], hsv[2]), false, true);
		}

		@Override
		public boolean keyPressed(int key, int scan, int modifiers) {
			if (key < 262 || key > 265) return super.keyPressed(key, scan, modifiers);
			var hsv = Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
			if (hueBar) hue = (hue + (key == 265 || key == 263 ? -0.01f : 0.01f) + 1) % 1;
			else if (key == 262 || key == 263)
				hsv[1] = Mth.clamp(hsv[1] + (key == 262 ? 0.02f : -0.02f), 0, 1);
			else hsv[2] = Mth.clamp(hsv[2] + (key == 265 ? 0.02f : -0.02f), 0, 1);
			setColor(Color.HSBtoRGB(hue, hsv[1], hsv[2]), false, true);
			return true;
		}

		@Override
		protected void renderWidget(GuiGraphics g, int x, int y, float delta) {
			// Small cells keep the color field inexpensive at every GUI scale.
			for (int py = 0; py < getHeight(); py += 2)
				for (int px = 0; px < getWidth(); px += 2) {
					int c =
							Color.HSBtoRGB(
									hueBar ? (float) py / (getHeight() - 1) : hue,
									hueBar ? 1 : (float) px / (getWidth() - 1),
									hueBar ? 1 : 1 - (float) py / (getHeight() - 1));
					g.fill(
							getX() + px,
							getY() + py,
							getX() + Math.min(px + 2, getWidth()),
							getY() + Math.min(py + 2, getHeight()),
							c);
				}
			var hsv = Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
			int cx = getX() + (hueBar ? getWidth() / 2 : Math.round(hsv[1] * (getWidth() - 1))),
					cy = getY() + Math.round((hueBar ? hue : 1 - hsv[2]) * (getHeight() - 1));
			g.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
			try {
				g.renderOutline(cx - 2, cy - 2, 5, 5, 0xFF000000);
				g.renderOutline(cx - 1, cy - 1, 3, 3, 0xFFFFFFFF);
			} finally {
				g.disableScissor();
			}
		}
	}
}
