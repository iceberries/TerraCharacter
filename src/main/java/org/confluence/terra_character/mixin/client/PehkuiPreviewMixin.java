package org.confluence.terra_character.mixin.client;

import net.minecraft.world.entity.Entity;
import org.confluence.terra_character.client.CharacterPortraits.PreviewPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "virtuoel.pehkui.util.ScaleUtils", remap = false)
abstract class PehkuiPreviewMixin {
	@Inject(method = "getTypedScale", at = @At("HEAD"), cancellable = true, require = 0)
	private static void menuScale(Entity entity, @Coerce Object type, float tick, CallbackInfoReturnable<Float> cir) {
		if (entity instanceof PreviewPlayer) cir.setReturnValue(1.0F);
	}

	@Inject(method = "getConfigurableTypedScale", at = @At("HEAD"), cancellable = true, require = 0)
	private static void menuScale(Entity entity, @Coerce Object type, Supplier<?> enabled, float tick,
			CallbackInfoReturnable<Float> cir) {
		if (entity instanceof PreviewPlayer) cir.setReturnValue(1.0F);
	}
}
