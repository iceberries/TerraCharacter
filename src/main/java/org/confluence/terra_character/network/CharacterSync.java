package org.confluence.terra_character.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.confluence.terra_character.client.MultiplayerClient;
import org.confluence.terra_character.runtime.CharacterSession;

import java.io.IOException;
import java.util.UUID;

public record CharacterSync(
		UUID token, long revision, boolean leaving, int index, int total, byte[] bytes)
		implements CustomPacketPayload {
	public static final Type<CharacterSync> TYPE =
			new Type<>(ResourceLocation.parse("terra_character:character_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CharacterSync> CODEC =
			StreamCodec.of(
					(b, p) -> {
						b.writeUUID(p.token());
						b.writeLong(p.revision());
						b.writeBoolean(p.leaving());
						b.writeVarInt(p.index());
						b.writeVarInt(p.total());
						b.writeByteArray(p.bytes());
					},
					b ->
							new CharacterSync(
									b.readUUID(),
									b.readLong(),
									b.readBoolean(),
									b.readVarInt(),
									b.readVarInt(),
									b.readByteArray(CharacterTransfer.PART)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void send(
			ServerPlayer player, UUID token, long revision, CompoundTag portable, boolean leaving)
			throws IOException {
		var parts = CharacterTransfer.parts(portable);
		for (int i = 0; i < parts.size(); i++)
			PacketDistributor.sendToPlayer(
					player,
					new CharacterSync(token, revision, leaving, i, parts.size(), parts.get(i)));
	}

	public static void handle(CharacterSync payload, IPayloadContext context) {
		context.enqueueWork(() -> MultiplayerClient.sync(payload, context));
	}

	public record Leave() implements CustomPacketPayload {
		public static final Type<Leave> TYPE =
				new Type<>(ResourceLocation.parse("terra_character:save_before_leave"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Leave> CODEC =
				StreamCodec.unit(new Leave());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		public static void handle(Leave payload, IPayloadContext context) {
			context.enqueueWork(
					() -> {
						if (!(context.player() instanceof ServerPlayer player)) return;
						var session = CharacterSession.of(player);
						if (session == null) return;
						// Return cursor/crafting items before capturing, including when the window
						// is closed in a container.
						try {
							player.closeContainer();
							session.save(player);
							session.snapshot(player, true);
						} catch (Exception ex) {
							session.fail(player, ex);
						}
					});
		}
	}
}
