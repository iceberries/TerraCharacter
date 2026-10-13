package org.confluence.terra_character.network;

import net.minecraft.nbt.*;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/** Bounded, ordered compressed transfers also fit the serverbound 32 KiB payload limit. */
public final class CharacterTransfer {
	public static final int PART = 24 * 1024, MAX = 8 * 1024 * 1024;

	private CharacterTransfer() {}

	public static void send(CompoundTag tag, Consumer<byte[]> consumer) throws IOException {
		var out = new ByteArrayOutputStream();
		NbtIo.writeCompressed(tag, out);
		byte[] bytes = out.toByteArray();
		if (bytes.length > MAX) throw new IOException("Character transfer exceeds 8 MiB");
		for (int i = 0; i < bytes.length; i += PART)
			consumer.accept(Arrays.copyOfRange(bytes, i, Math.min(bytes.length, i + PART)));
	}

	public static List<byte[]> parts(CompoundTag tag) throws IOException {
		var out = new ArrayList<byte[]>();
		send(tag, out::add);
		return out;
	}

	public static final class Assembly {
		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		private int next, total = -1;

		public synchronized CompoundTag accept(int index, int count, byte[] part)
				throws IOException {
			if (part == null
					|| count < 1
					|| count > (MAX + PART - 1) / PART
					|| index != next
					|| index >= count
					|| part.length < 1
					|| part.length > PART
					|| (total != -1 && total != count)
					|| bytes.size() + part.length > MAX)
				throw new IOException("Invalid character transfer sequence or size");
			total = count;
			next++;
			bytes.write(part);
			if (next != total) return null;
			return NbtIo.readCompressed(
					new ByteArrayInputStream(bytes.toByteArray()),
					NbtAccounter.create(32L * 1024 * 1024));
		}
	}
}
