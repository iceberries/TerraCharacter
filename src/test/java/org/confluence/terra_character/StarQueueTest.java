package org.confluence.terra_character;

import static org.junit.jupiter.api.Assertions.*;

import org.confluence.terra_character.storage.StarQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class StarQueueTest {
	@TempDir Path directory;

	@Test
	void newestStarComesFirstAndRestarringReturnsToTheTop() throws Exception {
		var queue = new StarQueue(directory.resolve("stars.json"));
		var entries = List.of("a", "b", "c", "d");
		queue.toggle("c");
		queue.toggle("a");
		assertEquals(List.of("a", "c", "b", "d"), queue.order(entries, s -> s));
		queue.toggle("c");
		assertFalse(queue.contains("c"));
		assertEquals(List.of("a", "b", "c", "d"), queue.order(entries, s -> s));
		queue.toggle("c");
		assertEquals(List.of("c", "a", "b", "d"), queue.order(entries, s -> s));
		assertEquals(List.of("a", "b", "c", "d"), entries);
	}

	@Test
	void reloadKeepsRecencyOrderAndCharacterWorldQueuesStayIndependent() throws Exception {
		Path characters = directory.resolve("characters.json"),
				worlds = directory.resolve("worlds.json");
		var people = new StarQueue(characters);
		people.toggle("second");
		people.toggle("first");
		var levels = new StarQueue(worlds);
		levels.toggle("first");
		assertEquals(
				List.of("first", "second"),
				new StarQueue(characters).order(List.of("first", "second"), s -> s));
		assertEquals(
				List.of("first", "second"),
				new StarQueue(worlds).order(List.of("second", "first"), s -> s));
	}

	@Test
	void filteringDoesNotRemoveHiddenStarsOrChangeTheirQueuePosition() throws Exception {
		var queue = new StarQueue(directory.resolve("stars.json"));
		queue.toggle("hidden");
		queue.toggle("visible");
		assertEquals(List.of("visible", "plain"), queue.order(List.of("plain", "visible"), s -> s));
		assertEquals(
				List.of("visible", "hidden", "plain"),
				queue.order(List.of("plain", "visible", "hidden"), s -> s));
	}

	@Test
	void failedSaveKeepsPreviousQueueAndExistingCorruptFilesAreNotOverwritten() throws Exception {
		Path file = directory.resolve("stars.json");
		var queue = new StarQueue(file);
		queue.toggle("old");
		Files.delete(file);
		Files.createDirectory(file);
		Files.writeString(file.resolve("blocker"), "occupied");
		assertThrows(IOException.class, () -> queue.toggle("new"));
		assertTrue(queue.contains("old"));
		assertFalse(queue.contains("new"));
		Path corrupt = directory.resolve("corrupt.json");
		Files.writeString(corrupt, "broken JSON");
		assertThrows(IOException.class, () -> new StarQueue(corrupt));
		assertEquals("broken JSON", Files.readString(corrupt));
	}

	@Test
	void duplicateStoredStarsKeepTheirFirstPositionAndInvalidIdsAreRejected() throws Exception {
		Path file = directory.resolve("stars.json");
		Files.writeString(file, "{\"version\":1,\"queue\":[\"b\",\"a\",\"b\"]}");
		var queue = new StarQueue(file);
		assertEquals(List.of("a", "b"), queue.order(List.of("a", "b"), s -> s));
		assertThrows(IllegalArgumentException.class, () -> queue.toggle(" "));
		assertThrows(IllegalArgumentException.class, () -> queue.toggle(null));
	}
}
