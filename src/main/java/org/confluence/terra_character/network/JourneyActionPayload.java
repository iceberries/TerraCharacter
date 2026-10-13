package org.confluence.terra_character.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.confluence.terra_character.runtime.JourneyController;

public record JourneyActionPayload(String action, String item) implements CustomPacketPayload {
	public static final Type<JourneyActionPayload> TYPE =
			new Type<>(ResourceLocation.parse("terra_character:journey_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, JourneyActionPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.stringUtf8(32),
					JourneyActionPayload::action,
					ByteBufCodecs.stringUtf8(256),
					JourneyActionPayload::item,
					JourneyActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(JourneyActionPayload payload, IPayloadContext context) {
		context.enqueueWork(
				() -> {
					if (context.player() instanceof ServerPlayer player)
						JourneyController.handle(player, payload.action(), payload.item());
				});
	}
}
