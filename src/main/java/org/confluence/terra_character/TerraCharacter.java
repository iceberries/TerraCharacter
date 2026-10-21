package org.confluence.terra_character;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import org.confluence.terra_character.api.integration.CharacterDataAdapter;
import org.confluence.terra_character.integration.AdapterRegistry;
import org.confluence.terra_character.integration.ConfluenceCompatibility;
import org.confluence.terra_character.integration.VanillaDataAdapter;
import org.confluence.terra_character.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TerraCharacter.MODID)
public final class TerraCharacter {
	public static final String MODID = "terra_character";
	public static final Logger LOGGER = LoggerFactory.getLogger("TerraCharacter");

	public TerraCharacter(IEventBus bus) {
		AdapterRegistry.register(new VanillaDataAdapter(CharacterDataAdapter.Scope.CHARACTER));
		AdapterRegistry.register(
				new VanillaDataAdapter(CharacterDataAdapter.Scope.CHARACTER_WORLD));
		ConfluenceCompatibility.initialize(bus);
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
