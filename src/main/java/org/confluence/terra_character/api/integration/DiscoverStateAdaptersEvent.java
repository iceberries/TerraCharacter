package org.confluence.terra_character.api.integration;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import org.confluence.terra_character.api.integration.CharacterDataAdapter.Scope;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fired on NeoForge.EVENT_BUS after registration, before any character creation screen. */
public final class DiscoverStateAdaptersEvent extends Event {
	private final Map<ResourceLocation, Scope> scopes = new HashMap<>();
	private final Set<ResourceLocation> ignored = new HashSet<>();

	public void attachmentScope(ResourceLocation id, Scope scope) {
		scopes.put(Objects.requireNonNull(id), Objects.requireNonNull(scope));
	}

	/** Excluded attachments remain subject to the session's unsupported-data check. */
	public void excludeAttachment(ResourceLocation id) {
		ignored.add(Objects.requireNonNull(id));
	}

	public Scope attachmentScope(ResourceLocation id) {
		return scopes.getOrDefault(id, Scope.CHARACTER_WORLD);
	}

	public boolean excluded(ResourceLocation id) {
		return ignored.contains(id);
	}
}
