package org.confluence.character_portal.smoke;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.confluence.character_portal.api.integration.CharacterDataAdapter;
import org.confluence.character_portal.integration.AdapterRegistry;
import java.util.Set;

/** Test-only registrations model a foreign mod; never packaged in release jars. */
@EventBusSubscriber(modid = "character_portal", bus = EventBusSubscriber.Bus.MOD)
public final class StateEnvironmentFixtures {
	public static final ResourceLocation ATTRIBUTE = ResourceLocation.parse("character_portal:smoke_attribute");
	public static final ResourceLocation CODEC = ResourceLocation.parse("character_portal:smoke_codec");
	public static final ResourceLocation HOLDER_ONLY = ResourceLocation.parse("character_portal:smoke_holder_only");
	public static final ResourceLocation SPECIAL = ResourceLocation.parse("character_portal:smoke_specialized");
	public static AttachmentType<Integer> codec;
	public static AttachmentType<CompoundTag> holderOnly;
	public static int constructions;

	@SubscribeEvent public static void register(RegisterEvent event) {
		event.register(Registries.ATTRIBUTE, helper -> helper.register(ATTRIBUTE,
				new RangedAttribute("attribute.character_portal.smoke", 9, 0, 1000).setSyncable(true)));
		event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, helper -> {
			codec = AttachmentType.builder(() -> 7).serialize(Codec.INT).build();
			holderOnly = AttachmentType.<CompoundTag>builder(holder -> {
				if (!(holder instanceof ServerPlayer)) throw new IllegalStateException("Not a player");
				constructions++;
				return new CompoundTag();
			}).serialize(CompoundTag.CODEC).copyOnDeath().build();
			helper.register(CODEC, codec);
			helper.register(HOLDER_ONLY, holderOnly);
			helper.register(SPECIAL, AttachmentType.builder(() -> 0).serialize(Codec.INT).build());
			helper.register(ResourceLocation.parse("character_portal:smoke_transient"), AttachmentType.builder(() -> 0).build());
			AdapterRegistry.register(new CharacterDataAdapter() {
				@Override public ResourceLocation id() { return SPECIAL; }
				@Override public int schemaVersion() { return 1; }
				@Override public Scope scope() { return Scope.CHARACTER; }
				@Override public Set<String> legacyAttachmentIds() { return Set.of(SPECIAL.toString()); }
				@Override public CompoundTag capture(ServerPlayer player) { return new CompoundTag(); }
				@Override public void restore(ServerPlayer player, CompoundTag data, int version) {}
				@Override public void initialize(ServerPlayer player) {}
			});
			NeoForge.EVENT_BUS.addListener((org.confluence.character_portal.api.integration.DiscoverStateAdaptersEvent policy) ->
					policy.attachmentScope(CODEC, CharacterDataAdapter.Scope.CHARACTER));
		});
	}

	@SubscribeEvent public static void attributes(EntityAttributeModificationEvent event) {
		event.add(EntityType.PLAYER, BuiltInRegistries.ATTRIBUTE.getHolder(ATTRIBUTE).orElseThrow(), 59);
	}
}
