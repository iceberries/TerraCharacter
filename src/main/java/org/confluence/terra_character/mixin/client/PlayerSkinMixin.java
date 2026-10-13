package org.confluence.terra_character.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;

import org.confluence.terra_character.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractClientPlayer.class)
public abstract class PlayerSkinMixin {
	@ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
	private PlayerSkin characterSkin(PlayerSkin original) {
		var player = (AbstractClientPlayer) (Object) this;
		var profile =
				player == Minecraft.getInstance().player
						? ClientState.profile()
						: MultiplayerClient.avatar(player.getUUID());
		return profile != null ? CharacterSkins.skin(profile.appearance()) : original;
	}
}
