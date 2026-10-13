package org.confluence.terra_character.mixin;

import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

import org.confluence.terra_character.network.CharacterHandshake;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ConfigurationDisconnectMixin {
	@Inject(method = "onDisconnect", at = @At("HEAD"))
	private void releaseSelection(CallbackInfo ci) {
		CharacterHandshake.disconnected(
				((ServerConfigurationPacketListenerImpl) (Object) this).getConnection());
	}
}
