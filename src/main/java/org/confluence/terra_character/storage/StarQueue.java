package org.confluence.terra_character.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/** Local UI preferences: chronological star queue, displayed newest first. */
public final class StarQueue {
	private final Path file;
	private Deque<String> queue = new ArrayDeque<>();
	private Set<String> membership = Set.of();

	public StarQueue(Path file) throws IOException {
		this.file = file.toAbsolutePath().normalize();
		if (!Files.exists(this.file)) return;
		try {
			if (Files.size(this.file) > 8 * 1024 * 1024)
				throw new IOException("Star queue too large");
			try (var reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
				var json = JsonParser.parseReader(reader).getAsJsonObject();
				if (json.get("version").getAsInt() != 1)
					throw new IOException("Unknown star queue version");
				var seen = new HashSet<String>();
				for (var value : json.getAsJsonArray("queue")) {
					String id = value.getAsString();
					validateId(id);
					if (seen.add(id)) queue.addLast(id);
					if (queue.size() > 10000) throw new IOException("Too many starred entries");
				}
				membership = Set.copyOf(seen);
			}
		} catch (RuntimeException ex) {
			throw new IOException("Invalid star queue: " + file.getFileName(), ex);
		}
	}

	public synchronized boolean contains(String id) {
		return membership.contains(id);
	}

	/** Publish only after the atomic write succeeds; a failed save does not change UI order. */
	public synchronized void toggle(String id) throws IOException {
		validateId(id);
		var next = new ArrayDeque<>(queue);
		if (!next.remove(id)) next.addLast(id);
		if (next.size() > 10000) throw new IOException("Too many starred entries");
		var json = new JsonObject();
		json.addProperty("version", 1);
		var values = new JsonArray();
		next.forEach(values::add);
		json.add("queue", values);
		Files.createDirectories(file.getParent());
		Path temporary = Files.createTempFile(file.getParent(), "stars-", ".tmp");
		try {
			Files.writeString(temporary, json.toString(), StandardCharsets.UTF_8);
			try {
				Files.move(
						temporary,
						file,
						StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
		queue = next;
		membership = Set.copyOf(next);
	}

	/** Stable sort keeps the caller's existing order for every nonstarred entry. */
	public synchronized <T> List<T> order(List<T> entries, Function<T, String> id) {
		var ranks = new HashMap<String, Integer>();
		int rank = 0;
		for (var iterator = queue.descendingIterator(); iterator.hasNext(); )
			ranks.put(iterator.next(), rank++);
		var result = new ArrayList<>(entries);
		result.sort(
				Comparator.comparingInt(
						value -> ranks.getOrDefault(id.apply(value), Integer.MAX_VALUE)));
		return List.copyOf(result);
	}

	private static void validateId(String id) {
		if (id == null || id.isBlank() || id.length() > 512)
			throw new IllegalArgumentException("Invalid starred entry ID");
	}
}
