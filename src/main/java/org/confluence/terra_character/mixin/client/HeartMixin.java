package org.confluence.terra_character.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.*;

import net.minecraft.client.gui.Gui;
import net.minecraft.world.level.storage.LevelData;

import org.confluence.terra_character.client.ClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class HeartMixin {
	@WrapOperation(
			method = "renderHearts",
			at =
					@At(
							value = "INVOKE",
							target = "Lnet/minecraft/world/level/storage/LevelData;isHardcore()Z"))
	private boolean characterHeart(LevelData data, Operation<Boolean> original) {
		return ClientState.profile() == null ? original.call(data) : ClientState.hardcore();
	}
}
