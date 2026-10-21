package org.confluence.terra_character.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.integration.AdapterRegistry;
import org.confluence.terra_character.network.CharacterHandshake;
import org.confluence.terra_character.network.ProfileStatePayload;

import java.io.IOException;

@EventBusSubscriber(modid = TerraCharacter.MODID)
public final class SessionEvents {
	@SubscribeEvent
	public static void starting(ServerAboutToStartEvent event) {
		try {
			CharacterSession.start(event.getServer());
		} catch (IOException ex) {
			CharacterSession.stop();
			throw new IllegalStateException("Cannot start character session", ex);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && CharacterSession.of(player) != null)
			CharacterSession.of(player).joined(player);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void leaving(PlayerEvent.PlayerLoggedOutEvent event) {
		save(event.getEntity());
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void cloned(PlayerEvent.Clone event) {
		if (event.getOriginal() instanceof ServerPlayer original
				&& event.getEntity() instanceof ServerPlayer replacement) {
			var session = CharacterSession.of(replacement);
			if (session != null)
				try {
					for (var adapter : AdapterRegistry.all())
						adapter.afterClone(original, replacement);
				} catch (Exception ex) {
					session.fail(replacement, ex);
				}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player
				&& CharacterSession.of(player) != null) {
			try {
				for (var adapter : AdapterRegistry.all()) adapter.afterRestore(player);
				JourneyController.apply(player);
				ProfileStatePayload.send(player);
				save(player);
			} catch (Exception ex) {
				CharacterSession.of(player).fail(player, ex);
			}
		}
	}

	@SubscribeEvent
	public static void changed(PlayerEvent.PlayerChangedDimensionEvent event) {
		save(event.getEntity());
	}

	@SubscribeEvent
	public static void tick(PlayerTickEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			var playing = CharacterSession.of(player);
			if (playing != null) playing.tickPlayTime();
			if (player.tickCount % 1200 == 0) save(player);
			else if (player.tickCount % 100 == 0) {
				var session = CharacterSession.of(player);
				if (session != null)
					try {
						session.syncIfChanged(player);
					} catch (Exception ex) {
						session.fail(player, ex);
					}
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void death(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			var session = CharacterSession.of(player);
			if (session == null) return;
			try {
				session.die(player);
			} catch (Exception ex) {
				event.setCanceled(true);
				session.fail(player, ex);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void damage(LivingIncomingDamageEvent event) {
		if (event.getEntity() instanceof ServerPlayer player
				&& JourneyController.enabled(player)
				&& CharacterSession.of(player).journey().getBoolean("God")) event.setCanceled(true);
	}

	@SubscribeEvent
	public static void stopping(ServerStoppingEvent event) {
		event.getServer().getPlayerList().getPlayers().forEach(SessionEvents::save);
	}

	@SubscribeEvent
	public static void stopped(ServerStoppedEvent event) {
		CharacterSession.stop();
	}

	@SubscribeEvent
	public static void serverTick(ServerTickEvent.Post event) {
		CharacterHandshake.sweep(event.getServer());
	}

	public static void save(Player entity) {
		if (entity instanceof ServerPlayer player) {
			var session = CharacterSession.of(player);
			if (session != null && !session.failed())
				try {
					session.save(player);
				} catch (Exception ex) {
					session.fail(player, ex);
				}
		}
	}
}
