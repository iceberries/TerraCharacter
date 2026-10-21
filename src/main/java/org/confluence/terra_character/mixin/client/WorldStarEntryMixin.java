package org.confluence.terra_character.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;

import org.confluence.terra_character.client.*;
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
	@Unique private WorldSelectionList terraCharacter$list;
	@Unique private int terraCharacter$starX, terraCharacter$starY, terraCharacter$textWidth;
	@Unique private boolean terraCharacter$rendered;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void owner(
			CallbackInfo ci, @Local(argsOnly = true, ordinal = 0) WorldSelectionList list) {
		terraCharacter$list = list;
	}

	@Override
	public String terraCharacter$worldId() {
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
		terraCharacter$starX = left + width - 20;
		terraCharacter$starY = top + 1;
		terraCharacter$textWidth = Math.max(0, width - 60);
		terraCharacter$rendered = true;
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
		return minecraft.font.plainSubstrByWidth(text, terraCharacter$textWidth);
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
			boolean hit = terraCharacter$inside(mouseX, mouseY);
			StarButton.draw(g, terraCharacter$starX, terraCharacter$starY, starred, hit);
			if (hit)
				terraCharacter$list
						.getScreen()
						.setTooltipForNextRenderPass(
								ClientProfiles.text(starred ? "star.remove" : "star.add"));
		} catch (IOException ex) {
			// A corrupt preference file must not prevent joining/editing the world itself.
			StarButton.draw(g, terraCharacter$starX, terraCharacter$starY, false, false);
		}
	}

	@Unique
	private boolean terraCharacter$inside(double x, double y) {
		return terraCharacter$rendered
				&& x >= terraCharacter$starX
				&& x < terraCharacter$starX + 18
				&& y >= terraCharacter$starY
				&& y < terraCharacter$starY + 18;
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void toggleStar(double x, double y, int button, CallbackInfoReturnable<Boolean> ci) {
		if (button != 0 || !terraCharacter$inside(x, y)) return;
		ci.setReturnValue(true);
		lastClickTime = 0;
		try {
			ClientStars.worlds().toggle(summary.getLevelId());
			((WorldStarsView) terraCharacter$list).terraCharacter$refreshStars();
		} catch (IOException ex) {
			ClientProfiles.error(terraCharacter$list.getScreen(), ex);
		}
	}
}
