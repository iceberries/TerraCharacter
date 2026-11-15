package org.confluence.character_portal.compat.confluence.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import org.confluence.mod.common.attachment.PlayerSpecialData;
import org.confluence.character_portal.compat.confluence.integration.InitialItemsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the receipt in the existing character attachment, including NeoForge death copies. */
@Mixin(PlayerSpecialData.class)
public abstract class InitialItemsMixin implements InitialItemsState {
	@Unique private boolean characterPortal$initialItems;

	@Override
	public boolean characterPortal$hasInitialItems() {
		return characterPortal$initialItems;
	}

	@Override
	public void characterPortal$setInitialItems(boolean granted) {
		characterPortal$initialItems = granted;
	}

	@ModifyReturnValue(
			method =
					"serializeNBT(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;",
			at = @At("RETURN"))
	private CompoundTag saveInitialItems(CompoundTag tag) {
		tag.putBoolean(InitialItemsState.NBT_KEY, characterPortal$initialItems);
		return tag;
	}

	@Inject(
			method =
					"deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V",
			at = @At("RETURN"))
	private void loadInitialItems(
			HolderLookup.Provider registries, CompoundTag tag, CallbackInfo ci) {
		characterPortal$initialItems = tag.getBoolean(InitialItemsState.NBT_KEY);
	}
}
