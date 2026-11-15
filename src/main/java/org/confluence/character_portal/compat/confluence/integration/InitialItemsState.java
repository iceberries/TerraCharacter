package org.confluence.character_portal.compat.confluence.integration;

/** Character-owned receipt for the complete Confluence new-world reward. */
public interface InitialItemsState {
	String NBT_KEY = "InitialItemsGranted";

	boolean characterPortal$hasInitialItems();

	void characterPortal$setInitialItems(boolean granted);
}
