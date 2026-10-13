package org.confluence.terra_character.storage;

import net.minecraft.nbt.CompoundTag;

import org.confluence.terra_character.api.model.WorldProfile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;

public final class WorldStore {
	private WorldStore() {}

	public static Path directory(Path world) {
		return world.resolve("data/terra_character");
	}

	public static boolean shared(Path world) throws IOException {
		Path marker = directory(world).resolve("multiplayer.nbt");
		if (!Files.exists(marker)) return false;
		var tag = AtomicNbt.read(marker);
		ProfileCodec.version(tag);
		if (!tag.getBoolean("Shared")) throw new IOException("Invalid multiplayer world marker");
		return true;
	}

	public static void markShared(Path world) throws IOException {
		var tag = new CompoundTag();
		tag.putInt("FormatVersion", ProfileCodec.VERSION);
		tag.putBoolean("Shared", true);
		AtomicNbt.write(directory(world).resolve("multiplayer.nbt"), tag, false);
	}

	public static Optional<WorldProfile> read(Path world) throws IOException {
		Path path = directory(world).resolve("world.nbt");
		if (!Files.exists(path)) return Optional.empty();
		var tag = AtomicNbt.read(path);
		ProfileCodec.version(tag);
		return Optional.of(ProfileCodec.world(tag.getCompound("Profile")));
	}

	public static void write(Path world, WorldProfile profile) throws IOException {
		var tag = new CompoundTag();
		tag.putInt("FormatVersion", ProfileCodec.VERSION);
		tag.put("Profile", ProfileCodec.world(profile));
		AtomicNbt.write(directory(world).resolve("world.nbt"), tag, true);
	}
}
