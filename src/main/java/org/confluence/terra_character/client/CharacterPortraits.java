package org.confluence.terra_character.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Lifecycle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.data.worldgen.DimensionTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ServerLinks;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.network.connection.ConnectionType;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.model.CharacterProfile;
import org.confluence.terra_character.integration.DataIntegrity;
import org.confluence.terra_character.integration.PortableInventory;
import org.confluence.terra_character.integration.VanillaDataAdapter;
import org.joml.Quaternionf;

import java.io.IOException;
import java.util.*;

/** Read-only, unticked menu entities; never replace Minecraft.level/player or open a connection. */
@EventBusSubscriber(modid = TerraCharacter.MODID, value = Dist.CLIENT)
public final class CharacterPortraits {
	private static ClientLevel level;

	public static final class LoadEvent extends Event {
		private final PreviewPlayer player;
		private final CompoundTag adapters;

		public PreviewPlayer getPlayer() {
			return player;
		}

		public CompoundTag getAdapters() {
			return adapters.copy();
		}

		LoadEvent(PreviewPlayer player, CompoundTag adapters) {
			this.player = player;
			this.adapters = adapters.copy();
		}
	}

	public static final class PreviewPlayer extends AbstractClientPlayer {
		private final CharacterProfile profile;
		private boolean unresolvedItems;

		PreviewPlayer(ClientLevel level, CharacterProfile profile) {
			super(level, new GameProfile(profile.id(), profile.name()));
			this.profile = profile;
		}

		@Override
		protected PlayerInfo getPlayerInfo() {
			return null;
		}

		@Override
		public PlayerSkin getSkin() {
			return CharacterSkins.skin(profile.appearance());
		}

		@Override
		public boolean isModelPartShown(PlayerModelPart part) {
			return true;
		}

		@Override
		public boolean isSpectator() {
			return false;
		}

		@Override
		public boolean isCreative() {
			return false;
		}
	}

	@SubscribeEvent
	public static void hideName(RenderNameTagEvent event) {
		if (event.getEntity() instanceof PreviewPlayer) event.setCanRender(TriState.FALSE);
	}

	private static <T> Registry<T> copy(HolderLookup.RegistryLookup<T> lookup) {
		var registry =
				new MappedRegistry<T>(
						ResourceKey.createRegistryKey(lookup.key().location()), Lifecycle.stable());
		lookup.listElements()
				.forEach(
						holder ->
								registry.register(
										holder.key(), holder.value(), RegistrationInfo.BUILT_IN));
		return registry.freeze();
	}

	private static ClientLevel level() {
		if (level != null) return level;
		var mc = Minecraft.getInstance();
		var registries = new ArrayList<Registry<?>>();
		var builtins = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
		var lookup = previewRegistries(builtins);
		builtins.registries().forEach(entry -> registries.add(entry.value()));
		lookup.listRegistries()
				.filter(key -> builtins.registry(key).isEmpty())
				.forEach(key -> registries.add(copy(lookup.lookupOrThrow(key))));
		var access = new RegistryAccess.ImmutableRegistryAccess(registries).freeze();
		var cookie =
				new CommonListenerCookie(
						new GameProfile(UUID.randomUUID(), "Portrait"),
						null,
						access,
						FeatureFlags.DEFAULT_FLAGS,
						null,
						null,
						null,
						Map.of(),
						null,
						false,
						Map.of(),
						ServerLinks.EMPTY,
						ConnectionType.NEOFORGE);
		var connection =
				new ClientPacketListener(mc, new Connection(PacketFlow.CLIENTBOUND), cookie);
		level =
				new ClientLevel(
						connection,
						new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false),
						Level.OVERWORLD,
						access.registryOrThrow(Registries.DIMENSION_TYPE)
								.getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD),
						2,
						2,
						mc::getProfiler,
						mc.levelRenderer,
						false,
						0);
		return level;
	}

	private static HolderLookup.Provider previewRegistries(RegistryAccess builtins) {
		try {
			return VanillaRegistries.createLookup();
		} catch (RuntimeException | LinkageError ex) {
			TerraCharacter.LOGGER.debug("Using minimal registries for character previews", ex);
			return new RegistrySetBuilder()
					.add(Registries.DIMENSION_TYPE, DimensionTypes::bootstrap)
					.add(Registries.DAMAGE_TYPE, DamageTypes::bootstrap)
					.add(Registries.BIOME, context -> context.register(Biomes.PLAINS,
							new Biome.BiomeBuilder().hasPrecipitation(false).temperature(0.8F).downfall(0)
									.specialEffects(new BiomeSpecialEffects.Builder().fogColor(0)
											.waterColor(0).waterFogColor(0).skyColor(0).build())
									.mobSpawnSettings(MobSpawnSettings.EMPTY)
									.generationSettings(BiomeGenerationSettings.EMPTY).build()))
					.build(builtins);
		}
	}

	public static PreviewPlayer load(CharacterProfile profile, CompoundTag document) {
		var player = new PreviewPlayer(level(), profile);
		var adapters = document.getCompound("Adapters").copy();
		var data = adapters.getCompound(VanillaDataAdapter.PORTABLE).getCompound("Data");
		player.unresolvedItems = !PortableInventory.prepare(data, player.registryAccess()).isEmpty();
		player.getInventory().load(data.getList("Inventory", Tag.TAG_COMPOUND));
		int selected = data.getInt("SelectedItemSlot");
		player.getInventory().selected = selected >= 0 && selected < 9 ? selected : 0;
		for (String key : List.copyOf(adapters.getAllKeys())) {
			if (key.equals(VanillaDataAdapter.PORTABLE)) continue;
			try {
				DataIntegrity.validate(adapters.getCompound(key).getCompound("Data"), player.registryAccess());
			} catch (IOException | RuntimeException | LinkageError ex) {
				adapters.remove(key);
				player.unresolvedItems = true;
			}
		}
		try {
			NeoForge.EVENT_BUS.post(new LoadEvent(player, adapters));
		} catch (RuntimeException | LinkageError ex) {
			player.unresolvedItems = true;
			TerraCharacter.LOGGER.warn("Cannot load optional character portrait data for {}", profile.id(), ex);
		}
		return player;
	}

	public static void render(
			GuiGraphics g, PreviewPlayer player, int x, int bottom, int scale, float yaw) {
		var mc = Minecraft.getInstance();
		var pose = new PoseStack();
		pose.last().pose().set(g.pose().last().pose());
		pose.last().normal().set(g.pose().last().normal());
		pose.translate(x, bottom, 150);
		pose.scale(scale, -scale, scale);
		pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(yaw)));
		player.setYRot(0);
		player.yBodyRot = 0;
		player.yBodyRotO = 0;
		player.yHeadRot = 0;
		player.yHeadRotO = 0;
		player.setXRot(0);
		Lighting.setupForEntityInInventory();
		RenderSystem.enableDepthTest();
		try {
			var renderer = mc.getEntityRenderDispatcher().getRenderer(player);
			renderer.render(player, 0, 1, pose, g.bufferSource(), 15728880);
			g.flush();
		} finally {
			Lighting.setupFor3DItems();
		}
		if (player.unresolvedItems) {
			var sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
					.apply(MissingTextureAtlasSprite.getLocation());
			g.blit(x - 5, bottom - 12, 0, 10, 10, sprite);
		}
	}
}
