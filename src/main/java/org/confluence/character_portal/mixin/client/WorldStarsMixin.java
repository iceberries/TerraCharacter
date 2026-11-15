package org.confluence.character_portal.mixin.client;

import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;

import org.confluence.character_portal.CharacterPortal;
import org.confluence.character_portal.client.ClientStars;
import org.confluence.character_portal.client.WorldStarEntry;
import org.confluence.character_portal.client.WorldStarsView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.IOException;
import java.util.*;

@Mixin(WorldSelectionList.class)
public abstract class WorldStarsMixin implements WorldStarsView {
	@Shadow private List<LevelSummary> currentlyDisplayedLevels;

	@ModifyVariable(method = "fillLevels", at = @At("HEAD"), argsOnly = true)
	private List<LevelSummary> orderedWorlds(List<LevelSummary> levels) {
		try {
			return ClientStars.worlds().order(levels, LevelSummary::getLevelId);
		} catch (IOException ex) {
			CharacterPortal.LOGGER.warn("Cannot read world stars", ex);
			return levels;
		}
	}

	@Override
	public void characterPortal$refreshStars() {
		if (currentlyDisplayedLevels == null) return;
		var list = (WorldSelectionList) (Object) this;
		var vanillaOrder = new HashMap<String, Integer>();
		for (int i = 0; i < currentlyDisplayedLevels.size(); i++)
			vanillaOrder.put(currentlyDisplayedLevels.get(i).getLevelId(), i);
		var entries = new ArrayList<>(list.children());
		entries.sort(
				Comparator.comparingInt(
						entry ->
								entry instanceof WorldStarEntry world
										? vanillaOrder.getOrDefault(
												world.characterPortal$worldId(), Integer.MAX_VALUE)
										: Integer.MAX_VALUE));
		try {
			var ordered =
					ClientStars.worlds()
							.order(
									entries,
									entry ->
											entry instanceof WorldStarEntry world
													? world.characterPortal$worldId()
													: "");
			// Reorder existing entries: keep selection, world icons, search and Confluence
			// metadata.
			for (int i = 0; i < ordered.size(); i++) list.children().set(i, ordered.get(i));
			list.setScrollAmount(0);
		} catch (IOException ex) {
			CharacterPortal.LOGGER.warn("Cannot reorder world stars", ex);
		}
	}
}
