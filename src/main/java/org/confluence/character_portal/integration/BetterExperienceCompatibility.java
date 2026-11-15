package org.confluence.character_portal.integration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;

import org.confluence.character_portal.api.integration.CharacterDataAdapter.Scope;
import org.confluence.character_portal.api.model.CharacterDifficulty;
import org.confluence.character_portal.compat.better_experience.AttachmentAdapter;
import org.confluence.character_portal.runtime.CharacterSession;

import java.io.IOException;

/** Optional bridge: never changes BetterExperience's shared config value. */
public final class BetterExperienceCompatibility {
	public static final String MODID = "better_experience";
	public static final String EVENTS =
			"com.github.edg_thexu.better_experience.module.antirage.AntiRageEvents";
	private static final AttachmentAdapter ANTI_RAGE =
			new AttachmentAdapter("anti_rage", Scope.CHARACTER_WORLD);
	private BetterExperienceCompatibility() {}

	public static boolean available() {
		return ModList.get().isLoaded(MODID)
				&& BetterExperienceCompatibility.class.getClassLoader()
						.getResource(EVENTS.replace('.', '/') + ".class") != null;
	}

	public static void initialize() {
		if (!available()) return;
		// Absolute protection deadlines must stay with their original world clock.
		AdapterRegistry.register(ANTI_RAGE);
		for (String name : new String[] {"ender_chest", "pig_chest", "safe_chest", "auto_potion", "temp_data"})
			AdapterRegistry.register(new AttachmentAdapter(name, Scope.CHARACTER));
	}

	public static void validate(CharacterDifficulty difficulty) throws IOException {
		if (difficulty == CharacterDifficulty.ANTI_RAGE && !available())
			throw new IOException("Anti-Rage characters require BetterExperience with Anti-Rage support");
	}

	public static boolean enabled(ServerPlayer player, boolean configured) {
		var session = CharacterSession.of(player);
		return session == null
				? configured
				: session.profile().difficulty() == CharacterDifficulty.ANTI_RAGE;
	}

	public static boolean keepOnDeath(ServerPlayer player, boolean fallback) {
		var session = CharacterSession.of(player);
		if (session == null || session.profile().difficulty() != CharacterDifficulty.ANTI_RAGE)
			return fallback;
		return keepsOnDeath(ANTI_RAGE.state(player), fallback);
	}

	/** Both death handlers use LOWEST priority; account for either registration order. */
	public static boolean keepsOnDeath(CompoundTag state, boolean fallback) {
		int deaths = state.getInt("Deaths");
		return fallback || deaths >= 3 || (deaths == 2 && !state.getBoolean("AwaitingRespawn"));
	}

	public static void resumeCreative(ServerPlayer player) {
		var session = CharacterSession.of(player);
		if (session == null || session.profile().difficulty() != CharacterDifficulty.ANTI_RAGE)
			return;
		var state = ANTI_RAGE.state(player);
		if (state.contains("OriginalMode")
				&& player.server.overworld().getGameTime() < state.getLong("CreativeUntil"))
			player.setGameMode(GameType.CREATIVE);
	}
}
