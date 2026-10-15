package org.confluence.terra_character.compat.confluence.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.*;

import net.minecraft.world.level.storage.LevelData;

import org.confluence.terra_character.client.ClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "org.confluence.mod.client.gui.hud.TerraStyleHealthHud$Health$2")
public abstract class HealthHudMixin {
	@WrapOperation(
			method = "render",
			at =
					@At(
							value = "INVOKE",
							target = "Lnet/minecraft/world/level/storage/LevelData;isHardcore()Z"))
	private boolean characterHeart(LevelData data, Operation<Boolean> original) {
		return ClientState.profile() == null ? original.call(data) : ClientState.hardcore();
	}
}
