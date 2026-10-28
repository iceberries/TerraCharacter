package org.confluence.terra_character.compat.confluence.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementProgressEvent.ProgressType;

import org.confluence.mod.common.attachment.PlayerSpecialData;
import org.confluence.terra_character.compat.confluence.integration.InitialItemsState;
import org.confluence.terra_character.runtime.CharacterSession;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/** The world may earn the welcome advancement again, but its reward belongs to the character. */
@Mixin(PlayerAdvancements.class)
public abstract class InitialRewardsMixin {
	@Unique
	private static final ResourceLocation terraCharacter$welcome =
			ResourceLocation.parse("confluence:achievements/new_world");

	@Shadow private ServerPlayer player;
	@Shadow @Final private Set<AdvancementHolder> progressChanged;

	@Shadow
	public abstract AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

	@Shadow
	protected abstract void unregisterListeners(AdvancementHolder advancement);

	@Shadow
	protected abstract void markForVisibilityUpdate(AdvancementHolder advancement);

	@Inject(method = "award", at = @At("HEAD"), cancellable = true)
	private void characterAlreadyReceived(
			AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
		if (!advancement.id().equals(terraCharacter$welcome) || CharacterSession.of(player) == null)
			return;
		var state = (InitialItemsState) PlayerSpecialData.of(player);
		if (!state.terraCharacter$hasInitialItems()) return;
		var progress = getOrStartProgress(advancement);
		boolean wasDone = progress.isDone();
		boolean changed = progress.grantProgress(criterion);
		if (changed) {
			unregisterListeners(advancement);
			progressChanged.add(advancement);
			EventHooks.onAdvancementProgressedEvent(
					player, advancement, progress, criterion, ProgressType.GRANT);
		}
		if (!wasDone && progress.isDone()) markForVisibilityUpdate(advancement);
		// Complete the world's UI entry without replaying rewards or the initial-item event.
		cir.setReturnValue(changed);
	}

	@WrapOperation(
			method = "award",
			at =
					@At(
							value = "INVOKE",
							target =
									"Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V"))
	private void recordInitialReward(
			AdvancementRewards rewards,
			ServerPlayer owner,
			Operation<Void> original,
			@Local(argsOnly = true) AdvancementHolder advancement) {
		if (advancement.id().equals(terraCharacter$welcome) && CharacterSession.of(owner) != null)
			((InitialItemsState) PlayerSpecialData.of(owner)).terraCharacter$setInitialItems(true);
		// Mark before callbacks: re-entrant grants must see the same character receipt.
		original.call(rewards, owner);
	}
}
