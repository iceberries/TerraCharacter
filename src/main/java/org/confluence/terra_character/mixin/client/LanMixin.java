package org.confluence.terra_character.mixin.client;

import net.minecraft.client.server.IntegratedServer;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.runtime.CharacterSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.UncheckedIOException;

@Mixin(IntegratedServer.class)
public abstract class LanMixin {
	@Inject(method = "publishServer", at = @At("HEAD"), cancellable = true)
	private void publishCharacters(CallbackInfoReturnable<Boolean> cir) {
		var session = CharacterSession.current();
		if (session == null) return;
		var server = (IntegratedServer) (Object) this;
		try {
			server.submit(
							() -> {
								try {
									session.prepareShare();
								} catch (IOException ex) {
									throw new UncheckedIOException(ex);
								}
							})
					.join();
		} catch (Exception ex) {
			TerraCharacter.LOGGER.error("Cannot commit world before sharing", ex);
			cir.setReturnValue(false);
		}
	}
}
