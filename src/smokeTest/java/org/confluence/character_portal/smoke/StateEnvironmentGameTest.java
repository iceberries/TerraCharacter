package org.confluence.character_portal.smoke;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.confluence.character_portal.api.integration.CharacterDataAdapter;
import org.confluence.character_portal.integration.*;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("character_portal_state")
@PrefixGameTestTemplate(false)
public final class StateEnvironmentGameTest {
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void discoversAndIsolatesForeignState(GameTestHelper helper) {
		helper.assertTrue(StateEnvironmentFixtures.constructions == 0, "discovery never constructs holder-specific attachments");
		var codec = adapter(StateEnvironmentFixtures.CODEC);
		var holderOnly = adapter(StateEnvironmentFixtures.HOLDER_ONLY);
		helper.assertTrue(codec.scope() == CharacterDataAdapter.Scope.CHARACTER, "mod-declared scope overrides fallback");
		helper.assertTrue(holderOnly.scope() == CharacterDataAdapter.Scope.CHARACTER_WORLD, "unknown ownership stays world-local");
		helper.assertTrue(adapter(StateEnvironmentFixtures.SPECIAL).id().equals(StateEnvironmentFixtures.SPECIAL), "specialized adapter wins");
		var player = player(helper);
		helper.assertTrue(codec.capture(player).isEmpty(), "missing attachments are not created by capture");
		codec.initialize(player);
		helper.assertTrue(StateEnvironmentFixtures.constructions == 0, "initialization never invokes holder-specific defaults");
		player.setData(StateEnvironmentFixtures.codec, 42);
		var saved = codec.capture(player);
		helper.assertTrue(saved.get("Value") instanceof IntTag, "non-compound Codec payload preserved");
		var replacement = player(helper); // connection is deliberately null
		codec.restore(replacement, saved, 1);
		helper.assertTrue(replacement.getData(StateEnvironmentFixtures.codec) == 42, "restore uses registered codec before networking exists");
		codec.initialize(replacement);
		helper.assertTrue(replacement.getData(StateEnvironmentFixtures.codec) == 7, "new character receives mod default instead of previous state");
		player.getData(StateEnvironmentFixtures.holderOnly).putInt("Progress", 20);
		holderOnly.restore(replacement, holderOnly.capture(player), 1);
		helper.assertTrue(replacement.getData(StateEnvironmentFixtures.holderOnly).getInt("Progress") == 20, "holder-aware codec uses replacement owner");
		var afterDeath = player(helper);
		holderOnly.afterClone(player, afterDeath);
		helper.assertTrue(!afterDeath.hasData(StateEnvironmentFixtures.holderOnly), "automatic adapters do not override the original mod's clone decisions");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void preservesModdedAttributesWithoutTransientDuplication(GameTestHelper helper) {
		var player = player(helper);
		var holder = BuiltInRegistries.ATTRIBUTE.getHolder(StateEnvironmentFixtures.ATTRIBUTE).orElseThrow();
		var value = player.getAttribute(holder);
		helper.assertTrue(value.getBaseValue() == 59, "modded player-specific default is present");
		value.setBaseValue(77);
		value.addPermanentModifier(new AttributeModifier(ResourceLocation.parse("character_portal:smoke_permanent"), 5, AttributeModifier.Operation.ADD_VALUE));
		value.addTransientModifier(new AttributeModifier(ResourceLocation.parse("character_portal:smoke_equipment"), 30, AttributeModifier.Operation.ADD_VALUE));
		var attributes = new PlayerAttributesAdapter();
		var saved = attributes.capture(player);
		var replacement = player(helper);
		attributes.restore(replacement, saved, 1);
		attributes.restore(replacement, saved, 1);
		helper.assertTrue(replacement.getAttribute(holder).getValue() == 82, "permanent modifiers restored once, transient equipment excluded");
		attributes.initialize(replacement);
		helper.assertTrue(replacement.getAttribute(holder).getValue() == 59, "reset uses modified player defaults, not raw attribute defaults");
		var persistent = new PlayerPersistentStateAdapter();
		player.getPersistentData().putInt("foreign_mod:progress", 12);
		persistent.restore(replacement, persistent.capture(player), 1);
		persistent.initialize(player);
		helper.assertTrue(player.getPersistentData().isEmpty(), "new character clears account persistent data");
		helper.assertTrue(replacement.getPersistentData().getInt("foreign_mod:progress") == 12, "saved data is owned by the destination");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void capturesEffectiveWorldgenThroughItsCodec(GameTestHelper helper) {
		var level = helper.getLevel();
		var registries = level.registryAccess();
		var dimensions = new WorldDimensions(Map.of(LevelStem.OVERWORLD,
				new LevelStem(level.dimensionTypeRegistration(), level.getChunkSource().getGenerator())));
		var options = new WorldOptions(123456789L, false, true);
		var snapshot = WorldStateAdapter.creation(registries, options, dimensions,
				level.getServer().getWorldData().getDataConfiguration(), level.getGameRules());
		var decoded = WorldGenSettings.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries),
				snapshot.get("WorldGenSettings")).getOrThrow();
		helper.assertTrue(decoded.options().seed() == options.seed(), "effective seed preserved");
		helper.assertTrue(!decoded.options().generateStructures() && decoded.options().generateBonusChest(), "worldgen options preserved");
		helper.assertTrue(snapshot.getCompound("Registries").contains("minecraft:worldgen/biome"), "datapack biome registry detected");
		helper.assertTrue(snapshot.getCompound("GameRules").equals(level.getGameRules().createTag()), "effective game rules detected");
		helper.assertTrue(snapshot.contains("DataConfiguration"), "datapack and feature selection detected");
		helper.succeed();
	}

	private static CharacterDataAdapter adapter(ResourceLocation attachment) {
		return AdapterRegistry.all().stream().filter(a -> a.legacyAttachmentIds().contains(attachment.toString())).findFirst().orElseThrow();
	}
	private static ServerPlayer player(GameTestHelper helper) {
		return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "StateTest"), ClientInformation.createDefault());
	}
}
