package org.confluence.terra_character.mixin.client;

import net.minecraft.client.Minecraft;

import org.confluence.terra_character.client.MultiplayerClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientStopMixin {
	@Inject(method = "stop", at = @At("HEAD"), cancellable = true)
	private void saveCharacterBeforeClosing(CallbackInfo ci) {
		if (MultiplayerClient.leave(() -> Minecraft.getInstance().stop())) ci.cancel();
	}
}
