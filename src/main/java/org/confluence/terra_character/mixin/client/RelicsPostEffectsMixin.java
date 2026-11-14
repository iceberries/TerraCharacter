package org.confluence.terra_character.mixin.client;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Pseudo
@Mixin(targets = "it.hurts.sskirillss.relics.init.RelicsPostEffects", remap = false)
abstract class RelicsPostEffectsMixin {
	@Shadow @Final @Mutable
	private static Map<?, ?> POST_EFFECTS;

	@Redirect(method = "<clinit>", at = @At(value = "FIELD", target =
			"Lit/hurts/sskirillss/relics/init/RelicsPostEffects;POST_EFFECTS:Ljava/util/Map;",
			opcode = Opcodes.PUTSTATIC), require = 0)
	private static void useConcurrentEffects(Map<?, ?> effects) {
		POST_EFFECTS = new ConcurrentHashMap<>(effects);
	}
}
