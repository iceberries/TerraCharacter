package org.confluence.terra_character.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.api.model.CharacterAppearance;
import org.confluence.terra_character.storage.CharacterStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class ClientProfiles {
	private ClientProfiles() {}

	private static CharacterStore store;
	private static UUID selected;
	private static final Map<String, String> worldOptions = new HashMap<>();

	public static UUID getSelected() {
		return selected;
	}

	public static void setSelected(UUID value) {
		selected = value;
	}

	public static Map<String, String> getWorldOptions() {
		return Map.copyOf(worldOptions);
	}

	public static void clearWorldOptions() {
		worldOptions.clear();
	}

	public static void putWorldOptions(Map<String, String> values) {
		worldOptions.putAll(Map.copyOf(values));
	}

	public static void setWorldOption(String key, String value) {
		worldOptions.put(Objects.requireNonNull(key), Objects.requireNonNull(value));
	}

	public static void defaultWorldOption(String key, String value) {
		worldOptions.putIfAbsent(Objects.requireNonNull(key), Objects.requireNonNull(value));
	}

	public static String getWorldOption(String key, String fallback) {
		return worldOptions.getOrDefault(key, fallback);
	}

	public static synchronized CharacterStore store() {
		if (store == null)
			store =
					new CharacterStore(
							Minecraft.getInstance()
									.gameDirectory
									.toPath()
									.resolve("terra_character/characters"));
		return store;
	}

	public static Path saves() {
		return Minecraft.getInstance().getLevelSource().getBaseDir();
	}

	public static CharacterProfile selectedProfile() throws IOException {
		UUID id = selected;
		if (id == null) throw new IOException("Select a character first");
		return store().find(id).orElseThrow(() -> new IOException("Character is missing"));
	}

	public static Component text(String key, Object... args) {
		return Component.translatable("terra_character." + key, args);
	}

	public static void error(Screen back, Exception ex) {
		TerraCharacter.LOGGER.warn("Character operation failed", ex);
		Minecraft.getInstance()
				.setScreen(
						new NoticeScreen(
								back,
								text("error.title"),
								Component.literal(
										ex.getMessage() == null
												? ex.toString()
												: ex.getMessage())));
	}

	public static CharacterAppearance defaults() {
		return AccountSkins.appearance();
	}

	public static Component difficulty(CharacterDifficulty difficulty) {
		return text("difficulty." + difficulty.name().toLowerCase(Locale.ROOT));
	}
}
