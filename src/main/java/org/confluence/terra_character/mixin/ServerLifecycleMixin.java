package org.confluence.terra_character.mixin;

import net.minecraft.server.MinecraftServer;

import org.confluence.terra_character.runtime.CharacterSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(MinecraftServer.class)
public abstract class ServerLifecycleMixin {
	@Inject(method = "stopServer", at = @At("RETURN"))
	private void cleanSession(CallbackInfo ci) {
		var session = CharacterSession.current();
		if (session != null) session.finish();
	}
}
