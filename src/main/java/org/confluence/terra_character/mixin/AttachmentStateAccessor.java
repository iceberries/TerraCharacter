package org.confluence.terra_character.mixin;

import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.Map;

@Mixin(value = AttachmentHolder.class, remap = false)
public interface AttachmentStateAccessor {
	/** Non-syncing installation is required before a player's network connection exists. */
	@Invoker("getAttachmentMap")
	Map<AttachmentType<?>, Object> terraCharacter$attachments();
}
