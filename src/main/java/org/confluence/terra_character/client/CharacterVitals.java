package org.confluence.terra_character.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;

import org.confluence.terra_character.api.model.CharacterStatistics;

import java.util.Locale;

/** Bounded, mirrored HUD icon columns for saved characters; no live player or world is needed. */
final class CharacterVitals {
	private static final ResourceLocation OVERLAY =
			ResourceLocation.parse("confluence:hud/overlay");
	private static final ResourceLocation HEART_CONTAINER =
			ResourceLocation.withDefaultNamespace("hud/heart/container");
	private static final ResourceLocation HEART_FULL =
			ResourceLocation.withDefaultNamespace("hud/heart/full");
	private static final ResourceLocation HEART_HALF =
			ResourceLocation.withDefaultNamespace("hud/heart/half");
	private static final int ICON_SIZE = 9, SPACING = 11;
	private static final int[] STAR_SHAPE = {8, 8, 127, 62, 28, 54, 65};
	private static final int[] HEALTH = {0xAB311E, 0x5D11BA, 0x41A9BA, 0x37C438, 0xEED536};
	private static final int[] HEALTH_LOW = {0xAB1F5D, 0x9D44AC, 0x12F7DD, 0x1FAB7F, 0xF7B60B};
	private static final int[] HEALTH_HIGH = {0xFFB5B5, 0xD6E7EB, 0xBDCED0, 0xD6EEAD, 0xEFF4CE};
	private static final int[] MANA = {0x5A82E2, 0xA248D7};
	private static final int[] MANA_LOW = {0x5D11BA, 0xAC1A91};
	private static final int[] MANA_HIGH = {0x90AFF8, 0xFF94BD};

	private CharacterVitals() {}

	static void render(
			GuiGraphics g, CharacterStatistics summary, int left, int right, int top, int height) {
		if (height < ICON_SIZE) return;
		boolean confluence = ModList.get().isLoaded("confluence");
		column(
				g,
				summary.health(),
				summary.maxHealth(),
				2,
				left,
				top,
				height,
				false,
				false,
				confluence);
		if (summary.hasMana() && summary.maxMana() > 0)
			column(
					g,
					summary.mana(),
					summary.maxMana(),
					20,
					right,
					top,
					height,
					true,
					true,
					confluence);
	}

	private static void column(
			GuiGraphics g,
			float current,
			float maximum,
			int unit,
			int x,
			int top,
			int height,
			boolean mirror,
			boolean mana,
			boolean confluence) {
		var pool = VitalIconLayers.of(current, maximum, unit);
		int columns = pool.icons() > 5 ? 2 : 1;
		int rows = Math.ceilDiv(pool.icons(), columns);
		boolean layered = pool.layer() > 1;
		int iconHeight = (rows - 1) * SPACING + ICON_SIZE;
		int contentHeight = iconHeight + (layered ? 12 : 0);
		float scale = Math.min(1, (float) height / contentHeight);
		g.pose().pushPose();
		g.pose()
				.translate(
						mirror ? x + ICON_SIZE - columns * SPACING + 2 : x,
						top + (height - contentHeight * scale) / 2,
						0);
		g.pose().scale(scale, scale, 1);
		try {
			for (int i = 0; i < pool.icons(); i++) {
				int iconX = (mirror ? columns - 1 - i / rows : i / rows) * SPACING;
				int iconY = (i % rows) * SPACING;
				float fill = pool.fill(i);
				if (confluence) {
					int v = mana ? 10 : 0;
					g.blitSprite(OVERLAY, 128, 128, 60, v, iconX, iconY, ICON_SIZE, ICON_SIZE);
					if (layered) overlay(g, iconX, iconY, 1, mana, pool.layer() - 1);
					overlay(g, iconX, iconY, fill, mana, pool.layer());
				} else if (mana) {
					star(g, iconX, iconY, 1, 0xFF343D52);
					if (layered)
						star(
								g,
								iconX,
								iconY,
								1,
								0xFF000000 | MANA[(int) ((pool.layer() - 2) % MANA.length)]);
					star(
							g,
							iconX,
							iconY,
							fill,
							0xFF000000 | MANA[(int) ((pool.layer() - 1) % MANA.length)]);
				} else {
					g.blitSprite(HEART_CONTAINER, iconX, iconY, ICON_SIZE, ICON_SIZE);
					if (layered) vanillaHeart(g, iconX, iconY, 1, pool.layer() - 1);
					vanillaHeart(g, iconX, iconY, fill, pool.layer());
				}
			}
			if (layered) {
				var font = Minecraft.getInstance().font;
				String label = String.format(Locale.ROOT, "×%.0f", pool.layer());
				int labelWidth = columns * SPACING - 2;
				if (font.width(label) > labelWidth)
					label =
							font.plainSubstrByWidth(
											label, Math.max(0, labelWidth - font.width("…")))
									+ "…";
				g.drawString(
						font,
						label,
						(labelWidth - font.width(label)) / 2,
						iconHeight + 3,
						EditorUi.MUTED,
						false);
			}
		} finally {
			g.pose().popPose();
		}
	}

	private static void star(GuiGraphics g, int x, int y, float fill, int color) {
		for (int row = 0; row < STAR_SHAPE.length; row++)
			for (int column = 0; column < 7; column++)
				if ((STAR_SHAPE[row] & (1 << column)) != 0 && column < Mth.ceil(7 * fill))
					g.fill(x + column + 1, y + row + 1, x + column + 2, y + row + 2, color);
	}

	private static void vanillaHeart(GuiGraphics g, int x, int y, float fill, double layer) {
		if (fill <= 0) return;
		int color = layer == 1 ? 0xFFFFFF : HEALTH_HIGH[(int) ((layer - 1) % HEALTH_HIGH.length)];
		g.setColor(
				((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, 1);
		try {
			g.blitSprite(
					fill > 0.5F ? HEART_FULL : HEART_HALF,
					ICON_SIZE,
					ICON_SIZE,
					0,
					0,
					x,
					y,
					Mth.ceil(ICON_SIZE * fill),
					ICON_SIZE);
		} finally {
			g.setColor(1, 1, 1, 1);
		}
	}

	private static void overlay(
			GuiGraphics g, int x, int y, float fill, boolean mana, double layer) {
		int v = mana ? 10 : 0;
		if (fill <= 0) return;
		int[] base = mana ? MANA : HEALTH,
				low = mana ? MANA_LOW : HEALTH_LOW,
				high = mana ? MANA_HIGH : HEALTH_HIGH;
		int index = (int) ((layer - 1) % base.length);
		try {
			int filledWidth = Mth.ceil(ICON_SIZE * fill);
			tinted(g, x, y, 0, v, filledWidth, base[index]);
			tinted(g, x, y, 20, v, filledWidth, low[index]);
			tinted(g, x, y, 40, v, filledWidth, high[index]);
		} finally {
			g.setColor(1, 1, 1, 1);
		}
	}

	private static void tinted(GuiGraphics g, int x, int y, int u, int v, int width, int color) {
		g.setColor(
				((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, 1);
		g.blitSprite(OVERLAY, 128, 128, u, v, x, y, width, ICON_SIZE);
	}
}
