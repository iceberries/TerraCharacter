package org.confluence.terra_character.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.confluence.terra_character.api.model.CharacterProfile;
import org.confluence.terra_character.runtime.CharacterSession;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class RespawnPacketMixin {
	@Shadow public ServerPlayer player;

	@Inject(method = "handleClientCommand", at = @At("HEAD"), cancellable = true)
	private void hardcoreGate(ServerboundClientCommandPacket packet, CallbackInfo ci) {
		PacketUtils.ensureRunningOnSameThread(
				packet, (ServerGamePacketListenerImpl) (Object) this, player.serverLevel());
		var session = CharacterSession.of(player);
		if (packet.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN
				&& session != null
				&& session.profile().status() == CharacterProfile.Status.DEAD) {
			player.connection.disconnect(Component.translatable("terra_character.error.dead"));
			ci.cancel();
		}
	}
}
