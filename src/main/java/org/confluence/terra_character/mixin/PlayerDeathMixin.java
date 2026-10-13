package org.confluence.terra_character.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;

import org.confluence.terra_character.runtime.CharacterSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class PlayerDeathMixin {
	@WrapOperation(
			method = {"dropEquipment", "getBaseExperienceReward"},
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
				? CharacterSession.keepInventory((Player) (Object) this, original.call(rules, key))
				: original.call(rules, key);
	}
}
