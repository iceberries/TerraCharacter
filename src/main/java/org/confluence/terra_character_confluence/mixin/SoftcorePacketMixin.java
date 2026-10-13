package org.confluence.terra_character_confluence.mixin;

import net.minecraft.server.level.ServerPlayer;

import org.confluence.mod.network.AskForSoftcorePacket;
import org.confluence.terra_character.runtime.CharacterSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AskForSoftcorePacket.class)
public abstract class SoftcorePacketMixin {
	@Inject(method = "c2s", at = @At("HEAD"), cancellable = true)
	private void immutableDifficulty(ServerPlayer player, CallbackInfo ci) {
		if (CharacterSession.of(player) != null) ci.cancel();
	}
}
