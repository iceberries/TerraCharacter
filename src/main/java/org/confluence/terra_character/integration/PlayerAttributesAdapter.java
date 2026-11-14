package org.confluence.terra_character.integration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import org.confluence.terra_character.api.integration.CharacterDataAdapter;
import java.util.Optional;

/** The vanilla attribute serializer includes base values and permanent modifiers only. */
public final class PlayerAttributesAdapter implements CharacterDataAdapter {
	@Override
	public ResourceLocation id() {
		return ResourceLocation.parse("terra_character:attributes");
	}

	@Override
	public int schemaVersion() {
		return 1;
	}

	@Override
	public Scope scope() {
		return Scope.CHARACTER;
	}

	@Override
	public CompoundTag capture(ServerPlayer player) {
		var result = new CompoundTag();
		result.put("Attributes", player.getAttributes().save());
		return result;
	}

	@Override
	public void initialize(ServerPlayer player) {
		var defaults = new AttributeMap(DefaultAttributes.getSupplier(EntityType.PLAYER));
		for (var attribute : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
			if (!player.getAttributes().hasAttribute(attribute) || !defaults.hasAttribute(attribute)) continue;
			player.getAttribute(attribute).replaceFrom(defaults.getInstance(attribute));
		}
	}

	@Override
	public void restore(ServerPlayer player, CompoundTag payload, int version) {
		var values = payload.getList("Attributes", Tag.TAG_COMPOUND);
		validate(values);
		for (Tag value : values) {
			var id = ResourceLocation.parse(((CompoundTag) value).getString("id"));
			if (!player.getAttributes().hasAttribute(BuiltInRegistries.ATTRIBUTE.getHolder(id).orElseThrow()))
				throw new IllegalArgumentException("Attribute no longer belongs to players: " + id);
		}
		initialize(player);
		player.getAttributes().load(values.copy());
	}

	public ListTag deferUnavailable(ServerPlayer player, CompoundTag payload) {
		var usable = new ListTag();
		var deferred = new ListTag();
		for (Tag value : payload.getList("Attributes", Tag.TAG_COMPOUND)) {
			var data = (CompoundTag) value;
			var id = ResourceLocation.parse(data.getString("id"));
			var holder = BuiltInRegistries.ATTRIBUTE.getHolder(id);
			(holder.isPresent() && player.getAttributes().hasAttribute(holder.get()) ? usable : deferred)
					.add(data);
		}
		payload.put("Attributes", usable);
		return deferred;
	}
	public static void validate(ListTag values) {
		for (Tag value : values) {
			var data = (CompoundTag) value;
			var id = ResourceLocation.tryParse(data.getString("id"));
			if (id == null || !BuiltInRegistries.ATTRIBUTE.containsKey(id))
				throw new IllegalArgumentException("Missing player attribute: " + data.getString("id"));
			if (!data.contains("base", Tag.TAG_ANY_NUMERIC) || !Double.isFinite(data.getDouble("base")))
				throw new IllegalArgumentException("Non-finite player attribute: " + id);
			for (Tag modifier : data.getList("modifiers", Tag.TAG_COMPOUND)) {
				var tag = (CompoundTag) modifier;
				if (!Double.isFinite(tag.getDouble("amount"))
						|| net.minecraft.world.entity.ai.attributes.AttributeModifier.load(tag) == null)
					throw new IllegalArgumentException("Invalid player attribute modifier: " + id);
			}
		}
	}

	@Override
	public Optional<CompoundTag> importLegacy(CompoundTag player) {
		if (!player.contains("attributes")) return Optional.empty();
		var result = new CompoundTag();
		result.put("Attributes", player.getList("attributes", Tag.TAG_COMPOUND).copy());
		return Optional.of(result);
	}
}
