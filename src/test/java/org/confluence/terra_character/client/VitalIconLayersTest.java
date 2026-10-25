package org.confluence.terra_character.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class VitalIconLayersTest {
	@Test
	void fullHealthUsesFixedTwoPointHeartsAndTheCorrectLayer() {
		var normal = VitalIconLayers.of(20, 20, 2);
		assertEquals(10, normal.icons());
		assertEquals(1, normal.layer());
		assertEquals(1, normal.fill(9));
		var upgraded = VitalIconLayers.of(100, 100, 2);
		assertEquals(10, upgraded.icons());
		assertEquals(5, upgraded.layer());
		assertEquals(1, upgraded.fill(9));
	}

	@Test
	void damageShowsOnlyTheRemainingPartOfTheTopLayer() {
		var damaged = VitalIconLayers.of(91, 100, 2);
		assertEquals(5, damaged.layer());
		assertEquals(1, damaged.fill(4));
		assertEquals(0.5F, damaged.fill(5));
		assertEquals(0, damaged.fill(6));
		var boundary = VitalIconLayers.of(80, 100, 2);
		assertEquals(4, boundary.layer());
		assertEquals(1, boundary.fill(9));
		assertEquals(0, VitalIconLayers.of(0, 100, 2).fill(0));
	}

	@Test
	void manaKeepsTwentyPointsPerStarAndUsesHigherLayers() {
		var mana = VitalIconLayers.of(200, 200, 20);
		assertEquals(10, mana.icons());
		assertEquals(1, mana.layer());
		assertEquals(1, mana.fill(9));
		var partial = VitalIconLayers.of(210, 400, 20);
		assertEquals(2, partial.layer());
		assertEquals(0.5F, partial.fill(0));
		assertEquals(0, partial.fill(1));
	}
}
