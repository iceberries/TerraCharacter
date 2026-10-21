package org.confluence.terra_character.compat.confluence;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

import org.confluence.mod.common.attachment.*;
import org.confluence.mod.common.init.ModAttachmentTypes;
import org.confluence.terra_character.api.integration.CharacterDataAdapter.Scope;
import org.confluence.terra_character.compat.confluence.integration.*;
import org.confluence.terra_character.compat.confluence.world.ConfluenceWorldOptions;
import org.confluence.terra_character.compat.confluence.world.WorldEvents;
import org.confluence.terra_character.integration.AdapterRegistry;

/**
 * Optional integration bootstrap registering Confluence ownership adapters and world-option
 * validation.
 */
public final class TerraCharacterConfluence {
	public static final String MODID = "terra_character_confluence";

	public static void initialize(IEventBus bus) {
		AdapterRegistry.registerWorldProvider(
				MODID,
				settings -> {
					ConfluenceWorldOptions.readFrom(settings).orElseThrow();
					for (String key : settings.extensions().keySet())
						if (key.startsWith(MODID + ":")
								&& !key.equals(ConfluenceWorldOptions.DIFFICULTY_KEY)
								&& !key.equals(ConfluenceWorldOptions.EVIL_KEY))
							throw new IllegalArgumentException("Unknown option: " + key);
				});
		AdapterRegistry.register(
				new AttachmentAdapter<>("mana", ModAttachmentTypes.MANA_STORAGE, ManaStorage::new));
		AdapterRegistry.register(
				new AttachmentAdapter<>(
						"ever_beneficial",
						ModAttachmentTypes.EVER_BENEFICIAL,
						EverBeneficial::new));
		AdapterRegistry.register(
				new AttachmentAdapter<>(
						"extra_inventory",
						ModAttachmentTypes.EXTRA_INVENTORY,
						() -> new ExtraInventory(true)));
		AdapterRegistry.register(
				new BankAdapter<>(
						"piggy_bank",
						ModAttachmentTypes.PIGGY_BANK,
						PlayerPiggyBankContainer::new));
		AdapterRegistry.register(
				new BankAdapter<>("safe", ModAttachmentTypes.SAFE, PlayerSafeContainer::new));
		AdapterRegistry.register(new SpecialDataAdapter(Scope.CHARACTER));
		AdapterRegistry.register(new SpecialDataAdapter(Scope.CHARACTER_WORLD));
		AdapterRegistry.register(new CuriosAdapter());
		NeoForge.EVENT_BUS.register(WorldEvents.class);
		NeoForge.EVENT_BUS.register(CharacterStatisticsIntegration.class);
		if (FMLEnvironment.dist.isClient()) {
			try {
				Class.forName(
								"org.confluence.terra_character.compat.confluence.client.ClientBootstrap")
						.getMethod("initialize", IEventBus.class)
						.invoke(null, bus);
			} catch (ReflectiveOperationException ex) {
				throw new IllegalStateException(
						"Cannot initialize Confluence character client integration", ex);
			}
		}
	}
}
