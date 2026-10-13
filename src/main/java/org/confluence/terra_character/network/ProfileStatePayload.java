package org.confluence.terra_character.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.confluence.terra_character.runtime.CharacterSession;
import org.confluence.terra_character.storage.ProfileCodec;

public record ProfileStatePayload(CompoundTag data) implements CustomPacketPayload {
	public static final Type<ProfileStatePayload> TYPE =
			new Type<>(ResourceLocation.parse("terra_character:profile_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ProfileStatePayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.COMPOUND_TAG,
					ProfileStatePayload::data,
					ProfileStatePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(ProfileStatePayload payload, IPayloadContext context) {
		context.enqueueWork(
				() -> {
					var player = context.player();
					if (player != null && context.connection().isConnected())
						player.getPersistentData()
								.put("terra_character:profile_state", payload.data().copy());
				});
	}

	public static void send(ServerPlayer player) {
		var session = CharacterSession.of(player);
		if (session == null) return;
		var tag = new CompoundTag();
		tag.put("Profile", ProfileCodec.character(session.profile()));
		tag.put("Journey", session.journey().copy());
		tag.putBoolean(
				"TimeFrozen", !player.level().getGameRules().getBoolean(GameRules.RULE_DAYLIGHT));
		tag.putBoolean(
				"WeatherFrozen",
				!player.level().getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE));
		PacketDistributor.sendToPlayer(player, new ProfileStatePayload(tag));
	}
}
