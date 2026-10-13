package org.confluence.terra_character.network;

import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.*;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.client.MultiplayerClient;
import org.confluence.terra_character.runtime.CharacterSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** A one-use, connection-bound configuration challenge completes before PlayerList.load. */
public final class CharacterHandshake {
	private CharacterHandshake() {}

	private static final ConfigurationTask.Type TASK =
			new ConfigurationTask.Type("terra_character:select_character");
	private static final Map<Connection, CharacterTransfer.Assembly> transfers =
			new ConcurrentHashMap<>();
	private static final Map<Connection, UUID> challenges = new ConcurrentHashMap<>();

	public static void register(RegisterConfigurationTasksEvent event) {
		var listener = (ServerConfigurationPacketListenerImpl) event.getListener();
		if (listener.getConnection().isMemoryConnection()) return;
		event.register(
				new ICustomConfigurationTask() {
					@Override
					public ConfigurationTask.Type type() {
						return TASK;
					}

					@Override
					public void run(Consumer<CustomPacketPayload> sender) {
						// The listener is in CONFIGURATION; PLAY channels must be queried
						// explicitly.
						if (!listener.hasChannel(Request.TYPE)
								|| !listener.hasChannel(Response.TYPE)
								|| !NetworkRegistry.hasChannel(
										listener.getConnection(),
										ConnectionProtocol.PLAY,
										ProfileStatePayload.TYPE.id())
								|| !NetworkRegistry.hasChannel(
										listener.getConnection(),
										ConnectionProtocol.PLAY,
										AvatarPayload.TYPE.id())
								|| !NetworkRegistry.hasChannel(
										listener.getConnection(),
										ConnectionProtocol.PLAY,
										CharacterSync.TYPE.id())
								|| !NetworkRegistry.hasChannel(
										listener.getConnection(),
										ConnectionProtocol.PLAY,
										CharacterSync.Leave.TYPE.id())) {
							listener.disconnect(
									Component.translatable("terra_character.network.required"));
							return;
						}
						UUID nonce = UUID.randomUUID();
						challenges.put(listener.getConnection(), nonce);
						transfers.put(listener.getConnection(), new CharacterTransfer.Assembly());
						sender.accept(new Request(nonce));
					}
				});
	}

	public static void disconnected(Connection connection) {
		challenges.remove(connection);
		transfers.remove(connection);
		CharacterSession.disconnected(connection);
	}

	public static void clear() {
		challenges.clear();
		transfers.clear();
	}

	public static void sweep(MinecraftServer server) {
		challenges.keySet().removeIf(c -> !c.isConnected());
		transfers.keySet().removeIf(c -> !c.isConnected());
		CharacterSession.sweep(server);
	}

	public record Request(UUID nonce) implements CustomPacketPayload {
		public static final Type<Request> TYPE =
				new Type<>(ResourceLocation.parse("terra_character:select_request"));
		public static final StreamCodec<FriendlyByteBuf, Request> CODEC =
				StreamCodec.of((b, p) -> b.writeUUID(p.nonce()), b -> new Request(b.readUUID()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		public static void handle(Request request, IPayloadContext context) {
			context.enqueueWork(() -> MultiplayerClient.select(request, context));
		}
	}

	public record Response(UUID nonce, int index, int total, byte[] bytes)
			implements CustomPacketPayload {
		public static final Type<Response> TYPE =
				new Type<>(ResourceLocation.parse("terra_character:select_response"));
		public static final StreamCodec<FriendlyByteBuf, Response> CODEC =
				StreamCodec.of(
						(b, p) -> {
							b.writeUUID(p.nonce());
							b.writeVarInt(p.index());
							b.writeVarInt(p.total());
							b.writeByteArray(p.bytes());
						},
						b ->
								new Response(
										b.readUUID(),
										b.readVarInt(),
										b.readVarInt(),
										b.readByteArray(CharacterTransfer.PART)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		public static void handle(Response response, IPayloadContext context) {
			context.enqueueWork(
					() -> {
						try {
							UUID expected = challenges.get(context.connection());
							if (expected == null || !expected.equals(response.nonce()))
								throw new IOException(
										"Invalid or repeated character selection challenge");
							if (!(context.listener()
									instanceof ServerConfigurationPacketListenerImpl listener))
								throw new IOException(
										"Character selection is only allowed during configuration");
							var server = (MinecraftServer) listener.getMainThreadEventLoop();
							var assembly = transfers.get(context.connection());
							if (assembly == null)
								throw new IOException("No character transfer pending");
							var portable =
									assembly.accept(
											response.index(), response.total(), response.bytes());
							if (portable == null) return;
							challenges.remove(context.connection());
							transfers.remove(context.connection());
							var denied =
									server.getPlayerList()
											.canPlayerLogin(
													context.connection().getRemoteAddress(),
													listener.getOwner());
							if (denied != null) {
								context.disconnect(denied);
								return;
							}
							CharacterSession.prepareRemote(
									server,
									listener.getOwner().getId(),
									portable,
									context.connection());
							CharacterSession.token(listener.getOwner().getId(), expected);
							context.finishCurrentTask(TASK);
						} catch (Exception ex) {
							TerraCharacter.LOGGER.warn(
									"Remote character selection rejected: {}", ex.getMessage());
							CharacterSession.disconnected(context.connection());
							context.disconnect(
									Component.translatable(
											"terra_character.network.rejected", ex.getMessage()));
						}
					});
		}
	}
}
