package org.confluence.terra_character.mixin.client;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;

import org.confluence.terra_character.client.CharacterSelectionScreen;
import org.confluence.terra_character.client.ClientProfiles;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A local character session must not fall back to vanilla's multiplayer server list. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class LocalDisconnectMixin {
	@Shadow @Final private Connection connection;

	@Inject(method = "createDisconnectScreen", at = @At("HEAD"), cancellable = true)
	private void localReturn(DisconnectionDetails details, CallbackInfoReturnable<Screen> cir) {
		if (!connection.isMemoryConnection() || ClientProfiles.getSelected() == null) return;
		cir.setReturnValue(
				new DisconnectedScreen(
						new CharacterSelectionScreen(new TitleScreen()),
						Component.translatable("disconnect.lost"),
						details,
						ClientProfiles.text("characters")));
	}
}
