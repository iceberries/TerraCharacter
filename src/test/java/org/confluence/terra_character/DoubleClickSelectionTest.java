package org.confluence.terra_character;

import static org.junit.jupiter.api.Assertions.*;

import org.confluence.terra_character.client.DoubleClickSelection;
import org.junit.jupiter.api.Test;

class DoubleClickSelectionTest {
	@Test
	void confirmsOnlySameEntryWithinWindowAndResetsAfterConfirmation() {
		var clicks = new DoubleClickSelection<String>();
		assertFalse(clicks.click("a", 1000));
		assertFalse(clicks.click("b", 1050));
		assertTrue(clicks.click("b", 1150));
		assertFalse(clicks.click("b", 1200));
		assertFalse(clicks.click("b", 1500));
		assertTrue(clicks.click("b", 1750));
		assertFalse(clicks.click("a", 1800));
		clicks.reset();
		assertFalse(clicks.click("a", 1801));
		assertFalse(clicks.click("a", 1700));
	}
}
