package org.confluence.terra_character.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.confluence.terra_character.api.model.CharacterAppearance;
import org.confluence.terra_character.api.model.WardrobeSlot;

import java.util.*;

/** Shared Confluence-style pixel frames, with a bundled atlas for standalone installations. */
final class EditorUi {
	static final int TEXT = 0xFFF0F0F0, MUTED = 0xFFB8B8B8, GOLD = 0xFFFFD15C;

	/** Text is fitted in GUI pixels and clipped to the content rectangle, including its shadow. */
	static void text(
			GuiGraphics g,
			Component label,
			int x,
			int y,
			int width,
			int height,
			int color,
			boolean centered) {
		if (width <= 0 || height <= 0) return;
		var font = Minecraft.getInstance().font;
		String value = label.getString();
		if (font.width(value) > width) {
			String suffix = "…";
			value =
					font.plainSubstrByWidth(value, Math.max(0, width - font.width(suffix)))
							+ suffix;
		}
		g.enableScissor(x, y, x + width, y + height);
		try {
			g.drawString(
					font,
					value,
					centered ? x + (width - font.width(value)) / 2 : x,
					y + Math.max(0, (height - font.lineHeight) / 2),
					color,
					false);
		} finally {
			g.disableScissor();
		}
	}

	static void wrapped(
			GuiGraphics g,
			Component label,
			int x,
			int y,
			int width,
			int height,
			int color,
			boolean centered) {
		if (width <= 0 || height <= 0) return;
		var font = Minecraft.getInstance().font;
		g.enableScissor(x, y, x + width, y + height);
		try {
			int row = y;
			for (var line : font.split(label, width)) {
				if (row + font.lineHeight > y + height) break;
				g.drawString(
						font,
						line,
						centered ? x + (width - font.width(line)) / 2 : x,
						row,
						color,
						false);
				row += font.lineHeight + 2;
			}
		} finally {
			g.disableScissor();
		}
	}

	private static final ResourceLocation ATLAS =
			ResourceLocation.parse("terra_character:textures/gui/confluence_achievement.png");

	static void panel(GuiGraphics g, int x, int y, int width, int height) {
		patch(g, x, y, width, height, 0, 20, 208, 184, 7);
	}

	static void frame(
			GuiGraphics g, int x, int y, int width, int height, boolean selected, boolean hovered) {
		panel(g, x, y, width, height);
		if (selected || hovered)
			g.renderOutline(x + 1, y + 1, width - 2, height - 2, selected ? GOLD : 0xFFBDA565);
	}

	static void row(
			GuiGraphics g, int x, int y, int width, int height, boolean selected, boolean hovered) {
		patch(g, x, y, width, height, 52, 208, 150, 13, 3);
		if (selected) {
			g.fill(x + 2, y + 2, x + 4, y + height - 2, GOLD);
			g.renderOutline(x, y, width, height, 0xFFAA8535);
		} else if (hovered) g.renderOutline(x, y, width, height, 0xFFBEBEBE);
	}

	static void thumb(GuiGraphics g, int x, int y, int width, int height, boolean highlighted) {
		patch(g, x, y, width, height, highlighted ? 218 : 210, 10, 7, 15, 2);
	}

	static void title(GuiGraphics g, Component title, int screenWidth) {
		var font = Minecraft.getInstance().font;
		int width = Math.min(screenWidth - 24, Math.max(110, font.width(title) + 32)),
				x = (screenWidth - width) / 2;
		row(g, x, 6, width, 20, false, false);
		g.fill(x + 5, 24, x + width - 5, 25, 0xFFAA8535);
		text(g, title, x + 6, 10, width - 12, 12, TEXT, true);
	}

	private static void patch(
			GuiGraphics g,
			int x,
			int y,
			int width,
			int height,
			int u,
			int v,
			int sourceWidth,
			int sourceHeight,
			int border) {
		if (width <= 0 || height <= 0) return;
		int b = Math.min(border, Math.min(width, height) / 2);
		int[] sx = {0, border, sourceWidth - border}, sy = {0, border, sourceHeight - border};
		int[] sw = {border, sourceWidth - border * 2, border},
				sh = {border, sourceHeight - border * 2, border};
		int[] dx = {x, x + b, x + width - b},
				dy = {y, y + b, y + height - b},
				dw = {b, width - b * 2, b},
				dh = {b, height - b * 2, b};
		for (int row = 0; row < 3; row++)
			for (int col = 0; col < 3; col++)
				if (dw[col] > 0 && dh[row] > 0)
					g.blit(
							ATLAS,
							dx[col],
							dy[row],
							dw[col],
							dh[row],
							u + sx[col],
							v + sy[row],
							sw[col],
							sh[row],
							256,
							256);
	}

	static Button button(int x, int y, int width, int height, Component label, Runnable action) {
		return new ThemedButton(x, y, width, height, label, action);
	}

	static void selected(Button button, boolean selected) {
		if (button instanceof ThemedButton themed) themed.selected = selected;
	}

	private static final class ThemedButton extends Button {
		private boolean selected;

		ThemedButton(int x, int y, int width, int height, Component label, Runnable action) {
			super(x, y, width, height, label, b -> action.run(), supplier -> supplier.get());
			if (Minecraft.getInstance().font.width(label) > width - (width < 40 ? 6 : 12))
				setTooltip(Tooltip.create(label));
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			boolean highlighted = selected || (active && isHoveredOrFocused());
			if (h > 32) frame(g, x, y, w, h, highlighted, false);
			else if (highlighted) patch(g, x, y, w, h, 209, 31, 38, 38, 4);
			else row(g, x, y, w, h, false, false);
			if (!active && !selected) g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x55000000);
			int padding = w < 40 ? 3 : 6;
			text(
					g,
					getMessage(),
					x + padding,
					y + 3,
					w - 2 * padding,
					h - 6,
					active || selected ? TEXT : 0xFF999999,
					true);
		}
	}

	static List<String> channels(CharacterAppearance appearance) {
		return channels(appearance, null);
	}

	static List<String> channels(CharacterAppearance appearance, WardrobeSlot slot) {
		var channels = new TreeSet<String>();
		channels.addAll(BaseSkinDyes.channels(appearance, slot));
		if (!CharacterSkins.textured(appearance))
			for (String name : List.of("skin", "hair", "eyes", "shirt", "pants", "shoes"))
				channels.add("terra_character:" + name);
		for (var entry : appearance.wardrobe().parts().entrySet()) {
			var part = WardrobeResources.part(entry.getValue());
			if (slot != null
					&& entry.getKey() != slot
					&& !entry.getKey().occupied().contains(slot)
					&& !slot.occupied().contains(entry.getKey())) continue;
			if (part != null)
				for (var fragment : part.fragments())
					if (fragment.tint() != null
							&& (fragment.body().equals("any")
									|| fragment.body()
											.equals(
													appearance.bodyStyle().endsWith(":slim")
															? "slim"
															: "wide")))
						channels.add(fragment.tint());
		}
		return List.copyOf(channels);
	}

	static Component channel(String id) {
		if (id.startsWith("terra_character:account_"))
			return ClientProfiles.text("channel." + id.substring("terra_character:".length()));
		if (id.startsWith("terra_character:base_"))
			return ClientProfiles.text(
					"wardrobe.slot." + id.substring("terra_character:base_".length()));
		return id.startsWith("terra_character:")
						&& List.of("skin", "hair", "eyes", "shirt", "pants", "shoes")
								.contains(id.substring(16))
				? ClientProfiles.text("channel." + id.substring(16))
				: Component.literal(id);
	}

	static int colorValue(CharacterAppearance appearance, String channel) {
		return appearance
				.colors()
				.getOrDefault(channel, BaseSkinDyes.reference(appearance, channel));
	}

	static CharacterAppearance resetColor(CharacterAppearance appearance, String channel) {
		var colors = new HashMap<>(appearance.colors());
		colors.remove(channel);
		return new CharacterAppearance(
				appearance.bodyStyle(),
				appearance.hairStyle(),
				colors,
				appearance.paint(),
				appearance.wardrobe(),
				appearance.dyeMasks(),
				appearance.baseSkin());
	}

	static CharacterAppearance color(CharacterAppearance appearance, String channel, int rgb) {
		var colors = new HashMap<>(appearance.colors());
		if (colors.size() >= 32 && !colors.containsKey(channel)) return appearance;
		colors.put(channel, rgb);
		return new CharacterAppearance(
				appearance.bodyStyle(),
				appearance.hairStyle(),
				colors,
				appearance.paint(),
				appearance.wardrobe(),
				appearance.dyeMasks(),
				appearance.baseSkin());
	}

	private EditorUi() {}
}
