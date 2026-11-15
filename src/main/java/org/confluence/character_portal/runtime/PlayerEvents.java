package org.confluence.character_portal.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.confluence.character_portal.CharacterPortal;
import org.confluence.character_portal.integration.AdapterRegistry;
import org.confluence.character_portal.network.ProfileStatePayload;

@EventBusSubscriber(modid = CharacterPortal.MODID)
public final class PlayerEvents {
	private PlayerEvents() {}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			var session = CharacterSession.of(player);
			if (session != null) {
				session.joined(player);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void leaving(PlayerEvent.PlayerLoggedOutEvent event) {
		CharacterSession.saveActive(event.getEntity());
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void cloned(PlayerEvent.Clone event) {
		if (event.getOriginal() instanceof ServerPlayer original
				&& event.getEntity() instanceof ServerPlayer replacement) {
			var session = CharacterSession.of(replacement);
			if (session == null) {
				return;
			}
			try {
				for (var adapter : AdapterRegistry.all()) {
					if (session.isAdapterActive(adapter)) {
						adapter.afterClone(original, replacement);
					}
				}
			} catch (Exception ex) {
				session.fail(replacement, ex);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			var session = CharacterSession.of(player);
			if (session == null) {
				return;
			}
			try {
				for (var adapter : AdapterRegistry.all()) {
					if (session.isAdapterActive(adapter)) {
						adapter.afterRestore(player);
					}
				}
				JourneyController.apply(player);
				ProfileStatePayload.send(player);
				CharacterSession.saveActive(player);
			} catch (Exception ex) {
				session.fail(player, ex);
			}
		}
	}

	@SubscribeEvent
	public static void changed(PlayerEvent.PlayerChangedDimensionEvent event) {
		CharacterSession.saveActive(event.getEntity());
	}

	@SubscribeEvent
	public static void tick$Post(PlayerTickEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			var session = CharacterSession.of(player);
			if (session == null) {
				return;
			}
			session.tickPlayTime();
			if (player.tickCount % 1200 == 0) {
				CharacterSession.saveActive(player);
			} else if (player.tickCount % 100 == 0) {
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
			if (session == null) {
				return;
			}
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
		if (event.getEntity() instanceof ServerPlayer player && JourneyController.invulnerable(player)) {
			event.setCanceled(true);
		}
	}
}
