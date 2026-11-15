package org.confluence.character_portal.compat.confluence.mixin;

import org.confluence.mod.common.attachment.ManaStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ManaStorage.class)
public interface ManaMixin {
	@Accessor("currentMana")
	void characterPortal$setCurrentMana(float value);
}
