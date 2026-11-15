package org.confluence.terra_character.api.model;

/** Character rules, independent of world combat difficulty. */
public enum CharacterDifficulty {
	JOURNEY,
	SOFTCORE,
	MEDIUMCORE,
	HARDCORE,
	CREATIVE,
	SURVIVAL,
	VANILLA_HARDCORE;

	public boolean isHardcore() {
		return this == HARDCORE || this == VANILLA_HARDCORE;
	}
}
