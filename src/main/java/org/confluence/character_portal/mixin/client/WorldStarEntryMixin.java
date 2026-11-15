package org.confluence.character_portal.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

import org.confluence.character_portal.client.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;

@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class WorldStarEntryMixin implements WorldStarEntry {
	@Shadow @Final private LevelSummary summary;
	@Shadow @Final private Minecraft minecraft;
	@Shadow private long lastClickTime;
	@Unique private static final int STAR_SIZE = 18;
	@Unique private static final int STAR_MARGIN = 5;
	@Unique private WorldSelectionList characterPortal$list;
	@Unique private int characterPortal$starX, characterPortal$starY, characterPortal$textWidth;
	@Unique private boolean characterPortal$rendered;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void owner(
			CallbackInfo ci, @Local(argsOnly = true, ordinal = 0) WorldSelectionList list) {
		characterPortal$list = list;
	}

	@Override
	public String characterPortal$worldId() {
		return summary.getLevelId();
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void bounds(
			GuiGraphics g,
			int index,
			int top,
			int left,
			int width,
			int height,
			int mouseX,
			int mouseY,
			boolean hovered,
			float delta,
			CallbackInfo ci) {
		// Match AbstractSelectionList.renderSelection: the content's left is inset by two
		// pixels, so left + width is not the right edge of the selection frame.
		int rowRight = characterPortal$list.getX() + (characterPortal$list.getWidth() + width) / 2;
		characterPortal$starX = rowRight - STAR_MARGIN - STAR_SIZE;
		characterPortal$starY = top + Math.max(0, (height - STAR_SIZE) / 2);
		characterPortal$textWidth = Math.max(0, characterPortal$starX - STAR_MARGIN - (left + 35));
		characterPortal$rendered = true;
	}

	@ModifyArg(
			method = "render",
			at =
					@At(
							value = "INVOKE",
							target =
									"Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"),
			index = 1)
	private String fitText(String text) {
		return minecraft.font.plainSubstrByWidth(text, characterPortal$textWidth);
	}

	@WrapOperation(
			method = "render",
			at =
					@At(
							value = "INVOKE",
							target =
									"Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"))
	private int fitInfo(
			GuiGraphics g,
			Font font,
			Component text,
			int x,
			int y,
			int color,
			boolean shadow,
			Operation<Integer> original) {
		// Keep the styled version/difficulty information intact while reserving the button gutter.
		g.enableScissor(x, y, x + characterPortal$textWidth, y + font.lineHeight);
		try {
			return original.call(g, font, text, x, y, color, shadow);
		} finally {
			g.disableScissor();
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void star(
			GuiGraphics g,
			int index,
			int top,
			int left,
			int width,
			int height,
			int mouseX,
			int mouseY,
			boolean hovered,
			float delta,
			CallbackInfo ci) {
		try {
			boolean starred = ClientStars.worlds().contains(summary.getLevelId());
			boolean hit = characterPortal$inside(mouseX, mouseY);
			StarButton.draw(g, characterPortal$starX, characterPortal$starY, starred, hit);
			if (hit)
				characterPortal$list
						.getScreen()
						.setTooltipForNextRenderPass(
								ClientProfiles.text(starred ? "star.remove" : "star.add"));
		} catch (IOException ex) {
			// A corrupt preference file must not prevent joining/editing the world itself.
			StarButton.draw(g, characterPortal$starX, characterPortal$starY, false, false);
		}
	}

	@Unique
	private boolean characterPortal$inside(double x, double y) {
		return characterPortal$rendered
				&& characterPortal$list.isMouseOver(x, y)
				&& x >= characterPortal$starX
				&& x < characterPortal$starX + STAR_SIZE
				&& y >= characterPortal$starY
				&& y < characterPortal$starY + STAR_SIZE;
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void toggleStar(double x, double y, int button, CallbackInfoReturnable<Boolean> ci) {
		if (button != 0 || !characterPortal$inside(x, y)) return;
		ci.setReturnValue(true);
		lastClickTime = 0;
		try {
			ClientStars.worlds().toggle(summary.getLevelId());
			((WorldStarsView) characterPortal$list).characterPortal$refreshStars();
		} catch (IOException ex) {
			ClientProfiles.error(characterPortal$list.getScreen(), ex);
		}
	}
}
