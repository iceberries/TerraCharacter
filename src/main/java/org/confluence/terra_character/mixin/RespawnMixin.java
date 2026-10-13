package org.confluence.terra_character.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

import org.confluence.terra_character.runtime.CharacterSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public abstract class RespawnMixin {
	@WrapOperation(
			method = "restoreFrom",
			at =
					@At(
							value = "INVOKE",
							target =
									"Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
	private boolean characterKeep(
			GameRules rules,
			GameRules.Key<GameRules.BooleanValue> key,
			Operation<Boolean> original) {
		return key == GameRules.RULE_KEEPINVENTORY
				? CharacterSession.keepInventory(
						(ServerPlayer) (Object) this, original.call(rules, key))
				: original.call(rules, key);
	}
}
