package org.confluence.terra_character.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.storage.SessionCheckpoint;

import java.nio.file.Files;
import java.util.*;

public final class CharacterSelectionScreen extends ProfileScreen {
	private List<CharacterProfile> profiles = List.of();
	private UUID selected;
	private int page;
	private int pageCount = 1;
	private final boolean trash;
	private final Map<UUID, CharacterPortraits.PreviewPlayer> portraits = new HashMap<>();
	private final Map<UUID, Float> rotations = new HashMap<>();
	private UUID rotatingCharacter;

	@Override
	public void removed() {
		rotatingCharacter = null;
		portraits.clear();
		super.removed();
	}

	private final DoubleClickSelection<UUID> clicks = new DoubleClickSelection<>();

	public CharacterSelectionScreen(Screen back) {
		this(back, false);
	}

	private CharacterSelectionScreen(Screen back, boolean trash) {
		super(back, ClientProfiles.text(trash ? "trash" : "characters"));
		this.trash = trash;
	}

	private CharacterProfile chosen() {
		return profiles.stream().filter(p -> p.id().equals(selected)).findFirst().orElse(null);
	}

	@Override
	protected void init() {
		rotatingCharacter = null;
		try {
			profiles = trash ? ClientProfiles.store().archived() : ClientProfiles.store().list();
		} catch (Exception ex) {
			ClientProfiles.error(getBack(), ex);
			return;
		}
		int span = Math.min(900, width - 24),
				left = (width - span) / 2,
				columns = Math.max(1, span / 126);
		int gridHeight = height - (trash ? 105 : 130);
		int cardWidth = (span - (columns - 1) * 8) / columns,
				cardHeight = Math.min(132, Math.max(trash ? 60 : 80, gridHeight));
		int rowCount = Math.max(1, (gridHeight + 8) / (cardHeight + 8)), rows = columns * rowCount;
		pageCount = Math.max(1, (profiles.size() + rows - 1) / rows);
		page = Math.min(page, pageCount - 1);
		for (int i = page * rows; i < Math.min(profiles.size(), (page + 1) * rows); i++) {
			var profile = profiles.get(i);
			int index = i - page * rows;
			int rowItems =
					Math.min(columns, profiles.size() - page * rows - index / columns * columns);
			int rowLeft = left + (span - (rowItems * cardWidth + (rowItems - 1) * 8)) / 2;
			addRenderableWidget(
					new CharacterCard(
							rowLeft + (index % columns) * (cardWidth + 8),
							35 + (index / columns) * (cardHeight + 8),
							cardWidth,
							cardHeight,
							profile));
		}
		if (!trash) {
			button(
									left,
									height - 86,
									28,
									Component.literal("◀"),
									() -> {
										clicks.reset();
										page--;
										rebuildWidgets();
									})
							.active =
					page > 0;
			button(
									left + span - 28,
									height - 86,
									28,
									Component.literal("▶"),
									() -> {
										clicks.reset();
										page++;
										rebuildWidgets();
									})
							.active =
					page + 1 < pageCount;
			button(
					left + 36,
					height - 86,
					span - 72,
					ClientProfiles.text("create"),
					() -> minecraft.setScreen(new CharacterEditorScreen(this, null, null)));
			int w = (span - 4) / 2;
			var chosen = chosen();
			button(left, height - 62, w, ClientProfiles.text("play"), this::confirmSelection)
							.active =
					chosen != null && chosen.status() == CharacterProfile.Status.ACTIVE;
			button(
									left + w + 4,
									height - 62,
									span - w - 4,
									ClientProfiles.text("delete"),
									() ->
											minecraft.setScreen(
													new ConfirmScreen(
															ok -> {
																if (ok)
																	run(
																			() -> {
																				ClientProfiles
																						.store()
																						.archive(
																								selected);
																				minecraft.setScreen(
																						this);
																			});
																else minecraft.setScreen(this);
															},
															ClientProfiles.text("delete.confirm"),
															ClientProfiles.text("delete.help"))))
							.active =
					chosen != null;
			boolean interrupted =
					selected != null
							&& Files.exists(
									SessionCheckpoint.marker(ClientProfiles.store(), selected));
			button(
					left,
					height - 38,
					span / 2 - 2,
					ClientProfiles.text(interrupted ? "recover.session" : "trash"),
					() -> {
						if (!interrupted)
							minecraft.setScreen(new CharacterSelectionScreen(this, true));
						else
							minecraft.setScreen(
									new ConfirmScreen(
											ok -> {
												if (ok)
													run(
															() -> {
																SessionCheckpoint.recover(
																		ClientProfiles.store(),
																		selected,
																		ClientProfiles.saves());
																minecraft.setScreen(this);
															});
												else minecraft.setScreen(this);
											},
											ClientProfiles.text("recover.session"),
											ClientProfiles.text("recover.help")));
					});
			if (!ClientProfiles.store().problems().isEmpty())
				button(
						width - 26,
						8,
						20,
						Component.literal("!"),
						() -> {
							var first =
									ClientProfiles.store().problems().entrySet().iterator().next();
							minecraft.setScreen(
									new ConfirmScreen(
											ok -> {
												if (ok)
													run(
															() -> {
																ClientProfiles.store()
																		.recover(first.getKey());
																minecraft.setScreen(this);
															});
												else minecraft.setScreen(this);
											},
											ClientProfiles.text("recover.backup"),
											Component.literal(
													first.getKey() + ": " + first.getValue())));
						});
			button(
					left + w + 4,
					height - 38,
					span - w - 4,
					ClientProfiles.text("back"),
					this::onClose);
		} else {
			if (pageCount > 1) {
				button(
										width / 2 - 72,
										height - 62,
										28,
										Component.literal("◀"),
										() -> {
											clicks.reset();
											page--;
											rebuildWidgets();
										})
								.active =
						page > 0;
				button(
										width / 2 + 44,
										height - 62,
										28,
										Component.literal("▶"),
										() -> {
											clicks.reset();
											page++;
											rebuildWidgets();
										})
								.active =
						page + 1 < pageCount;
			}
			int actionWidth = (span - 16) / 3;
			button(
									left,
									height - 34,
									actionWidth,
									ClientProfiles.text("restore"),
									() ->
											run(
													() -> {
														ClientProfiles.store().unarchive(selected);
														clearSelection();
														rebuildWidgets();
													}))
							.active =
					chosen() != null;
			button(
									left + actionWidth + 8,
									height - 34,
									actionWidth,
									ClientProfiles.text("delete.permanent"),
									this::confirmPermanentDelete)
							.active =
					chosen() != null;
			button(
					left + 2 * (actionWidth + 8),
					height - 34,
					span - 2 * (actionWidth + 8),
					ClientProfiles.text("back"),
					this::onClose);
		}
	}

	private void clearSelection() {
		portraits.remove(selected);
		rotations.remove(selected);
		selected = null;
		clicks.reset();
	}

	private void confirmPermanentDelete() {
		var profile = chosen();
		if (!trash || profile == null) return;
		UUID id = profile.id();
		minecraft.setScreen(
				new ConfirmScreen(
						ok -> {
							if (ok)
								run(
										() -> {
											ClientProfiles.store().deletePermanently(id);
											clearSelection();
											minecraft.setScreen(this);
										});
							else minecraft.setScreen(this);
						},
						Component.translatable(
								"terra_character.delete.permanent.confirm", profile.name()),
						ClientProfiles.text("delete.permanent.help")));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 1) {
			for (var child : children())
				if (child instanceof CharacterCard card && card.isMouseOver(mouseX, mouseY)) {
					rotatingCharacter = card.profile.id();
					clicks.reset();
					return true;
				}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(
			double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button == 1 && rotatingCharacter != null) {
			// Entity portraits use the opposite yaw convention from the raw skin preview.
			rotations.compute(
					rotatingCharacter,
					(id, yaw) -> Mth.wrapDegrees((yaw == null ? 0 : yaw) + (float) dragX));
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 1 && rotatingCharacter != null) {
			rotatingCharacter = null;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private void confirmSelection() {
		var profile = chosen();
		if (trash || profile == null || profile.status() != CharacterProfile.Status.ACTIVE) return;
		run(
				() -> {
					ClientProfiles.setSelected(selected);
					ClientProfiles.clearWorldOptions();
					minecraft.setScreen(new PlayDestinationScreen(this));
				});
	}

	private void select(CharacterProfile profile, boolean mouse) {
		selected = profile.id();
		if (mouse && clicks.click(selected, Util.getMillis())) {
			confirmSelection();
			return;
		}
		if (!mouse) clicks.reset();
		// Rebuild only the controls; the click tracker belongs to the screen, not a recycled card.
		rebuildWidgets();
	}

	private final class CharacterCard extends AbstractButton {
		private final CharacterProfile profile;
		private CharacterPortraits.PreviewPlayer portrait;

		CharacterCard(int x, int y, int w, int h, CharacterProfile profile) {
			super(x, y, w, h, Component.literal(profile.name()));
			this.profile = profile;
			setTooltip(
					Tooltip.create(
							Component.literal(profile.name())
									.append("\n")
									.append(ClientProfiles.text("characters.rotate"))));
			try {
				if (!portraits.containsKey(profile.id()))
					portraits.put(
							profile.id(),
							CharacterPortraits.load(
									profile, ClientProfiles.store().read(profile.id())));
				portrait = portraits.get(profile.id());
			} catch (Exception ex) {
				TerraCharacter.LOGGER.warn("Cannot load character portrait {}", profile.id(), ex);
			}
		}

		@Override
		public void onPress() {
			select(profile, false);
		}

		@Override
		public void onClick(double x, double y) {
			select(profile, true);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			defaultButtonNarrationText(out);
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			boolean chosen = profile.id().equals(selected);
			EditorUi.frame(g, x, y, w, h, chosen, isHoveredOrFocused());
			EditorUi.row(g, x + 8, y + h - 35, w - 16, 27, chosen, false);
			g.enableScissor(x + 8, y + 8, x + w - 8, y + h - 39);
			try {
				int scale = Math.max(1, Math.min(43, Math.min((h - 48) / 2, (w - 20) * 10 / 13)));
				float yaw = rotations.getOrDefault(profile.id(), 0f);
				if (portrait != null)
					CharacterPortraits.render(g, portrait, x + w / 2, y + h - 39, scale, yaw);
				// Convert entity portrait yaw at the raw-model rendering boundary.
				else
					CharacterSkins.preview(
							g, profile.appearance(), x + w / 2, y + h - 39, scale, 180 - yaw);
			} catch (Exception ex) {
				portrait = null;
				portraits.put(profile.id(), null);
				TerraCharacter.LOGGER.warn("Cannot render character portrait {}", profile.id(), ex);
			} finally {
				g.disableScissor();
			}
			EditorUi.text(
					g,
					Component.literal(profile.name()),
					x + 14,
					y + h - 32,
					w - 28,
					10,
					EditorUi.TEXT,
					true);
			var info =
					ModList.get().isLoaded("confluence")
							? ClientProfiles.difficulty(profile.difficulty()).copy()
							: Component.empty();
			if (profile.status() == CharacterProfile.Status.DEAD)
				info.append(ClientProfiles.text("dead"));
			EditorUi.text(
					g,
					info,
					x + 14,
					y + h - 20,
					w - 28,
					10,
					profile.status() == CharacterProfile.Status.DEAD ? 0xFFFF8888 : EditorUi.MUTED,
					true);
		}
	}

	@Override
	public void render(GuiGraphics g, int x, int y, float d) {
		super.render(g, x, y, d);
		if (trash && pageCount > 1)
			EditorUi.text(
					g,
					Component.translatable("terra_character.page", page + 1, pageCount),
					width / 2 - 40,
					height - 58,
					80,
					12,
					EditorUi.MUTED,
					true);
		if (profiles.isEmpty())
			EditorUi.text(
					g,
					ClientProfiles.text(trash ? "trash.empty" : "empty"),
					12,
					48,
					width - 24,
					20,
					EditorUi.MUTED,
					true);
	}
}
