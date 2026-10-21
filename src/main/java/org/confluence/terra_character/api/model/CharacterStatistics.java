package org.confluence.terra_character.api.model;

/** Last saved vitals and character-owned game ticks, independent of a world or account. */
public record CharacterStatistics(
		float health, float maxHealth, boolean hasMana, float mana, float maxMana, long playTicks) {
	public static final CharacterStatistics DEFAULT =
			new CharacterStatistics(20, 20, false, 0, 0, 0);

	public CharacterStatistics {
		if (!Float.isFinite(health)
				|| !Float.isFinite(maxHealth)
				|| maxHealth <= 0
				|| health < 0
				|| health > maxHealth
				|| !Float.isFinite(mana)
				|| !Float.isFinite(maxMana)
				|| mana < 0
				|| maxMana < 0
				|| mana > maxMana
				|| (!hasMana && (mana != 0 || maxMana != 0))
				|| playTicks < 0)
			throw new IllegalArgumentException("Invalid character statistics");
	}
}
