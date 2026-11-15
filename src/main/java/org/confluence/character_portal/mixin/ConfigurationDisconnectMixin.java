package org.confluence.character_portal.mixin;

import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

import org.confluence.character_portal.network.CharacterHandshake;
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
