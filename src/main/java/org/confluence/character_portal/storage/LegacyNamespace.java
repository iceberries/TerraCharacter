package org.confluence.character_portal.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import org.confluence.character_portal.CharacterPortal;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Reads data written under the module's previous identifiers.
 *
 * <p>The mod id, asset namespace and stored ids changed from {@code terra_character} to {@code
 * character_portal}. Folders are taken over once and stored ids are renamed before decoding, so
 * existing saves keep working without a manual conversion.
 */
public final class LegacyNamespace {
	public static final String LEGACY_MODID = "terra_character";

	private LegacyNamespace() {}

	/** Client-wide data root; the previous directory is renamed before first use. */
	public static Path gameData(Path gameDirectory) {
		return takeOver(
				gameDirectory.resolve(LEGACY_MODID),
				gameDirectory.resolve(CharacterPortal.MODID));
	}

	/**
	 * Returns {@code current}, renaming {@code legacy} onto it once when only the old path exists.
	 * A failed move keeps the old path in use instead of hiding the data.
	 */
	public static Path takeOver(Path legacy, Path current) {
		try {
			if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) return current;
			if (!Files.isDirectory(legacy, LinkOption.NOFOLLOW_LINKS)) return current;
			Path parent = current.getParent();
			if (parent != null) Files.createDirectories(parent);
			try {
				Files.move(legacy, current, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(legacy, current);
			}
			CharacterPortal.LOGGER.info("Moved legacy data from {} to {}", legacy, current);
		} catch (IOException ex) {
			CharacterPortal.LOGGER.warn("Could not move legacy data from {}", legacy, ex);
		}
		return Files.exists(current, LinkOption.NOFOLLOW_LINKS) ? current : legacy;
	}

	/** Renames legacy ids in keys and values of a stored document. Idempotent. */
	public static void rewrite(CompoundTag tag) {
		for (String key : List.copyOf(tag.getAllKeys())) {
			Tag value = tag.get(key);
			if (value == null) continue;
			String renamed = rename(key, true);
			if (renamed.equals(key)) {
				rewriteValue(tag, key, value);
				continue;
			}
			tag.remove(key);
			tag.put(renamed, value);
			rewriteValue(tag, renamed, value);
		}
	}

	private static void rewriteValue(CompoundTag parent, String key, Tag value) {
		if (value instanceof CompoundTag compound) {
			rewrite(compound);
		} else if (value instanceof ListTag list) {
			for (int i = 0; i < list.size(); i++) {
				Tag element = list.get(i);
				if (element instanceof CompoundTag compound) {
					rewrite(compound);
				} else if (element instanceof StringTag string) {
					String current = string.getAsString();
					String renamed = rename(current, false);
					if (!renamed.equals(current)) list.set(i, StringTag.valueOf(renamed));
				}
			}
		} else if (value instanceof StringTag string) {
			String current = string.getAsString();
			String renamed = rename(current, false);
			if (!renamed.equals(current)) parent.put(key, StringTag.valueOf(renamed));
		}
	}

	/**
	 * Renames namespaced ids. Values also have to contain a path separator, so a character or
	 * world name that merely starts with the old mod id is left untouched.
	 */
	private static String rename(String id, boolean key) {
		int end = LEGACY_MODID.length();
		if (!id.startsWith(LEGACY_MODID) || id.length() == end) return id;
		char next = id.charAt(end);
		if (next != ':' && next != '_') return id;
		if (!key && id.indexOf(':') < 0) return id;
		return CharacterPortal.MODID + id.substring(end);
	}
}
