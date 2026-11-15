package org.confluence.character_portal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import org.confluence.character_portal.api.model.CharacterProfile;
import org.confluence.character_portal.storage.ProfileCodec;

import java.io.IOException;

public final class ClientState {
	private ClientState() {}

	private static CompoundTag cachedProfileTag;
	private static CharacterProfile cachedProfile;

	static void clear() {
		cachedProfileTag = null;
		cachedProfile = null;
	}

	public static CompoundTag state() {
		var player = Minecraft.getInstance().player;
		return player == null
				? new CompoundTag()
				: player.getPersistentData().getCompound("character_portal:profile_state");
	}

	// Each received payload replaces the compound. Avoid decoding a 64x64 skin on every render/HUD
	// query.
	public static CharacterProfile profile() {
		if (Minecraft.getInstance().player == null) {
			return null;
		}
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
		return p != null && p.difficulty().isHardcore();
	}
}
