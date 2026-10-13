package org.confluence.terra_character.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.storage.ProfileCodec;

import java.io.IOException;

public final class ClientState {
	private ClientState() {}

	private static CompoundTag cachedProfileTag;
	private static CharacterProfile cachedProfile;

	public static CompoundTag state() {
		var player = Minecraft.getInstance().player;
		return player == null
				? new CompoundTag()
				: player.getPersistentData().getCompound("terra_character:profile_state");
	}

	// Each received payload replaces the compound. Avoid decoding a 64x64 skin on every render/HUD
	// query.
	public static CharacterProfile profile() {
		var tag = state().getCompound("Profile");
		if (tag != cachedProfileTag) {
			cachedProfileTag = tag;
			try {
				cachedProfile = ProfileCodec.character(tag);
			} catch (IOException ex) {
				cachedProfile = null;
			}
		}
		return cachedProfile;
	}

	public static boolean hardcore() {
		var p = profile();
		return p != null && p.difficulty() == CharacterDifficulty.HARDCORE;
	}
}
