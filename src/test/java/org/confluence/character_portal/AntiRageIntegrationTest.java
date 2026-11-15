package org.confluence.character_portal;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundTag;
import org.confluence.character_portal.api.model.*;
import org.confluence.character_portal.integration.BetterExperienceCompatibility;
import org.confluence.character_portal.storage.ProfileCodec;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;

class AntiRageIntegrationTest {
	@Test
	void selectedModeSurvivesProfileSerialization() throws Exception {
		var profile = new CharacterProfile(UUID.randomUUID(), "Anti-Rage", CharacterDifficulty.ANTI_RAGE,
				new CharacterAppearance("character_portal:wide", "character_portal:short", Map.of()),
				CharacterProfile.Status.ACTIVE);
		assertEquals(profile, ProfileCodec.character(ProfileCodec.character(profile)));
	}

	@Test
	void thirdDeathKeepsModdedInventoryRegardlessOfHandlerRegistrationOrder() {
		var state = new CompoundTag();
		assertFalse(BetterExperienceCompatibility.keepsOnDeath(state, false));
		state.putInt("Deaths", 1);
		assertFalse(BetterExperienceCompatibility.keepsOnDeath(state, false));
		state.putInt("Deaths", 2);
		state.putBoolean("AwaitingRespawn", true);
		assertFalse(BetterExperienceCompatibility.keepsOnDeath(state, false));
		// Before BetterExperience handles the third death.
		state.putBoolean("AwaitingRespawn", false);
		assertTrue(BetterExperienceCompatibility.keepsOnDeath(state, false));
		// After BetterExperience handles the same death.
		state.putInt("Deaths", 3);
		state.putBoolean("AwaitingRespawn", true);
		assertTrue(BetterExperienceCompatibility.keepsOnDeath(state, false));
		assertTrue(BetterExperienceCompatibility.keepsOnDeath(new CompoundTag(), true));
	}
}
