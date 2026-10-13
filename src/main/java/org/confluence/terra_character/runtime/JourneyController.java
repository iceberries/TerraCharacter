package org.confluence.terra_character.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

import org.confluence.terra_character.api.model.CharacterDifficulty;
import org.confluence.terra_character.api.model.CharacterProfile;
import org.confluence.terra_character.network.ProfileStatePayload;

import java.io.IOException;
import java.util.Set;

public final class JourneyController {
	private JourneyController() {}

	public static boolean enabled(ServerPlayer player) {
		var session = CharacterSession.of(player);
		return session != null
				&& !session.failed()
				&& session.profile().status() == CharacterProfile.Status.ACTIVE
				&& session.profile().difficulty() == CharacterDifficulty.JOURNEY
				&& player.isAlive();
	}

	public static void apply(ServerPlayer player) {
		if (!enabled(player)) return;
		var journey = CharacterSession.of(player).journey();
		player.getAbilities().mayfly = journey.getBoolean("Flight");
		if (!player.getAbilities().mayfly) player.getAbilities().flying = false;
		player.getAbilities().invulnerable = journey.getBoolean("God");
		player.onUpdateAbilities();
	}

	public static void handle(ServerPlayer player, String action, String itemId) {
		if (!enabled(player)) return;
		var session = CharacterSession.of(player);
		if (Set.of("noon", "midnight", "time", "weather", "clear").contains(action)
				&& !player.server.isSingleplayerOwner(player.getGameProfile())
				&& !player.hasPermissions(2)) {
			player.sendSystemMessage(
					Component.translatable("terra_character.journey.operator_required"));
			return;
		}
		var journey = session.journey().copy();
		switch (action) {
			case "god" -> journey.putBoolean("God", !journey.getBoolean("God"));
			case "flight" -> journey.putBoolean("Flight", !journey.getBoolean("Flight"));
			case "noon" ->
					player.serverLevel()
							.setDayTime(
									Math.floorDiv(player.serverLevel().getDayTime(), 24000) * 24000
											+ 6000);
			case "midnight" ->
					player.serverLevel()
							.setDayTime(
									Math.floorDiv(player.serverLevel().getDayTime(), 24000) * 24000
											+ 18000);
			case "time" ->
					player.level()
							.getGameRules()
							.getRule(GameRules.RULE_DAYLIGHT)
							.set(
									!player.level()
											.getGameRules()
											.getBoolean(GameRules.RULE_DAYLIGHT),
									player.server);
			case "weather" ->
					player.level()
							.getGameRules()
							.getRule(GameRules.RULE_WEATHER_CYCLE)
							.set(
									!player.level()
											.getGameRules()
											.getBoolean(GameRules.RULE_WEATHER_CYCLE),
									player.server);
			case "clear" -> player.serverLevel().setWeatherParameters(6000, 0, false, false);
			case "research" -> {
				ItemStack held = player.getMainHandItem();
				if (held.isEmpty()) return;
				String key = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
				var research = journey.getCompound("Research");
				int needed = held.getMaxStackSize();
				int already = research.getInt(key);
				if (already >= needed) {
					player.sendSystemMessage(
							Component.translatable("terra_character.journey.already"));
					return;
				}
				int take = Math.min(held.getCount(), needed - already);
				research.putInt(key, already + take);
				held.shrink(take);
				journey.put("Research", research);
				player.sendSystemMessage(
						Component.translatable(
								"terra_character.journey.progress", already + take, needed));
			}
			case "duplicate" -> {
				var id = ResourceLocation.tryParse(itemId);
				if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return;
				var item = BuiltInRegistries.ITEM.get(id);
				var stack = new ItemStack(item);
				if (journey.getCompound("Research").getInt(id.toString()) < stack.getMaxStackSize())
					return;
				stack.setCount(stack.getMaxStackSize());
				int capacity = 0;
				for (var slot : player.getInventory().items) {
					if (slot.isEmpty()) capacity += stack.getMaxStackSize();
					else if (ItemStack.isSameItemSameComponents(slot, stack))
						capacity += Math.max(0, slot.getMaxStackSize() - slot.getCount());
				}
				if (capacity < stack.getCount()) {
					player.sendSystemMessage(
							Component.translatable("terra_character.journey.full"));
					return;
				}
				player.getInventory().add(stack);
			}
			default -> {
				return;
			}
		}
		session.journey(journey);
		apply(player);
		player.inventoryMenu.broadcastChanges();
		try {
			session.save(player);
		} catch (IOException ex) {
			session.fail(player, ex);
			return;
		}
		ProfileStatePayload.send(player);
	}
}
