package org.confluence.terra_character.integration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.confluence.terra_character.api.integration.CharacterDataAdapter;
import org.confluence.terra_character.mixin.AttachmentStateAccessor;
import org.confluence.terra_character.mixin.AttachmentTypeAccessor;
import java.util.Optional;
import java.util.Set;

/** Uses the mod's own serializer, including Codec and non-compound attachment payloads. */
public final class AutomaticAttachmentAdapter implements CharacterDataAdapter {
	private final ResourceLocation attachment;
	private final AttachmentType<?> type;
	private final Scope scope;

	public AutomaticAttachmentAdapter(ResourceLocation attachment, AttachmentType<?> type, Scope scope) {
		this.attachment = attachment;
		this.type = type;
		this.scope = scope;
	}

	@Override
	public ResourceLocation id() {
		return ResourceLocation.fromNamespaceAndPath("terra_character_auto",
				scope.name().toLowerCase(java.util.Locale.ROOT) + "/" + attachment.getNamespace() + "/" + attachment.getPath());
	}

	@Override
	public int schemaVersion() {
		return 1;
	}

	@Override
	public Scope scope() {
		return scope;
	}

	@Override
	public Set<String> legacyAttachmentIds() {
		return Set.of(attachment.toString());
	}

	@SuppressWarnings("unchecked")
	private IAttachmentSerializer<Tag, Object> serializer() {
		return (IAttachmentSerializer<Tag, Object>) ((AttachmentTypeAccessor) (Object) type).terraCharacter$serializer();
	}

	@Override
	public CompoundTag capture(ServerPlayer player) {
		var result = new CompoundTag();
		// Never construct attachments belonging only to chunks, levels or other entity types.
		Object value = player.getExistingDataOrNull(type);
		if (value != null) {
			Tag data = serializer().write(value, player.registryAccess());
			if (data != null) result.put("Value", data.copy());
		}
		return result;
	}

	@Override
	public void initialize(ServerPlayer player) {
		((AttachmentStateAccessor) player).terraCharacter$attachments().remove(type);
	}

	@Override
	public void restore(ServerPlayer player, CompoundTag payload, int version) {
		initialize(player);
		if (payload.contains("Value")) {
			Object value = serializer().read(player, payload.get("Value").copy(), player.registryAccess());
			if (value == null) throw new IllegalStateException("Attachment decoder returned null: " + attachment);
			((AttachmentStateAccessor) player).terraCharacter$attachments().put(type, value);
		}
	}

	@Override
	public void afterRestore(ServerPlayer player) {
		if (player.connection != null) player.syncData(type);
	}

	@Override
	public Optional<CompoundTag> importLegacy(CompoundTag player) {
		var attachments = player.getCompound("neoforge:attachments");
		if (!attachments.contains(attachment.toString())) return Optional.empty();
		var data = new CompoundTag();
		data.put("Value", attachments.get(attachment.toString()).copy());
		return Optional.of(data);
	}
}
