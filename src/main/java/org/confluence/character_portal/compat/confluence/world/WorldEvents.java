package org.confluence.character_portal.compat.confluence.world;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.*;

import org.confluence.mod.common.init.ModSecretSeeds;
import org.confluence.mod.mixed.IWorldOptions;
import org.confluence.character_portal.api.integration.ConfigureWorldEvent;
import org.confluence.character_portal.runtime.CharacterSession;

import top.theillusivec4.curios.api.event.DropRulesEvent;
import top.theillusivec4.curios.api.type.capability.ICurio;


public final class WorldEvents {
	private WorldEvents() {}

	@SubscribeEvent
	public static void configure(ConfigureWorldEvent event) {
		var configured = ConfluenceWorldOptions.readFrom(event.settings());
		if (configured.isEmpty()) return;
		var choice = configured.get();
		// Reuse the exact special-seed parser used by Confluence's native world creation screen.
		var options =
				ModSecretSeeds.matchSeed(
								event.settings().seed(),
								IWorldOptions.of(event.options())
										.confluence$copyWithoutSecretFlag())
						.left();
		var mixed = IWorldOptions.of(options);
		long flags = mixed.confluence$getSecretFlag();
		if ((flags & IWorldOptions.DW_MASK) != 0) flags |= IWorldOptions.DOUBLE_EVIL;
		else {
			flags &= ~IWorldOptions.DOUBLE_EVIL;
			flags |=
					choice.evil() == ConfluenceWorldOptions.Evil.RANDOM
							? (options.seed() % 2 == 0
									? IWorldOptions.THE_CORRUPTION
									: IWorldOptions.THE_CRIMSON)
							: choice.evil() == ConfluenceWorldOptions.Evil.CRIMSON
									? IWorldOptions.THE_CRIMSON
									: IWorldOptions.THE_CORRUPTION;
		}
		mixed.confluence$resetSecretFlag();
		mixed.confluence$withSecretFlag(flags);
		mixed.confluence$setVersion(IWorldOptions.CURRENT_VERSION);
		event.options(options);
		event.difficulty(
				switch (choice.difficulty()) {
					case CLASSIC -> Difficulty.EASY;
					case EXPERT -> Difficulty.NORMAL;
					case MASTER -> Difficulty.HARD;
				});
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void drops(DropRulesEvent event) {
		if (event.getEntity() instanceof Player player) {
			var session = CharacterSession.of(player);
			if (session != null)
				event.addOverride(
						stack -> true,
						CharacterSession.keepInventory(
								player, player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY))
								? ICurio.DropRule.ALWAYS_KEEP
								: ICurio.DropRule.ALWAYS_DROP);
		}
	}
}
