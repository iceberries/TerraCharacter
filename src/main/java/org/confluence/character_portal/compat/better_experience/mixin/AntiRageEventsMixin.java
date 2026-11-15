package org.confluence.character_portal.compat.better_experience.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.confluence.character_portal.integration.BetterExperienceCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.github.edg_thexu.better_experience.module.antirage.AntiRageEvents", remap = false)
public abstract class AntiRageEventsMixin {
	@WrapOperation(
			method = {"onDeath", "onRespawn", "onPlayerTick", "onDamage", "onInvulnerabilityCheck"},
			at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/ModConfigSpec$BooleanValue;get()Ljava/lang/Object;"))
	private static Object characterMode(ModConfigSpec.BooleanValue config, Operation<Object> original,
			@Local ServerPlayer player) {
		return BetterExperienceCompatibility.enabled(player, (Boolean) original.call(config));
	}
}
