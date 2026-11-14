package org.confluence.terra_character.integration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/** Separates unresolved recipe references before vanilla loads the character's recipe book. */
public final class PortableRecipes {
	public static final String DEFERRED = "UnresolvedRecipes";

	private PortableRecipes() {}

	public static CompoundTag prepare(CompoundTag player, Predicate<String> exists) {
		var book = player.getCompound("recipeBook");
		var previous = player.getCompound(DEFERRED);
		var deferred = new CompoundTag();
		player.remove(DEFERRED);
		for (String key : List.of("recipes", "toBeDisplayed")) {
			var entries = new LinkedHashSet<Tag>(book.getList(key, Tag.TAG_STRING));
			entries.addAll(previous.getList(key, Tag.TAG_STRING));
			if (entries.isEmpty()) continue;
			var usable = new ListTag();
			var unresolved = new ListTag();
			for (Tag entry : entries)
				(exists.test(entry.getAsString()) ? usable : unresolved).add(entry);
			book.put(key, usable);
			if (!unresolved.isEmpty()) deferred.put(key, unresolved);
		}
		if (!book.isEmpty()) player.put("recipeBook", book);
		return deferred;
	}
}
