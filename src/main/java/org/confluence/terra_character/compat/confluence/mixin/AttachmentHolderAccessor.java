package org.confluence.terra_character.compat.confluence.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentHolder;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** NeoForge's load path installs attachments without sending packets before player login. */
@Mixin(AttachmentHolder.class)
public interface AttachmentHolderAccessor {
	@Invoker("deserializeAttachments")
	void terraCharacter$loadAttachments(HolderLookup.Provider registries, CompoundTag attachments);
}
