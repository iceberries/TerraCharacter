package org.confluence.terra_character.compat.pehkui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.confluence.terra_character.api.integration.CharacterDataAdapter;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

public final class PehkuiStateAdapter implements CharacterDataAdapter {
	private static final String SCALE_NBT = "pehkui:scale_data_types";
	private final Method write;
	private final Method read;
	private final Method scales;
	private final Method reset;
	private final Map<?, ?> types;

	public PehkuiStateAdapter() throws ReflectiveOperationException {
		var entity = Class.forName("virtuoel.pehkui.util.PehkuiEntityExtensions");
		var scaleData = Class.forName("virtuoel.pehkui.api.ScaleData");
		var registries = Class.forName("virtuoel.pehkui.api.ScaleRegistries");
		write = entity.getMethod("pehkui_writeScaleNbt", CompoundTag.class);
		read = entity.getMethod("pehkui_readScaleNbt", CompoundTag.class);
		scales = entity.getMethod("pehkui_getScales");
		reset = scaleData.getMethod("resetScale", boolean.class);
		types = (Map<?, ?>) registries.getField("SCALE_TYPES").get(null);
	}

	@Override
	public ResourceLocation id() {
		return ResourceLocation.parse("terra_character:pehkui");
	}

	@Override
	public int schemaVersion() {
		return 1;
	}

	@Override
	public Scope scope() {
		return Scope.CHARACTER;
	}

	@Override
	public CompoundTag capture(ServerPlayer player) {
		return (CompoundTag) invoke(write, player, new CompoundTag());
	}

	@Override
	public void initialize(ServerPlayer player) {
		for (Object scale : ((Map<?, ?>) invoke(scales, player)).values().toArray())
			invoke(reset, scale, true);
	}

	@Override
	public void restore(ServerPlayer player, CompoundTag payload, int version) {
		var saved = payload.getCompound(SCALE_NBT);
		for (String key : saved.getAllKeys()) {
			var id = ResourceLocation.tryParse(key);
			if (id == null || !types.containsKey(id) || !saved.contains(key, Tag.TAG_COMPOUND))
				throw new IllegalArgumentException("Unknown Pehkui scale type: " + key);
			var data = saved.getCompound(key);
			for (String field : data.getAllKeys())
				if (data.get(field) instanceof NumericTag number && !Double.isFinite(number.getAsDouble()))
					throw new IllegalArgumentException("Non-finite Pehkui scale: " + key);
		}
		invoke(read, player, payload);
		player.refreshDimensions();
	}

	@Override
	public Optional<CompoundTag> importLegacy(CompoundTag player) {
		if (!player.contains(SCALE_NBT, Tag.TAG_COMPOUND)) return Optional.empty();
		var data = new CompoundTag();
		data.put(SCALE_NBT, player.getCompound(SCALE_NBT).copy());
		return Optional.of(data);
	}

	private static Object invoke(Method method, Object receiver, Object... args) {
		try {
			return method.invoke(receiver, args);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Cannot access Pehkui scale state", ex);
		}
	}
}
