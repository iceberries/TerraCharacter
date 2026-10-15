package org.confluence.terra_character.compat.confluence.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.world.DifficultyInstance;

import org.confluence.mod.common.init.ModSecretSeeds;
import org.confluence.terra_character.compat.confluence.world.ConfluenceWorldOptions;
import org.confluence.terra_character.compat.confluence.world.WorldEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DifficultyInstance.class)
public abstract class DifficultyMixin {
	@ModifyReturnValue(method = "getEffectiveDifficulty", at = @At("RETURN"))
	private float selectedDifficulty(float original) {
		var settings = WorldEvents.runningWorld();
		if (settings == null) return original;
		var options = ConfluenceWorldOptions.readFrom(settings);
		if (options.isEmpty()) return original;
		float base =
				switch (options.get().difficulty()) {
					case CLASSIC -> 0.75F;
					case EXPERT -> 1.5F;
					case MASTER -> 2.25F;
				};
		return base + (ModSecretSeeds.FOR_THE_WORTHY.match() ? 1F : 0F);
	}
}
