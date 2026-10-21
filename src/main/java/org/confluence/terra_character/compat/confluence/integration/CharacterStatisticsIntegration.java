package org.confluence.terra_character.compat.confluence.integration;

import net.neoforged.bus.api.SubscribeEvent;

import org.confluence.mod.common.init.ModAttachmentTypes;
import org.confluence.terra_character.api.integration.CharacterStatisticsEvent;

public final class CharacterStatisticsIntegration {
	private CharacterStatisticsIntegration() {}

	@SubscribeEvent
	public static void statistics(CharacterStatisticsEvent event) {
		var player = event.getPlayer();
		if (player.isPresent()) {
			var storage = player.get().getData(ModAttachmentTypes.MANA_STORAGE);
			float maximum = Math.max(0, storage.getMaxMana());
			event.setMana(Math.clamp(storage.getCurrentMana(), 0, maximum), maximum);
		} else {
			var mana = event.getAdapterData("terra_character_confluence:mana");
			float maximum =
					Math.clamp(
							mana.getInt("stars") * 20L + mana.getInt("additionalMana"),
							0,
							Integer.MAX_VALUE);
			if (mana.isEmpty()) maximum = 20;
			float current = mana.contains("currentMana") ? mana.getFloat("currentMana") : maximum;
			if (!Float.isFinite(current)) current = 0;
			event.setMana(Math.clamp(current, 0, maximum), maximum);
		}
	}
}
