package org.confluence.character_portal.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import org.confluence.character_portal.CharacterPortal;

@Mod(value = CharacterPortal.MODID, dist = Dist.CLIENT)
public final class CharacterPortalClient {
	public CharacterPortalClient(ModContainer container) {
		container.registerExtensionPoint(
				IConfigScreenFactory.class, (mod, parent) -> new ClientSettingsScreen(parent));
	}
}
