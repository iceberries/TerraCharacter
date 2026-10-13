package org.confluence.terra_character.integration;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import org.confluence.terra_character.api.integration.CharacterDataAdapter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * An explicit allowlist; account identity, attributes and foreign attachment NBT are never copied.
 */
public final class VanillaDataAdapter implements CharacterDataAdapter {
	public static final String PORTABLE = "terra_character:vanilla";
	public static final String LOCAL = "terra_character:world_local";
	public static final List<String> CHARACTER_KEYS =
			List.of(
					"Inventory",
					"EnderItems",
					"SelectedItemSlot",
					"XpP",
					"XpLevel",
					"XpTotal",
					"XpSeed",
					"Score",
					"Health",
					"AbsorptionAmount",
					"foodLevel",
					"foodTickTimer",
					"foodSaturationLevel",
					"foodExhaustionLevel",
					"active_effects",
					"ActiveEffects",
					"recipeBook");
	public static final List<String> WORLD_KEYS =
			List.of(
					"Pos",
					"Rotation",
					"Dimension",
					"SpawnX",
					"SpawnY",
					"SpawnZ",
					"SpawnAngle",
					"SpawnDimension",
					"SpawnForced",
					"LastDeathLocation");
	private final Scope scope;

	public VanillaDataAdapter(Scope scope) {
		this.scope = scope;
	}

	@Override
	public ResourceLocation id() {
		return ResourceLocation.parse(scope == Scope.CHARACTER ? PORTABLE : LOCAL);
	}

	@Override
	public int schemaVersion() {
		return 1;
	}

	@Override
	public Scope scope() {
		return scope;
	}

	public static CompoundTag select(CompoundTag source, List<String> keys) {
		var out = new CompoundTag();
		for (String key : keys) if (source.contains(key)) out.put(key, source.get(key).copy());
		return out;
	}

	@Override
	public CompoundTag capture(ServerPlayer player) {
		return select(
				player.saveWithoutId(new CompoundTag()),
				scope == Scope.CHARACTER ? CHARACTER_KEYS : WORLD_KEYS);
	}

	@Override
	public void restore(ServerPlayer player, CompoundTag payload, int version) {
		player.load(payload.copy());
	}

	/**
	 * Vanilla would otherwise silently discard missing recipes or move a missing dimension to the
	 * overworld.
	 */
	public static void validateReferences(
			ServerPlayer player, CompoundTag portable, CompoundTag local) throws IOException {
		for (String key : List.of("Dimension", "SpawnDimension"))
			if (local.contains(key)) {
				var id = ResourceLocation.tryParse(local.getString(key));
				if (id == null
						|| player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id))
								== null)
					throw new IOException(
							"Missing character world dimension: " + local.getString(key));
			}
		var recipes = portable.getCompound("recipeBook");
		for (String key : List.of("recipes", "toBeDisplayed"))
			for (var tag : recipes.getList(key, Tag.TAG_STRING)) {
				var id = ResourceLocation.tryParse(tag.getAsString());
				if (id == null || player.server.getRecipeManager().byKey(id).isEmpty())
					throw new IOException("Missing character recipe: " + tag.getAsString());
			}
	}

	@Override
	public void initialize(ServerPlayer player) {
		if (scope == Scope.CHARACTER) {
			player.getInventory().clearContent();
			player.getEnderChestInventory().clearContent();
			player.removeAllEffects();
			player.experienceLevel = 0;
			player.totalExperience = 0;
			player.experienceProgress = 0;
			player.setHealth(player.getMaxHealth());
			player.getFoodData().setFoodLevel(20);
			player.getFoodData().setSaturation(5);
		}
	}

	@Override
	public Optional<CompoundTag> importLegacy(CompoundTag playerTag) {
		return Optional.of(
				select(playerTag, scope == Scope.CHARACTER ? CHARACTER_KEYS : WORLD_KEYS));
	}
}
