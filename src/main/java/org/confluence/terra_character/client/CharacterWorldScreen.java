package org.confluence.terra_character.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.api.session.LaunchSelection;
import org.confluence.terra_character.runtime.CharacterSession;
import org.confluence.terra_character.service.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class CharacterWorldScreen extends ProfileScreen {
	private List<WorldIndex.Entry> worlds = List.of();
	private WorldIndex.Entry selected;
	private int page;
	private final DoubleClickSelection<String> clicks = new DoubleClickSelection<>();

	public CharacterWorldScreen(Screen back) {
		super(back, ClientProfiles.text("worlds"));
	}

	@Override
	protected void init() {
		try {
			worlds = WorldIndex.scan(ClientProfiles.saves());
			try {
				worlds = ClientStars.worlds().order(worlds, WorldIndex.Entry::folder);
			} catch (IOException ignored) {
			}
		} catch (Exception ex) {
			ClientProfiles.error(getBack(), ex);
			return;
		}
		if (selected != null) {
			String folder = selected.folder();
			selected =
					worlds.stream().filter(e -> e.folder().equals(folder)).findFirst().orElse(null);
		}
		int left = width / 2 - Math.min(220, width / 2 - 12),
				span = Math.min(440, width - 24),
				rows = Math.max(1, (height - 138) / 24);
		page = Math.min(page, Math.max(0, (worlds.size() - 1) / rows));
		for (int i = page * rows; i < Math.min(worlds.size(), (page + 1) * rows); i++) {
			var world = worlds.get(i);
			String label =
					(world.equals(selected) ? "▶ " : "")
							+ (world.profile() == null ? world.folder() : world.profile().name());
			if (world.profile() != null)
				label += " [" + world.profile().settings().difficulty().name() + "]";
			if (world.duplicate() || !world.error().isEmpty()) label += " !";
			button(
					left,
					36 + (i - page * rows) * 24,
					span - 24,
					Component.literal(font.plainSubstrByWidth(label, span - 38)),
					() -> {
						selected = world;
						if (clicks.click(world.folder(), Util.getMillis())
								&& world.profile() != null) run(this::play);
						else rebuildWidgets();
					});
			try {
				addRenderableWidget(
						new StarButton(
								left + span - 18,
								37 + (i - page * rows) * 24,
								ClientStars.worlds().contains(world.folder()),
								() ->
										run(
												() -> {
													ClientStars.worlds().toggle(world.folder());
													clicks.reset();
													page = 0;
													rebuildWidgets();
												})));
			} catch (IOException ignored) {
			}
		}
		button(
								left,
								height - 94,
								28,
								Component.literal("◀"),
								() -> {
									page--;
									rebuildWidgets();
								})
						.active =
				page > 0;
		button(
								left + span - 28,
								height - 94,
								28,
								Component.literal("▶"),
								() -> {
									page++;
									rebuildWidgets();
								})
						.active =
				(page + 1) * rows < worlds.size();
		button(
				left + 36,
				height - 94,
				span - 72,
				ClientProfiles.text("world.create"),
				() -> {
					ClientProfiles.clearWorldOptions();
					CreateWorldScreen.openFresh(minecraft, this);
				});
		button(left, height - 70, span / 2 - 2, ClientProfiles.text("play"), () -> run(this::play))
						.active =
				selected != null && selected.profile() != null;
		button(
								left + span / 2 + 2,
								height - 70,
								span / 2 - 2,
								ClientProfiles.text("import"),
								() -> run(this::chooseImport))
						.active =
				selected != null && selected.profile() != null;
		button(
								left,
								height - 46,
								span / 2 - 2,
								ClientProfiles.text("world.copy"),
								() ->
										minecraft.setScreen(
												new ConfirmScreen(
														ok -> {
															if (ok)
																run(
																		() -> {
																			WorldIndex.separateCopy(
																					selected);
																			minecraft.setScreen(
																					this);
																		});
															else minecraft.setScreen(this);
														},
														ClientProfiles.text("world.copy"),
														ClientProfiles.text("world.copy.help"))))
						.active =
				selected != null && selected.duplicate();
		button(
				left + span / 2 + 2,
				height - 46,
				span / 2 - 2,
				ClientProfiles.text("back"),
				this::onClose);
	}

	private void play() throws IOException {
		if (selected.duplicate())
			throw new IOException(
					"Duplicate world identity: assign the copied world a new identity first");
		if (selected.hardcore())
			throw new IOException(
					"Import this hardcore world's character, then create a non-hardcore world for"
							+ " it");
		new LaunchSelection(ClientProfiles.selectedProfile(), selected.profile());
		var target = selected;
		var character = ClientProfiles.getSelected();
		var store = ClientProfiles.store();
		minecraft.forceSetScreen(new GenericMessageScreen(ClientProfiles.text("checkpoint")));
		CompletableFuture.runAsync(
						() -> {
							try {
								CharacterSession.arm(
										store, character, target.directory(), target.profile());
							} catch (IOException ex) {
								throw new CompletionException(ex);
							}
						},
						Util.ioPool())
				.whenComplete(
						(unused, error) ->
								minecraft.tell(
										() -> {
											if (error != null) {
												ClientProfiles.error(
														this,
														error.getCause() instanceof Exception ex
																? ex
																: new IOException(error));
												return;
											}
											minecraft
													.createWorldOpenFlows()
													.openWorld(
															target.folder(),
															() -> {
																try {
																	CharacterSession
																			.cancelPending();
																} catch (IOException ignored) {
																}
																minecraft.setScreen(this);
															});
										}));
	}

	private void chooseImport() throws IOException {
		var sources = LegacyImporter.sources(selected.directory());
		if (sources.isEmpty()) throw new IOException("No legacy player data in this world");
		minecraft.setScreen(new LegacySourceScreen(this, selected, sources));
	}

	@Override
	public void render(GuiGraphics g, int x, int y, float d) {
		super.render(g, x, y, d);
		if (worlds.isEmpty())
			g.drawCenteredString(font, ClientProfiles.text("empty"), width / 2, 52, 0xBBBBBB);
	}
}
