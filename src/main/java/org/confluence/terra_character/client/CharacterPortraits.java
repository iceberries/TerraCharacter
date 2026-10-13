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
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ServerLinks;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
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
import org.joml.Quaternionf;

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
		var lookup = VanillaRegistries.createLookup();
		var registries = new ArrayList<Registry<?>>();
		var builtins = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
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

	public static PreviewPlayer load(CharacterProfile profile, CompoundTag document) {
		var player = new PreviewPlayer(level(), profile);
		var adapters = document.getCompound("Adapters");
		var data = adapters.getCompound("terra_character:vanilla").getCompound("Data");
		player.getInventory().load(data.getList("Inventory", Tag.TAG_COMPOUND));
		int selected = data.getInt("SelectedItemSlot");
		player.getInventory().selected = selected >= 0 && selected < 9 ? selected : 0;
		NeoForge.EVENT_BUS.post(new LoadEvent(player, adapters));
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
	}
}
