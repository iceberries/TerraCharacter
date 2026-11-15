package org.confluence.character_portal.api.model;

/** Character rules, independent of world combat difficulty. */
public enum CharacterDifficulty {
	JOURNEY,
	SOFTCORE,
	MEDIUMCORE,
	HARDCORE,
	ANTI_RAGE,
	CREATIVE,
	SURVIVAL,
	VANILLA_HARDCORE;

	public boolean isHardcore() {
		return this == HARDCORE || this == VANILLA_HARDCORE;
	}
}
