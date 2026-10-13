package org.confluence.terra_character.mixin.client;

import net.minecraft.client.server.IntegratedPlayerList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import org.confluence.terra_character.runtime.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(IntegratedPlayerList.class)
public abstract class IntegratedPlayerListMixin {
	@Inject(method = "save", at = @At("HEAD"), cancellable = true)
	private void preserveAccountSave(ServerPlayer player, CallbackInfo ci) {
		if (CharacterSession.of(player) == null) return;
		SessionEvents.save(player);
		((PlayerList) (Object) this).getPlayerStats(player).save();
		((PlayerList) (Object) this).getPlayerAdvancements(player).save();
		ci.cancel();
	}

	@Inject(method = "getSingleplayerData", at = @At("HEAD"), cancellable = true)
	private void preserveAccountTag(CallbackInfoReturnable<CompoundTag> cir) {
		if (CharacterSession.current() != null)
			cir.setReturnValue(CharacterSession.current().originalPlayer());
	}
}
