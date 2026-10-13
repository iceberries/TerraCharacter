package org.confluence.terra_character.mixin;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;

import org.confluence.terra_character.runtime.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
	@Inject(method = "remove", at = @At("RETURN"))
	private void releaseCharacter(ServerPlayer player, CallbackInfo ci) {
		CharacterSession.removed(player);
	}

	@Inject(method = "load", at = @At("HEAD"), cancellable = true)
	private void characterLoad(
			ServerPlayer player, CallbackInfoReturnable<Optional<CompoundTag>> cir) {
		var session = CharacterSession.of(player);
		if (session != null)
			try {
				cir.setReturnValue(Optional.of(session.load(player)));
			} catch (IOException ex) {
				throw new IllegalStateException("Character data cannot be restored safely", ex);
			}
	}

	@Inject(method = "save", at = @At("HEAD"), cancellable = true)
	private void characterSave(ServerPlayer player, CallbackInfo ci) {
		if (CharacterSession.of(player) != null) {
			SessionEvents.save(player);
			((PlayerList) (Object) this).getPlayerStats(player).save();
			((PlayerList) (Object) this).getPlayerAdvancements(player).save();
			ci.cancel();
		}
	}

	@ModifyArg(
			method = "getPlayerStats",
			at =
					@At(
							value = "INVOKE",
							target =
									"Lnet/minecraft/stats/ServerStatsCounter;<init>(Lnet/minecraft/server/MinecraftServer;Ljava/io/File;)V"),
			index = 1)
	private File characterStats(File original, @Local(argsOnly = true) Player player) {
		var session = CharacterSession.of(player);
		if (session == null) return original;
		try {
			return session.progressPath("stats").toFile();
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@ModifyArg(
			method = "getPlayerAdvancements",
			at =
					@At(
							value = "INVOKE",
							target =
									"Lnet/minecraft/server/PlayerAdvancements;<init>(Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/server/players/PlayerList;Lnet/minecraft/server/ServerAdvancementManager;Ljava/nio/file/Path;Lnet/minecraft/server/level/ServerPlayer;)V"),
			index = 3)
	private Path characterAdvancements(Path original, @Local(argsOnly = true) ServerPlayer player) {
		var session = CharacterSession.of(player);
		if (session == null) return original;
		try {
			return session.progressPath("advancements");
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
