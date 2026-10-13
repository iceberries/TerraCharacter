package org.confluence.terra_character_confluence.client;

import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import org.confluence.terra_character.client.CharacterPortraits;

import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.common.capability.CurioInventoryCapability;

/**
 * Menu players have no server slot sync. Supply a local handler only for these preview entities.
 */
public final class PortraitCapabilities {
	@SubscribeEvent
	public static void register(RegisterCapabilitiesEvent event) {
		event.registerEntity(
				CuriosCapability.INVENTORY,
				EntityType.PLAYER,
				(player, context) ->
						player instanceof CharacterPortraits.PreviewPlayer
								? new CurioInventoryCapability(player)
								: null);
	}
}
