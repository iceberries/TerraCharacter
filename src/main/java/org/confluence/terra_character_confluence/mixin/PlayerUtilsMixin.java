package org.confluence.terra_character_confluence.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;

import org.confluence.mod.util.PlayerUtils;
import org.confluence.terra_character.api.model.CharacterDifficulty;
import org.confluence.terra_character.runtime.CharacterSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerUtils.class)
public abstract class PlayerUtilsMixin {
	@Inject(method = "askForSoftcore", at = @At("HEAD"), cancellable = true)
	private static void noWorldPrompt(ServerPlayer player, CallbackInfo ci) {
		if (CharacterSession.of(player) != null) ci.cancel();
	}

	@Inject(method = "dropMoney", at = @At("HEAD"), cancellable = true)
	private static void journeyKeepsMoney(Player player, CallbackInfo ci) {
		var session = CharacterSession.of(player);
		if (session != null && session.profile().difficulty() == CharacterDifficulty.JOURNEY)
			ci.cancel();
	}

	@WrapOperation(
			method = "dropMoney",
			at =
					@At(
							value = "INVOKE",
							target =
									"Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
	private static boolean characterPenalty(
			GameRules rules,
			GameRules.Key<GameRules.BooleanValue> key,
			Operation<Boolean> original,
			@Local(argsOnly = true) Player player) {
		return CharacterSession.keepInventory(player, original.call(rules, key));
	}
}
