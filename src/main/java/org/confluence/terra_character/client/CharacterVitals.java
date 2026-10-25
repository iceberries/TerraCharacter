package org.confluence.terra_character.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;

import org.confluence.terra_character.api.model.CharacterStatistics;

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

	private CharacterVitals() {}

	static void render(
			GuiGraphics g, CharacterStatistics summary, int left, int right, int top, int height) {
		if (height < ICON_SIZE) return;
		boolean confluence = ModList.get().isLoaded("confluence");
		column(
				g,
				summary.health(),
				summary.maxHealth(),
				confluence ? 4 : 2,
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
		int rows = Math.max(1, (height + SPACING - ICON_SIZE) / SPACING);
		int limit = Math.min(20, rows * 2);
		// Very large modded pools share capacity across the available icons instead of overflowing.
		double capacity = Math.max(unit, (double) maximum / limit);
		int count = Math.clamp((int) Math.ceil(maximum / capacity), 1, limit);
		int usedRows = Math.min(count, rows);
		int y = top + (height - ((usedRows - 1) * SPACING + ICON_SIZE)) / 2;
		for (int i = 0; i < count; i++) {
			int iconX = x + (mirror ? -1 : 1) * (i / rows) * SPACING;
			int iconY = y + (i % rows) * SPACING;
			double iconCapacity = Math.min(capacity, maximum - i * capacity);
			float fill = (float) Math.clamp((current - i * capacity) / iconCapacity, 0, 1);
			if (confluence) overlay(g, iconX, iconY, fill, mana);
			else if (mana) star(g, iconX, iconY, fill);
			else {
				g.blitSprite(HEART_CONTAINER, iconX, iconY, ICON_SIZE, ICON_SIZE);
				if (fill > 0)
					g.blitSprite(
							fill > 0.5F ? HEART_FULL : HEART_HALF,
							iconX,
							iconY,
							ICON_SIZE,
							ICON_SIZE);
			}
		}
	}

	private static void star(GuiGraphics g, int x, int y, float fill) {
		for (int row = 0; row < STAR_SHAPE.length; row++)
			for (int column = 0; column < 7; column++)
				if ((STAR_SHAPE[row] & (1 << column)) != 0)
					g.fill(
							x + column + 1,
							y + row + 1,
							x + column + 2,
							y + row + 2,
							column < Mth.ceil(7 * fill) ? 0xFF90AFF8 : 0xFF343D52);
	}

	private static void overlay(GuiGraphics g, int x, int y, float fill, boolean mana) {
		int v = mana ? 10 : 0;
		g.blitSprite(OVERLAY, 128, 128, 60, v, x, y, ICON_SIZE, ICON_SIZE);
		if (fill <= 0) return;
		g.enableScissor(x, y, x + Mth.ceil(ICON_SIZE * fill), y + ICON_SIZE);
		try {
			tinted(g, x, y, 0, v, mana ? 0x5A82E2 : 0xAB311E);
			tinted(g, x, y, 20, v, mana ? 0x5D11BA : 0xAB1F5D);
			tinted(g, x, y, 40, v, mana ? 0x90AFF8 : 0xFFB5B5);
		} finally {
			g.setColor(1, 1, 1, 1);
			g.disableScissor();
		}
	}

	private static void tinted(GuiGraphics g, int x, int y, int u, int v, int color) {
		g.setColor(
				((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, 1);
		g.blitSprite(OVERLAY, 128, 128, u, v, x, y, ICON_SIZE, ICON_SIZE);
	}
}
