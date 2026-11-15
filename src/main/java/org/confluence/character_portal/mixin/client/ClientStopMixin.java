package org.confluence.character_portal.mixin.client;

import net.minecraft.client.Minecraft;

import org.confluence.character_portal.client.MultiplayerClient;
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
