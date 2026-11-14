package org.confluence.terra_character.mixin;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AttachmentType.class, remap = false)
public interface AttachmentTypeAccessor {
	@Accessor("serializer")
	IAttachmentSerializer<?, ?> terraCharacter$serializer();

	@Accessor("copyOnDeath")
	boolean terraCharacter$copyOnDeath();
}
