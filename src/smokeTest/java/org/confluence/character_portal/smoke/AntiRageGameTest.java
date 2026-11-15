package org.confluence.character_portal.smoke;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.confluence.character_portal.api.model.*;
import org.confluence.character_portal.api.integration.CharacterDataAdapter.Scope;
import org.confluence.character_portal.compat.better_experience.AttachmentAdapter;
import org.confluence.character_portal.integration.BetterExperienceCompatibility;
import org.confluence.character_portal.runtime.CharacterSession;
import org.confluence.character_portal.storage.ProfileCodec;
import java.util.Map;
import java.util.UUID;

/** Runs against the installed BetterExperience event handlers, including the optional Mixin. */
@GameTestHolder("character_portal")
@PrefixGameTestTemplate(false)
public final class AntiRageGameTest {
	@GameTest(template = "anti_rage_empty", timeoutTicks = 100)
	public static void selectedCharacterDrivesBetterExperience(GameTestHelper helper) throws Exception {
		var server = helper.getLevel().getServer();
		var config = (ModConfigSpec.BooleanValue) Class.forName(
				"com.github.edg_thexu.better_experience.config.CommonConfig").getField("ANTI_RAGE_MODE").get(null);
		boolean original = config.get();
		var keepRule = server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
		boolean originalKeep = keepRule.get();
		var adapter = new AttachmentAdapter("anti_rage", Scope.CHARACTER_WORLD);
		try {
			helper.assertTrue(BetterExperienceCompatibility.available(), "optional integration detected");
			config.set(false);
			keepRule.set(false, server);
			var anti = player(helper, "Anti", CharacterDifficulty.ANTI_RAGE);
			for (int death = 1; death <= 10; death++) {
				invoke("onDeath", LivingDeathEvent.class, new LivingDeathEvent(anti, anti.damageSources().generic()));
				helper.assertTrue(adapter.state(anti).getInt("Deaths") == death, "death counted with global config off");
				helper.assertTrue(CharacterSession.keepInventory(anti, keepRule.get()) == (death >= 3), "third death enables keep inventory");
				invoke("onRespawn", PlayerEvent.PlayerRespawnEvent.class, new PlayerEvent.PlayerRespawnEvent(anti, false));
				invoke("onPlayerTick", PlayerTickEvent.Post.class, new PlayerTickEvent.Post(anti));
				helper.assertTrue(adapter.state(anti).getInt("Deaths") == death, "tick does not clear selected mode");
				if (death >= 4 && death < 10)
					helper.assertTrue(adapter.state(anti).getLong("ProtectionUntil") > server.overworld().getGameTime(), "respawn protection applied");
			}
			helper.assertTrue(anti.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "tenth respawn grants creative");
			var saved = adapter.capture(anti);
			adapter.initialize(anti);
			helper.assertTrue(adapter.state(anti).getInt("Deaths") == 0, "new character does not inherit deaths");
			adapter.restore(anti, saved, 1);
			anti.setGameMode(GameType.SURVIVAL);
			BetterExperienceCompatibility.resumeCreative(anti);
			helper.assertTrue(anti.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "temporary creative resumes after reconnect");
			saved.putLong("CreativeUntil", 0);
			adapter.restore(anti, saved, 1);
			invoke("onPlayerTick", PlayerTickEvent.Post.class, new PlayerTickEvent.Post(anti));
			helper.assertTrue(anti.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "expired creative restores original mode");
			helper.assertTrue(adapter.state(anti).getInt("Deaths") == 0, "creative expiry resets count");
			config.set(true);
			var normal = player(helper, "Medium", CharacterDifficulty.MEDIUMCORE);
			invoke("onDeath", LivingDeathEvent.class, new LivingDeathEvent(normal, normal.damageSources().generic()));
			helper.assertTrue(adapter.state(normal).getInt("Deaths") == 0, "other character does not use global anti-rage setting");
			helper.assertTrue(!CharacterSession.keepInventory(normal, true), "mediumcore keeps its own death rule");
			helper.succeed();
		} finally {
			config.set(original);
			keepRule.set(originalKeep, server);
			CharacterSession.stop();
		}
	}

	private static ServerPlayer player(GameTestHelper helper, String name, CharacterDifficulty mode) throws Exception {
		var profile = new GameProfile(UUID.randomUUID(), name);
		var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, ClientInformation.createDefault());
		player.connection = new FakePlayer(helper.getLevel(), profile).connection;
		player.setGameMode(GameType.SURVIVAL);
		var metadata = new CharacterProfile(UUID.randomUUID(), name, mode,
				new CharacterAppearance("character_portal:wide", "character_portal:short", Map.of()), CharacterProfile.Status.ACTIVE);
		var portable = new CompoundTag();
		portable.put("Profile", ProfileCodec.character(metadata));
		portable.put("Adapters", new CompoundTag());
		portable.put("Journey", new CompoundTag());
		CharacterSession.prepareRemote(player.server, profile.getId(), portable, player.connection.getConnection());
		return player;
	}

	private static void invoke(String method, Class<?> type, Object event) throws Exception {
		Class.forName(BetterExperienceCompatibility.EVENTS).getMethod(method, type).invoke(null, event);
	}
}
