package org.confluence.terra_character.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;

import org.confluence.terra_character.client.*;
import org.confluence.terra_character.runtime.CharacterSession;
import org.confluence.terra_character.storage.WorldStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;

@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenMixin {
	@Inject(method = "openWorld", at = @At("HEAD"), cancellable = true)
	private void managedWorld(String folder, Runnable failure, CallbackInfo ci) {
		if (CharacterSession.hasPending()) return;
		if (ClientProfiles.getSelected() != null) {
			ci.cancel();
			WorldLaunchCoordinator.open(folder, failure);
			return;
		}
		try {
			var directory = Minecraft.getInstance().getLevelSource().getLevelPath(folder);
			if (WorldStore.read(directory).isPresent()
					|| Files.exists(WorldStore.directory(directory).resolve("session.nbt"))) {
				ci.cancel();
				Minecraft.getInstance()
						.setScreen(
								new NoticeScreen(
										new CharacterSelectionScreen(new TitleScreen()),
										ClientProfiles.text("characters"),
										ClientProfiles.text("world.managed")));
			}
		} catch (Exception ex) {
			ci.cancel();
			ClientProfiles.error(new TitleScreen(), ex);
		}
	}
}
