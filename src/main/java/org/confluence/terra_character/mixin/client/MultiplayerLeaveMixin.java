package org.confluence.terra_character.mixin.client;

import net.minecraft.client.gui.screens.PauseScreen;

import org.confluence.terra_character.client.MultiplayerClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class MultiplayerLeaveMixin {
	@Shadow
	private void onDisconnect() {
		throw new AssertionError();
	}

	@Inject(method = "onDisconnect", at = @At("HEAD"), cancellable = true)
	private void saveCharacterBeforeLeaving(CallbackInfo ci) {
		if (MultiplayerClient.leave(this::onDisconnect)) ci.cancel();
	}
}
