package org.confluence.terra_character_confluence.mixin;

import org.confluence.mod.common.attachment.ManaStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ManaStorage.class)
public interface ManaMixin {
	@Accessor("currentMana")
	void terraCharacter$setCurrentMana(float value);
}
