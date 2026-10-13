package org.confluence.terra_character.api.integration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.Set;

/**
 * SPI implemented by core vanilla adapters and separately installed compatibility modules. The core
 * does not reference compatibility implementations or the mods whose data they adapt. Capture must
 * return an owned copy and restore must replace data rather than merge with another character. The
 * session coordinator calls these on the server thread after registry availability and before play.
 * Implementations must whitelist fields and must not transfer account UUID, position or world
 * progression. Register through AdapterRegistry during mod construction; CharacterSession
 * coordinates transfers.
 */
public interface CharacterDataAdapter {
	ResourceLocation id();

	int schemaVersion();

	Scope scope();

	CompoundTag capture(ServerPlayer player);

	void restore(ServerPlayer player, CompoundTag payload, int storedVersion);

	/**
	 * Explicit initialization for a new character or first visit; never inherit previous live data.
	 */
	void initialize(ServerPlayer player);

	default void afterRestore(ServerPlayer player) {}

	/** Repair or replace mod state after NeoForge has copied attachments to a respawned entity. */
	default void afterClone(ServerPlayer original, ServerPlayer replacement) {}

	default void onDeath(ServerPlayer player, boolean keepInventory) {}

	/** Extract only this adapter's portable fields from a backed-up legacy player tag. */
	default Optional<CompoundTag> importLegacy(CompoundTag playerTag) {
		return Optional.empty();
	}

	default Set<String> legacyAttachmentIds() {
		return Set.of();
	}

	enum Scope {
		CHARACTER,
		CHARACTER_WORLD
	}
}
