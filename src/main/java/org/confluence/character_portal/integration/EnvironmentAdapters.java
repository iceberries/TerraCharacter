package org.confluence.character_portal.integration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.level.GameRules;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.confluence.character_portal.CharacterPortal;
import org.confluence.character_portal.api.integration.CharacterDataAdapter;
import org.confluence.character_portal.api.integration.DiscoverStateAdaptersEvent;
import org.confluence.character_portal.compat.pehkui.PehkuiStateAdapter;
import org.confluence.character_portal.mixin.AttachmentTypeAccessor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/** Discovery runs after all mods have registered their specialized ownership adapters. */
public final class EnvironmentAdapters {
	private static final Object DISCOVERY_LOCK = new Object();
	private static volatile CompoundTag catalog;
	private static boolean discovering;

	private EnvironmentAdapters() {}

	/** Called from game lifecycle events after NeoForge has started the game event bus. */
	public static void ensureDiscovered() {
		synchronized (DISCOVERY_LOCK) {
			if (catalog != null) {
				return;
			}
			if (discovering) {
				throw new IllegalStateException("State adapter discovery is already in progress");
			}
			discovering = true;
		}
		try {
			discover();
		} finally {
			synchronized (DISCOVERY_LOCK) {
				discovering = false;
			}
		}
	}

	private static void discover() {
		var policy = new DiscoverStateAdaptersEvent();
		NeoForge.EVENT_BUS.post(policy);
		var additions = new ArrayList<CharacterDataAdapter>();
		if (ModList.get().isLoaded("pehkui")) {
			try {
				additions.add(new PehkuiStateAdapter());
			} catch (ReflectiveOperationException | LinkageError ex) {
				CharacterPortal.LOGGER.warn("Cannot register the Pehkui scale adapter", ex);
			}
		}
		var registered = new ArrayList<>(AdapterRegistry.all());
		registered.addAll(additions);
		var owned = new HashSet<String>();
		registered.forEach(adapter -> owned.addAll(adapter.legacyAttachmentIds()));
		int automatic = 0;
		for (var entry : NeoForgeRegistries.ATTACHMENT_TYPES.entrySet()) {
			var id = entry.getKey().location();
			if (owned.contains(id.toString()) || policy.excluded(id)) continue;
			if (((AttachmentTypeAccessor) (Object) entry.getValue()).characterPortal$serializer() == null) continue;
			additions.add(new AutomaticAttachmentAdapter(id, entry.getValue(), policy.attachmentScope(id)));
			automatic++;
		}
		var result = new CompoundTag();
		result.putInt("Version", 1);
		var mods = new CompoundTag();
		ModList.get().getMods().forEach(mod -> mods.putString(mod.getModId(), mod.getVersion().toString()));
		result.put("Mods", mods);
		result.put("Attributes", strings(BuiltInRegistries.ATTRIBUTE.keySet().stream().map(Object::toString).toList()));
		var defaults = new CompoundTag();
		var playerDefaults = DefaultAttributes.getSupplier(EntityType.PLAYER);
		BuiltInRegistries.ATTRIBUTE.holders().forEach(holder -> {
			var value = new CompoundTag();
			value.putDouble("Default", holder.value().getDefaultValue());
			value.putBoolean("ClientSyncable", holder.value().isClientSyncable());
			if (playerDefaults.hasAttribute(holder)) value.putDouble("PlayerDefault", playerDefaults.getBaseValue(holder));
			defaults.put(holder.key().location().toString(), value);
		});
		result.put("AttributeDefaults", defaults);
		result.put("GameRuleDefaults", new GameRules().createTag());
		result.put("Attachments", strings(NeoForgeRegistries.ATTACHMENT_TYPES.keySet().stream().map(Object::toString).toList()));
		var attachmentPolicies = new CompoundTag();
		NeoForgeRegistries.ATTACHMENT_TYPES.entrySet().forEach(entry -> {
			var access = (AttachmentTypeAccessor) (Object) entry.getValue();
			var value = new CompoundTag();
			value.putBoolean("Serializable", access.characterPortal$serializer() != null);
			value.putBoolean("CopyOnDeath", access.characterPortal$copyOnDeath());
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
		registered.forEach(adapter -> adapters.putString(adapter.id().toString(), adapter.scope().name()));
		additions.forEach(adapter -> adapters.putString(adapter.id().toString(), adapter.scope().name()));
		result.put("Adapters", adapters);
		AdapterRegistry.registerAll(additions);
		catalog = result;
		CharacterPortal.LOGGER.info("State environment loaded: {} attributes, {} attachment types, {} automatic adapters",
				BuiltInRegistries.ATTRIBUTE.size(), NeoForgeRegistries.ATTACHMENT_TYPES.size(), automatic);
	}

	/** An owned snapshot; registry access never runs on the asynchronous profile writer. */
	public static CompoundTag snapshot() {
		var current = catalog;
		return current == null ? new CompoundTag() : current.copy();
	}

	public static ListTag strings(Iterable<String> values) {
		Set<String> ordered = new TreeSet<>();
		values.forEach(ordered::add);
		var result = new ListTag();
		ordered.forEach(value -> result.add(StringTag.valueOf(value)));
		return result;
	}
}
