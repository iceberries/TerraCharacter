package org.confluence.character_portal.mixin.client;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

import org.confluence.character_portal.client.ClientProfiles;
import org.confluence.character_portal.client.WorldCreationCoordinator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldMixin implements WorldCreationCoordinator.Access {
	@Unique
	private boolean characterPortal$worldOptionsBound;
	@Unique
	private WorldCreationCoordinator.Draft characterPortal$draft;

	@Override
	public boolean characterPortal$bindWorldOptions() {
		if (characterPortal$worldOptionsBound) {
			return false;
		}
		characterPortal$worldOptionsBound = true;
		return true;
	}

	@Inject(method = "onCreate", at = @At("HEAD"), cancellable = true)
	private void configureCharacterWorld(CallbackInfo ci) {
		try {
			characterPortal$draft =
					WorldCreationCoordinator.configure((CreateWorldScreen) (Object) this);
		} catch (Exception ex) {
			characterPortal$draft = null;
			ci.cancel();
			ClientProfiles.error((CreateWorldScreen) (Object) this, ex);
		}
	}

	@Inject(method = "createNewWorld", at = @At("HEAD"), cancellable = true)
	private void launchCharacterWorld(CallbackInfo ci) {
		try {
			WorldCreationCoordinator.arm((CreateWorldScreen) (Object) this, characterPortal$draft);
		} catch (Exception ex) {
			ci.cancel();
			ClientProfiles.error((CreateWorldScreen) (Object) this, ex);
		} finally {
			characterPortal$draft = null;
		}
	}
}
