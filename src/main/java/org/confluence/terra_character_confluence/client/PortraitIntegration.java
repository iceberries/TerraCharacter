package org.confluence.terra_character_confluence.client;

import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;

import org.confluence.mod.common.attachment.ExtraInventory;
import org.confluence.mod.common.init.ModAttachmentTypes;
import org.confluence.terra_character.client.CharacterPortraits;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;

import java.util.*;

public final class PortraitIntegration {
	@SubscribeEvent
	public static void load(CharacterPortraits.LoadEvent event) {
		var player = event.getPlayer();
		var extra =
				event.getAdapters()
						.getCompound("terra_character_confluence:extra_inventory")
						.getCompound("Data");
		if (!extra.isEmpty()) {
			var inventory = new ExtraInventory(true);
			inventory.deserializeNBT(player.registryAccess(), extra.copy());
			var dyes = extra.getList("AccessoryDye", Tag.TAG_COMPOUND);
			for (int i = 0; i < dyes.size(); i++)
				inventory.setAccessoryDye(
						i, ItemStack.parseOptional(player.registryAccess(), dyes.getCompound(i)));
			player.setData(ModAttachmentTypes.EXTRA_INVENTORY, inventory);
		}
		var data =
				event.getAdapters()
						.getCompound("terra_character_confluence:curios")
						.getCompound("Data");
		CuriosApi.getCuriosInventory(player)
				.ifPresent(
						handler -> {
							var slots = new LinkedHashMap<String, ICurioStacksHandler>();
							for (var tag : data.getList("Curios", Tag.TAG_COMPOUND)) {
								var entry = (CompoundTag) tag;
								String id = entry.getString("Identifier");
								var stacks = new CurioStacksHandler(handler, id);
								stacks.deserializeNBT(entry.getCompound("StacksHandler"));
								slots.put(id, stacks);
							}
							handler.setCurios(slots);
						});
	}
}
