package org.confluence.terra_character_confluence.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.confluence.mod.common.attachment.PlayerSpecialData;
import org.confluence.terra_character.runtime.CharacterSession;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Armor-derived cache resets must not reset the separately owned quest counters. */
@Mixin(PlayerSpecialData.class)
public abstract class QuestResetMixin {
	@Shadow private long completedAnglerQuestDay;
	@Shadow private int anglerQuestCount;
	@Shadow private ItemStack currentQuestedFish;

	@WrapOperation(
			method = "flushArmorSetBonus",
			at =
					@At(
							value = "INVOKE",
							target =
									"Lorg/confluence/mod/common/attachment/PlayerSpecialData;setToDefaultValue()V"))
	private void preserveQuests(
			PlayerSpecialData data,
			Operation<Void> original,
			@Local(argsOnly = true) Player player) {
		long day = completedAnglerQuestDay;
		int count = anglerQuestCount;
		ItemStack fish = currentQuestedFish;
		original.call(data);
		if (CharacterSession.of(player) != null) {
			completedAnglerQuestDay = day;
			anglerQuestCount = count;
			currentQuestedFish = fish;
		}
	}
}
