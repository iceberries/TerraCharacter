package org.confluence.character_portal.storage;

import net.minecraft.nbt.CompoundTag;

import org.confluence.character_portal.CharacterPortal;
import org.confluence.character_portal.api.model.WorldProfile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;

public final class WorldStore {
	private WorldStore() {}

	public static Path directory(Path world) {
		return LegacyNamespace.takeOver(
				world.resolve("data/" + LegacyNamespace.LEGACY_MODID),
				world.resolve("data/" + CharacterPortal.MODID));
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

	/** Keep creation codecs and live observations separate; never overwrite level.dat. */
	public static void environment(Path world, String phase, CompoundTag data) throws IOException {
		Path path = directory(world).resolve("environment.nbt");
		var tag = new CompoundTag();
		if (Files.exists(path)) {
			tag = AtomicNbt.read(path);
			if (tag.getInt("FormatVersion") < ProfileCodec.VERSION) LegacyNamespace.rewrite(tag);
		}
		tag.putInt("FormatVersion", ProfileCodec.VERSION);
		tag.put(phase, data.copy());
		AtomicNbt.write(path, tag, true);
	}
}
