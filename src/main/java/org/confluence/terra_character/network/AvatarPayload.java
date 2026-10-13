package org.confluence.terra_character.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.confluence.terra_character.client.MultiplayerClient;
import org.confluence.terra_character.runtime.CharacterSession;
import org.confluence.terra_character.storage.ProfileCodec;

/** Account UUID identifies the rendered entity; character UUID never replaces authentication. */
public record AvatarPayload(CompoundTag data) implements CustomPacketPayload {
	public static final Type<AvatarPayload> TYPE =
			new Type<>(ResourceLocation.parse("terra_character:avatar"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AvatarPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.COMPOUND_TAG, AvatarPayload::data, AvatarPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(AvatarPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> MultiplayerClient.avatar(payload.data()));
	}

	private static AvatarPayload avatar(ServerPlayer player) {
		var data = new CompoundTag();
		data.putUUID("Account", player.getUUID());
		var session = CharacterSession.of(player);
		if (session != null) data.put("Profile", ProfileCodec.character(session.profile()));
		return new AvatarPayload(data);
	}

	private static void send(ServerPlayer recipient, AvatarPayload payload) {
		if (recipient.connection.hasChannel(TYPE))
			PacketDistributor.sendToPlayer(recipient, payload);
	}

	public static void joined(ServerPlayer player) {
		for (var other : player.server.getPlayerList().getPlayers()) {
			send(player, avatar(other));
			if (other != player) send(other, avatar(player));
		}
	}

	public static void departed(ServerPlayer player) {
		var data = new CompoundTag();
		data.putUUID("Account", player.getUUID());
		for (var other : player.server.getPlayerList().getPlayers())
			send(other, new AvatarPayload(data));
	}
}
