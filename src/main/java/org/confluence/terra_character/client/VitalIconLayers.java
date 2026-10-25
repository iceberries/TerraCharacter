package org.confluence.terra_character.client;

/** HUD pools keep a fixed value per icon; additional capacity is shown as colored layers. */
record VitalIconLayers(int icons, double layer, double inLayer, int unit) {
	static VitalIconLayers of(float current, float maximum, int unit) {
		double capacity = unit * 10D;
		double layer = Math.max(1, Math.ceil(current / capacity));
		double inLayer = current % capacity;
		if (current > 0 && inLayer == 0) inLayer = capacity;
		return new VitalIconLayers(
				Math.min(10, (int) Math.ceil(maximum / (double) unit)), layer, inLayer, unit);
	}

	float fill(int icon) {
		return (float) Math.clamp((inLayer - icon * unit) / unit, 0, 1);
	}
}
