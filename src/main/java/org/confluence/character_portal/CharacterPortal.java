package org.confluence.character_portal;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import org.confluence.character_portal.api.integration.CharacterDataAdapter;
import org.confluence.character_portal.integration.AdapterRegistry;
import org.confluence.character_portal.integration.BetterExperienceCompatibility;
import org.confluence.character_portal.integration.ConfluenceCompatibility;
import org.confluence.character_portal.integration.VanillaDataAdapter;
import org.confluence.character_portal.integration.PlayerAttributesAdapter;
import org.confluence.character_portal.integration.PlayerPersistentStateAdapter;
import org.confluence.character_portal.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CharacterPortal.MODID)
public final class CharacterPortal {
	public static final String MODID = "character_portal";
	public static final Logger LOGGER = LoggerFactory.getLogger("CharacterPortal");

	public CharacterPortal(IEventBus bus) {
		AdapterRegistry.register(new VanillaDataAdapter(CharacterDataAdapter.Scope.CHARACTER));
		AdapterRegistry.register(
				new VanillaDataAdapter(CharacterDataAdapter.Scope.CHARACTER_WORLD));
		ConfluenceCompatibility.initialize(bus);
		BetterExperienceCompatibility.initialize();
		AdapterRegistry.register(new PlayerAttributesAdapter());
		AdapterRegistry.register(new PlayerPersistentStateAdapter());
		bus.addListener(
				(RegisterPayloadHandlersEvent event) -> {
					var registrar = event.registrar("8").optional();
					registrar.playToClient(
							ProfileStatePayload.TYPE,
							ProfileStatePayload.CODEC,
							ProfileStatePayload::handle);
					registrar.playToServer(
							JourneyActionPayload.TYPE,
							JourneyActionPayload.CODEC,
							JourneyActionPayload::handle);
					registrar.configurationToClient(
							CharacterHandshake.Request.TYPE,
							CharacterHandshake.Request.CODEC,
							CharacterHandshake.Request::handle);
					registrar.configurationToServer(
							CharacterHandshake.Response.TYPE,
							CharacterHandshake.Response.CODEC,
							CharacterHandshake.Response::handle);
					registrar.playToClient(
							CharacterSync.TYPE, CharacterSync.CODEC, CharacterSync::handle);
					registrar.playToServer(
							CharacterSync.Leave.TYPE,
							CharacterSync.Leave.CODEC,
							CharacterSync.Leave::handle);
					registrar.playToClient(
							AvatarPayload.TYPE, AvatarPayload.CODEC, AvatarPayload::handle);
				});
		bus.addListener(CharacterHandshake::register);
	}
}
