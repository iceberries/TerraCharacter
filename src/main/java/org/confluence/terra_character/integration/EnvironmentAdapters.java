package org.confluence.terra_character.integration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.integration.DiscoverStateAdaptersEvent;
import org.confluence.terra_character.compat.pehkui.PehkuiStateAdapter;
import org.confluence.terra_character.mixin.AttachmentTypeAccessor;
import java.util.Set;
import java.util.TreeSet;

/** Discovery runs after all mods have registered their specialized ownership adapters. */
public final class EnvironmentAdapters {
	private static volatile CompoundTag catalog = new CompoundTag();
	private static boolean discovered;
	private EnvironmentAdapters() {}

	public static synchronized void discover() {
		if (discovered) return;
		if (ModList.get().isLoaded("pehkui")) {
			try {
				AdapterRegistry.register(new PehkuiStateAdapter());
			} catch (ReflectiveOperationException | LinkageError ex) {
				TerraCharacter.LOGGER.warn("Cannot register the Pehkui scale adapter", ex);
			}
		}
		var policy = new DiscoverStateAdaptersEvent();
		NeoForge.EVENT_BUS.post(policy);
		var owned = new java.util.HashSet<String>();
		AdapterRegistry.all().forEach(adapter -> owned.addAll(adapter.legacyAttachmentIds()));
		int automatic = 0;
		for (var entry : NeoForgeRegistries.ATTACHMENT_TYPES.entrySet()) {
			var id = entry.getKey().location();
			if (owned.contains(id.toString()) || policy.excluded(id)) continue;
			if (((AttachmentTypeAccessor) (Object) entry.getValue()).terraCharacter$serializer() == null) continue;
			AdapterRegistry.register(new AutomaticAttachmentAdapter(id, entry.getValue(), policy.attachmentScope(id)));
			automatic++;
		}
		var result = new CompoundTag();
		result.putInt("Version", 1);
		var mods = new CompoundTag();
		ModList.get().getMods().forEach(mod -> mods.putString(mod.getModId(), mod.getVersion().toString()));
		result.put("Mods", mods);
		result.put("Attributes", strings(BuiltInRegistries.ATTRIBUTE.keySet().stream().map(Object::toString).toList()));
		var defaults = new CompoundTag();
		var playerDefaults = net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(net.minecraft.world.entity.EntityType.PLAYER);
		BuiltInRegistries.ATTRIBUTE.holders().forEach(holder -> {
			var value = new CompoundTag();
			value.putDouble("Default", holder.value().getDefaultValue());
			value.putBoolean("ClientSyncable", holder.value().isClientSyncable());
			if (playerDefaults.hasAttribute(holder)) value.putDouble("PlayerDefault", playerDefaults.getBaseValue(holder));
			defaults.put(holder.key().location().toString(), value);
		});
		result.put("AttributeDefaults", defaults);
		result.put("GameRuleDefaults", new net.minecraft.world.level.GameRules().createTag());
		result.put("Attachments", strings(NeoForgeRegistries.ATTACHMENT_TYPES.keySet().stream().map(Object::toString).toList()));
		var attachmentPolicies = new CompoundTag();
		NeoForgeRegistries.ATTACHMENT_TYPES.entrySet().forEach(entry -> {
			var access = (AttachmentTypeAccessor) (Object) entry.getValue();
			var value = new CompoundTag();
			value.putBoolean("Serializable", access.terraCharacter$serializer() != null);
			value.putBoolean("CopyOnDeath", access.terraCharacter$copyOnDeath());
			attachmentPolicies.put(entry.getKey().location().toString(), value);
		});
		result.put("AttachmentPolicies", attachmentPolicies);
		var registries = new CompoundTag();
		var entries = new CompoundTag();
		BuiltInRegistries.REGISTRY.forEach(registry -> {
			String key = registry.key().location().toString();
			registries.putInt(key, registry.size());
			entries.put(key, strings(registry.keySet().stream().map(Object::toString).toList()));
		});
		result.put("RegistryCounts", registries);
		result.put("RegistryEntries", entries);
		var adapters = new CompoundTag();
		AdapterRegistry.all().forEach(adapter -> adapters.putString(adapter.id().toString(), adapter.scope().name()));
		result.put("Adapters", adapters);
		catalog = result;
		discovered = true;
		TerraCharacter.LOGGER.info("State environment loaded: {} attributes, {} attachment types, {} automatic adapters",
				BuiltInRegistries.ATTRIBUTE.size(), NeoForgeRegistries.ATTACHMENT_TYPES.size(), automatic);
	}

	/** An owned snapshot; registry access never runs on the asynchronous profile writer. */
	public static CompoundTag snapshot() {
		return catalog.copy();
	}

	public static ListTag strings(Iterable<String> values) {
		Set<String> ordered = new TreeSet<>();
		values.forEach(ordered::add);
		var result = new ListTag();
		ordered.forEach(value -> result.add(StringTag.valueOf(value)));
		return result;
	}
}
