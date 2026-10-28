package org.confluence.terra_character.compat.confluence.integration;

/** Character-owned receipt for the complete Confluence new-world reward. */
public interface InitialItemsState {
	String NBT_KEY = "InitialItemsGranted";

	boolean terraCharacter$hasInitialItems();

	void terraCharacter$setInitialItems(boolean granted);
}
