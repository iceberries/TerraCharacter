package org.confluence.terra_character;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundTag;

import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.integration.VanillaDataAdapter;
import org.confluence.terra_character.service.CharacterPlayTime;
import org.confluence.terra_character.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

class CharacterStatisticsTest {
	@TempDir Path directory;

	@Test
	void oldBundlesHaveZeroTimeAndReadExistingHealthWithoutInventingMana() throws Exception {
		var old = new CompoundTag();
		var adapters = new CompoundTag();
		var envelope = new CompoundTag();
		var vanilla = new CompoundTag();
		vanilla.putFloat("Health", 15.5f);
		envelope.put("Data", vanilla);
		adapters.put(VanillaDataAdapter.PORTABLE, envelope);
		old.put("Adapters", adapters);
		assertEquals(
				new CharacterStatistics(15.5f, 20, false, 0, 0, 0),
				CharacterStatisticsCodec.read(old));
		CharacterStatisticsCodec.validate(old);
		assertFalse(old.contains("Statistics"));
		var profile = new CompoundTag();
		profile.putString("Status", "DEAD");
		old.put("Profile", profile);
		assertEquals(0, CharacterStatisticsCodec.read(old).health());
		assertEquals(20, CharacterStatisticsCodec.read(old).maxHealth());
	}

	@Test
	void vitalsAndTimeRoundTripAndMalformedNumbersAreRejected() throws Exception {
		var stats = new CharacterStatistics(17.5f, 40, true, 37.25f, 200, 123456);
		var document = new CompoundTag();
		document.put("Statistics", CharacterStatisticsCodec.encode(stats));
		CharacterStatisticsCodec.validate(document);
		assertEquals(stats, CharacterStatisticsCodec.read(document));
		document.getCompound("Statistics").putFloat("Mana", Float.NaN);
		assertThrows(IOException.class, () -> CharacterStatisticsCodec.validate(document));
		document.put("Statistics", CharacterStatisticsCodec.encode(stats));
		document.getCompound("Statistics").putLong("PlayTicks", -1);
		assertThrows(IOException.class, () -> CharacterStatisticsCodec.validate(document));
	}

	@Test
	void activeTicksAccumulateAcrossSessionsButIdleAndRepeatedSavesDoNotAddTime() {
		var time = new CharacterPlayTime(600);
		for (int i = 0; i < 100; i++) time.tick(false);
		assertEquals(600, time.getTicks());
		for (int i = 0; i < 20; i++) time.tick(true);
		assertEquals(620, time.getTicks());
		assertEquals(620, time.getTicks());
		var resumed = new CharacterPlayTime(time.getTicks());
		for (int i = 0; i < 20; i++) resumed.tick(true);
		assertEquals(640, resumed.getTicks());
		assertEquals("25:01:09", CharacterPlayTime.format(20L * (25 * 3600 + 69)));
		assertEquals("00:00:00", CharacterPlayTime.format(19));
		var saturated = new CharacterPlayTime(Long.MAX_VALUE);
		saturated.tick(true);
		assertEquals(Long.MAX_VALUE, saturated.getTicks());
	}

	@Test
	void characterStorePreservesStatisticsDuringEditsAndArchiveRestore() throws Exception {
		var profile =
				new CharacterProfile(
						UUID.randomUUID(),
						"Stats",
						CharacterDifficulty.SOFTCORE,
						new CharacterAppearance(
								"terra_character:slim", "terra_character:account", Map.of()),
						CharacterProfile.Status.ACTIVE);
		var store = new CharacterStore(directory);
		store.create(profile);
		var document = store.read(profile.id());
		var stats = new CharacterStatistics(20, 40, true, 50, 100, 72000);
		document.put("Statistics", CharacterStatisticsCodec.encode(stats));
		store.commit(profile.id(), document, 0);
		store.edit(profile);
		store.archive(profile.id());
		store.unarchive(profile.id());
		assertEquals(
				stats,
				CharacterStatisticsCodec.read(new CharacterStore(directory).read(profile.id())));
	}
}
