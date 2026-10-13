package org.confluence.terra_character.client;

import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.service.*;

import java.util.List;

public final class LegacySourceScreen extends ProfileScreen {
	private final WorldIndex.Entry world;
	private final List<LegacyImporter.Source> sources;
	private int page;

	public LegacySourceScreen(
			Screen back, WorldIndex.Entry world, List<LegacyImporter.Source> sources) {
		super(back, ClientProfiles.text("import"));
		this.world = world;
		this.sources = sources;
	}

	@Override
	protected void init() {
		int rows = Math.max(1, (height - 100) / 24);
		int span = Math.min(290, width - 24), left = (width - span) / 2, half = (span - 8) / 2;
		page = Math.min(page, Math.max(0, (sources.size() - 1) / rows));
		for (int i = page * rows; i < Math.min(sources.size(), (page + 1) * rows); i++) {
			var source = sources.get(i);
			button(
					left,
					36 + (i - page * rows) * 24,
					span,
					Component.literal(source.label()),
					() ->
							minecraft.setScreen(
									new ConfirmScreen(
											ok -> {
												if (!ok) {
													minecraft.setScreen(this);
													return;
												}
												var suggested =
														world.hardcore()
																? CharacterDifficulty.HARDCORE
																: CharacterDifficulty.MEDIUMCORE;
												minecraft.setScreen(
														new CharacterEditorScreen(
																		this,
																		null,
																		(name,
																				diff,
																				appearance) -> {
																			var imported =
																					LegacyImporter
																							.importCharacter(
																									ClientProfiles
																											.store(),
																									world,
																									source,
																									name,
																									diff,
																									appearance);
																			ClientProfiles
																					.setSelected(
																							imported
																									.id());
																			minecraft.setScreen(
																					new CharacterSelectionScreen(
																							new TitleScreen()));
																		})
																.suggested(suggested));
											},
											ClientProfiles.text("import"),
											ClientProfiles.text("import.help"))));
		}
		button(
								left,
								height - 60,
								half,
								Component.literal("◀"),
								() -> {
									page--;
									rebuildWidgets();
								})
						.active =
				page > 0;
		button(
								left + half + 8,
								height - 60,
								span - half - 8,
								Component.literal("▶"),
								() -> {
									page++;
									rebuildWidgets();
								})
						.active =
				(page + 1) * rows < sources.size();
		button(width / 2 - 75, height - 32, 150, ClientProfiles.text("back"), this::onClose);
	}
}
