package org.confluence.terra_character.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModList;

import org.confluence.terra_character.api.model.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class CharacterEditorScreen extends ProfileScreen {
	@FunctionalInterface
	public interface ImportAction {
		void save(String name, CharacterDifficulty difficulty, CharacterAppearance appearance)
				throws Exception;
	}

	private final CharacterProfile existing;
	private final ImportAction importer;
	private CharacterAppearance draft;
	private CharacterDifficulty difficulty;
	private String nameValue, colorChannel;
	private EditBox name;
	private ColorControls colorControls;
	private DiscreteSlider difficultyControl;
	private final SkinPreview preview = new SkinPreview();
	private CompletableFuture<AccountSkins.Result> accountSkin;
	private boolean accountSkinApplied, bodyChanged;
	private String skinStatus;

	private record Layout(int left, int span, int form, int right, int portrait) {}

	private Layout layout() {
		int span = Math.min(820, width - 24),
				left = (width - span) / 2,
				portrait = Math.max(88, Math.min(192, span / 3));
		return new Layout(left, span, span - portrait - 10, left + span - portrait, portrait);
	}

	public CharacterEditorScreen(Screen back, CharacterProfile existing, ImportAction importer) {
		super(back, ClientProfiles.text(existing == null ? "create" : "edit"));
		this.existing = existing;
		this.importer = importer;
		draft = existing == null ? ClientProfiles.defaults() : existing.appearance();
		difficulty = existing == null ? CharacterDifficulty.SOFTCORE : existing.difficulty();
		nameValue = existing == null ? "" : existing.name();
		if (existing == null) {
			skinStatus = "skin.account.loading";
			accountSkin = AccountSkins.load();
		}
	}

	public CharacterEditorScreen suggested(CharacterDifficulty value) {
		difficulty = value;
		return this;
	}

	@Override
	protected void init() {
		var l = layout();
		int x = l.left() + 10, w = l.form() - 20, half = (w - 6) / 2;
		name = new EditBox(font, x, 42, w, 18, ClientProfiles.text("name"));
		name.setMaxLength(64);
		name.setHint(ClientProfiles.text("name"));
		name.setValue(nameValue);
		name.setResponder(value -> nameValue = value);
		addRenderableWidget(name);
		boolean difficultyAvailable = ModList.get().isLoaded("confluence");
		difficultyControl = null;
		if (difficultyAvailable) {
			difficultyControl =
					addRenderableWidget(
							new DiscreteSlider(
									x,
									68,
									half,
									CharacterDifficulty.values().length,
									difficulty.ordinal(),
									i -> ClientProfiles.difficulty(CharacterDifficulty.values()[i]),
									i -> {
										difficulty = CharacterDifficulty.values()[i];
										if (difficultyControl != null)
											difficultyControl.setTooltip(
													Tooltip.create(
															ClientProfiles.text(
																	"difficulty.help."
																			+ difficulty
																					.name()
																					.toLowerCase(
																							Locale
																									.ROOT))));
									}));
			difficultyControl.active = existing == null;
			difficultyControl.setTooltip(
					Tooltip.create(
							ClientProfiles.text(
									"difficulty.help."
											+ difficulty.name().toLowerCase(Locale.ROOT))));
		}
		addRenderableWidget(
				OptionDropdown.button(
						this,
						difficultyAvailable ? x + half + 6 : x,
						68,
						difficultyAvailable ? w - half - 6 : w,
						List.of("terra_character:slim", "terra_character:wide"),
						draft.bodyStyle(),
						value ->
								ClientProfiles.text(
										value.endsWith(":slim") ? "body.slim" : "body.wide"),
						value -> {
							bodyChanged = true;
							draft =
									new CharacterAppearance(
											value,
											draft.hairStyle(),
											draft.colors(),
											draft.paint(),
											draft.wardrobe(),
											draft.dyeMasks(),
											draft.baseSkin());
						}));
		var channels = EditorUi.channels(draft);
		colorControls = null;
		if (!channels.isEmpty()) {
			if (colorChannel == null || !channels.contains(colorChannel))
				colorChannel = channels.getFirst();
			addRenderableWidget(
					OptionDropdown.button(
							this,
							x,
							104,
							w - 100,
							channels,
							colorChannel,
							EditorUi::channel,
							value -> {
								colorChannel = value;
								if (colorControls != null)
									colorControls.loadSilently(EditorUi.colorValue(draft, value));
							}));
			addRenderableWidget(
					EditorUi.button(
							x + w - 96,
							104,
							54,
							18,
							ClientProfiles.text("color.reset"),
							() -> {
								draft = EditorUi.resetColor(draft, colorChannel);
								rebuildWidgets();
							}));
			addRenderableWidget(
					EditorUi.button(
							x + w - 38,
							104,
							38,
							18,
							ClientProfiles.text("random"),
							() -> {
								draft =
										EditorUi.color(
												draft,
												colorChannel,
												new Random().nextInt(0x1000000));
								colorControls.loadSilently(
										EditorUi.colorValue(draft, colorChannel));
							}));
			colorControls =
					new ColorControls(
							x,
							126,
							w,
							Math.max(70, Math.min(160, height - 170)),
							EditorUi.colorValue(draft, colorChannel),
							this::addRenderableWidget,
							rgb -> draft = EditorUi.color(draft, colorChannel, rgb));
		}
		int right = l.right() + 8, rw = l.portrait() - 16;
		boolean ready = accountSkin == null || accountSkinApplied;
		addRenderableWidget(
								EditorUi.button(
										right,
										42,
										rw,
										18,
										ClientProfiles.text("wardrobe.title"),
										() ->
												minecraft.setScreen(
														new WardrobeScreen(
																this,
																draft,
																value -> draft = value))))
						.active =
				ready;
		addRenderableWidget(
								EditorUi.button(
										right,
										66,
										rw,
										18,
										ClientProfiles.text("skin.edit"),
										() ->
												minecraft.setScreen(
														new SkinEditorScreen(
																this,
																draft,
																value -> draft = value))))
						.active =
				ready;
		addRenderableWidget(
								EditorUi.button(
										width / 2 - 122,
										height - 28,
										118,
										18,
										ClientProfiles.text("save"),
										() ->
												run(
														() -> {
															if (colorControls != null
																	&& !colorControls.valid())
																throw new IllegalArgumentException(
																		"Use six hexadecimal color"
																				+ " digits");
															if (importer != null)
																importer.save(
																		nameValue,
																		difficulty,
																		draft);
															else if (existing == null)
																ClientProfiles.store()
																		.create(
																				new CharacterProfile(
																						UUID
																								.randomUUID(),
																						nameValue,
																						difficulty,
																						draft,
																						CharacterProfile
																								.Status
																								.ACTIVE));
															else
																ClientProfiles.store()
																		.edit(
																				new CharacterProfile(
																						existing
																								.id(),
																						nameValue,
																						existing
																								.difficulty(),
																						draft,
																						existing
																								.status()));
															if (importer == null)
																minecraft.setScreen(getBack());
														})))
						.active =
				ready;
		addRenderableWidget(
				EditorUi.button(
						width / 2 + 4,
						height - 28,
						118,
						18,
						ClientProfiles.text("cancel"),
						this::onClose));
		setInitialFocus(name);
	}

	@Override
	public void tick() {
		super.tick();
		if (accountSkin != null && !accountSkinApplied && accountSkin.isDone()) {
			var result = accountSkin.join();
			accountSkinApplied = true;
			skinStatus = result.official() ? "skin.account.ready" : "skin.account.fallback";
			if (draft.hairStyle().equals(AccountSkins.STYLE))
				draft =
						new CharacterAppearance(
								bodyChanged
										? draft.bodyStyle()
										: result.skin().slim()
												? "terra_character:slim"
												: "terra_character:wide",
								draft.hairStyle(),
								draft.colors(),
								draft.paint(),
								draft.wardrobe(),
								draft.dyeMasks(),
								result.skin());
			rebuildWidgets();
		}
	}

	@Override
	public void renderBackground(GuiGraphics g, int x, int y, float delta) {
		super.renderBackground(g, x, y, delta);
		var l = layout();
		EditorUi.panel(g, l.left(), 32, l.form(), height - 68);
		EditorUi.panel(g, l.right(), 32, l.portrait(), height - 68);
	}

	@Override
	public void render(GuiGraphics g, int x, int y, float delta) {
		super.render(g, x, y, delta);
		var l = layout();
		EditorUi.text(
				g,
				ClientProfiles.text("color.channels"),
				l.left() + 10,
				92,
				l.form() - 20,
				10,
				EditorUi.GOLD,
				false);
		if (colorControls == null)
			EditorUi.wrapped(
					g,
					ClientProfiles.text("color.no_channels"),
					l.left() + 10,
					116,
					l.form() - 20,
					Math.max(1, height - 160),
					EditorUi.MUTED,
					false);
		int modelTop = skinStatus == null ? 92 : 112;
		if (skinStatus != null)
			EditorUi.wrapped(
					g,
					ClientProfiles.text(skinStatus),
					l.right() + 8,
					88,
					l.portrait() - 16,
					20,
					EditorUi.MUTED,
					true);
		int scale =
				Math.max(
						1,
						Math.min(
								64,
								Math.min(
										(height - 50 - modelTop) / 2,
										(l.portrait() - 20) * 10 / 13)));
		preview.bounds(l.right() + l.portrait() / 2, height - 50, scale);
		preview.interactionArea(
				l.right() + 8, modelTop, l.portrait() - 16, Math.max(1, height - 50 - modelTop));
		g.enableScissor(l.right() + 8, modelTop, l.right() + l.portrait() - 8, height - 44);
		try {
			preview.render(g, draft, x, true, null, null, null);
		} finally {
			g.disableScissor();
		}
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		return preview.click(x, y, button) || super.mouseClicked(x, y, button);
	}

	@Override
	public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		return preview.drag(button, dx) || super.mouseDragged(x, y, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		return preview.release(button) || super.mouseReleased(x, y, button);
	}

	@Override
	public void removed() {
		preview.close();
		super.removed();
	}
}
