package org.confluence.terra_character.mixin.compat;

import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.confluence.terra_character.runtime.CharacterSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Pseudo
@Mixin(targets = "lain.mods.cos.impl.InventoryManager", remap = false)
abstract class CosmeticArmorInventoryMixin {
	@Inject(method = "getDataFile", at = @At("HEAD"), cancellable = true, require = 0)
	private void useCharacterFile(UUID account, CallbackInfoReturnable<File> cir) throws IOException {
		var server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) return;
		var file = CharacterSession.cosmeticArmorFile(server, account);
		if (file != null) cir.setReturnValue(file);
	}
}
