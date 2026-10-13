package org.confluence.terra_character.mixin.client;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

import org.confluence.terra_character.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldMixin {
	@Inject(method = "onCreate", at = @At("HEAD"), cancellable = true)
	private void configureCharacterWorld(CallbackInfo ci) {
		try {
			WorldCreationCoordinator.configure((CreateWorldScreen) (Object) this);
		} catch (Exception ex) {
			ci.cancel();
			ClientProfiles.error((CreateWorldScreen) (Object) this, ex);
		}
	}

	@Inject(method = "createNewWorld", at = @At("HEAD"), cancellable = true)
	private void launchCharacterWorld(CallbackInfo ci) {
		try {
			WorldCreationCoordinator.arm((CreateWorldScreen) (Object) this);
		} catch (Exception ex) {
			ci.cancel();
			ClientProfiles.error((CreateWorldScreen) (Object) this, ex);
		}
	}
}
