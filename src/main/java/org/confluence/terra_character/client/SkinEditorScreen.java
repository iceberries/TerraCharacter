package org.confluence.terra_character.client;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.service.SelectedPart;
import org.confluence.terra_character.service.SkinCanvas;
import org.confluence.terra_character.service.SkinLayout;
import org.confluence.terra_character.service.SkinLayout.*;
import org.confluence.terra_character.service.SkinPixelBlend;
import org.confluence.terra_character.service.SkinSelection;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Face-sized pixel editor. Drafts are applied to the parent editor, then saved with the character.
 */
public final class SkinEditorScreen extends ProfileScreen {
	private enum Tool {
		BRUSH,
		ERASER,
		PICKER,
		REGION,
		SELECT
	}

	private enum LayerView {
		BASE,
		OUTER,
		ALL
	}

	private record Layout(
			int left,
			int canvasWidth,
			int selectorWidth,
			int controls,
			int controlWidth,
			int preview,
			int previewWidth) {}

	private Layout layout() {
		int span = Math.min(880, width - 24),
				canvas = (span - 16) * 40 / 100,
				controls = (span - 16) * 35 / 100,
				preview = span - canvas - controls - 16;
		return new Layout(
				(width - span) / 2,
				canvas,
				(canvas - 20) / 2,
				canvas + 8,
				controls,
				canvas + controls + 16,
				preview);
	}

	private final CharacterAppearance appearance;
	private final boolean slim;
	private final SkinCanvas canvas;
	private final Consumer<CharacterAppearance> apply;
	private DyeMasks masks;
	private WardrobeSlot regionSlot = WardrobeSlot.EYES;
	private final Deque<DyeMasks> maskUndo = new ArrayDeque<>(), maskRedo = new ArrayDeque<>();
	private int selectionX = -1,
			selectionY = -1,
			selectionEndX,
			selectionEndY,
			selectionButton = -1;
	private SkinSelection pixelSelection;
	private SkinSelection cachedSelection;
	private DyeMasks cachedSelectionMasks;
	private long cachedWardrobeGeneration = -1;
	private List<WardrobeSlot> cachedEligibleSlots = List.of();
	private boolean movingSelection;
	private int dragStartX, dragStartY, dragOffsetX, dragOffsetY;
	private Button rotateLeft, rotateRight, saveSelection;
	private Component selectionStatus = Component.empty();
	private Part part = Part.HEAD;
	private Face face = Face.FRONT;
	private Layer layer = Layer.BASE;
	private LayerView layerView = LayerView.BASE;
	private Tool tool = Tool.BRUSH;
	private int brushSize = 1, previousX = -1, previousY = -1, strokeButton = -1;
	private boolean showOuter = true;
	private final SkinPreview modelPreview = new SkinPreview();
	private int brushRgb = 0xE34C55;
	private ColorControls colors;
	private Button undo, redo;
	private NativeImage baseline;
	private final int[] palette;

	public SkinEditorScreen(
			Screen back, CharacterAppearance appearance, Consumer<CharacterAppearance> apply) {
		super(back, ClientProfiles.text("skin.edit"));
		this.appearance = appearance;
		this.apply = apply;
		masks = appearance.dyeMasks();
		slim = appearance.bodyStyle().endsWith(":slim");
		canvas = new SkinCanvas(appearance.paint());
		palette =
				new int[] {
					appearance.colors().getOrDefault("terra_character:skin", 0xDFAF88),
					appearance.colors().getOrDefault("terra_character:hair", 0x553322),
					appearance.colors().getOrDefault("terra_character:eyes", 0x3377AA),
					appearance.colors().getOrDefault("terra_character:shirt", 0x2B9D94),
					appearance.colors().getOrDefault("terra_character:pants", 0x354968),
					appearance.colors().getOrDefault("terra_character:shoes", 0x493A33),
					0xFFFFFF,
					0x111111,
					0xE34C55,
					0x22AA66,
					0x1155EE,
					0xF5CE55
				};
	}

	@Override
	protected void init() {
		if (baseline == null)
			baseline =
					CharacterSkins.image(appearance.withDyeMasks(masks).withPaint(SkinPaint.EMPTY));
		var layout = layout();
		int left = layout.left(), controls = left + layout.controls();
		addRenderableWidget(
				OptionDropdown.button(
						this,
						left + 8,
						40,
						layout.selectorWidth(),
						List.of(Part.values()),
						part,
						value -> label("part", value),
						value -> {
							finishStroke();
							clearPixelSelection();
							part = value;
						}));
		addRenderableWidget(
				OptionDropdown.button(
						this,
						left + layout.selectorWidth() + 12,
						40,
						layout.canvasWidth() - 20 - layout.selectorWidth(),
						List.of(Face.values()),
						face,
						value -> label("face", value),
						value -> {
							finishStroke();
							clearPixelSelection();
							face = value;
						}));
		addRenderableWidget(
				OptionDropdown.button(
						this,
						controls + 8,
						40,
						layout.controlWidth() - 16,
						List.of(LayerView.values()),
						layerView,
						value -> label("layer", value),
						value -> {
							finishStroke();
							clearPixelSelection();
							layerView = value;
							if (value != LayerView.ALL)
								layer = value == LayerView.BASE ? Layer.BASE : Layer.OUTER;
							if (value != LayerView.BASE) showOuter = true;
						}));
		var outer =
				addRenderableWidget(
						EditorUi.button(
								left + layout.preview() + 8,
								40,
								layout.previewWidth() - 16,
								18,
								ClientProfiles.text(
										layerView == LayerView.ALL
												? "skin.layer.all"
												: layerView == LayerView.OUTER
														? "skin.outer.editing"
														: showOuter
																? "skin.outer.show"
																: "skin.outer.hide"),
								() -> {
									showOuter = !showOuter;
									rebuildWidgets();
								}));
		outer.active = layerView == LayerView.BASE;
		addRenderableWidget(
				OptionDropdown.button(
						this,
						controls + 8,
						64,
						layout.controlWidth() - 16,
						List.of(Tool.values()),
						tool,
						value -> label("tool", value),
						value -> {
							finishStroke();
							clearPixelSelection();
							tool = value;
						}));
		colors = null;
		rotateLeft = rotateRight = saveSelection = null;
		if (tool == Tool.REGION) {
			var slots =
					Arrays.stream(WardrobeSlot.values())
							.filter(s -> s != WardrobeSlot.OUTFIT && s != WardrobeSlot.OVERALLS)
							.toList();
			addRenderableWidget(
					OptionDropdown.button(
							this,
							controls + 8,
							96,
							layout.controlWidth() - 16,
							slots,
							regionSlot,
							value ->
									ClientProfiles.text(
											"wardrobe.slot."
													+ value.name().toLowerCase(Locale.ROOT)),
							value -> regionSlot = value));
			addRenderableWidget(
					EditorUi.button(
							controls + 8,
							120,
							layout.controlWidth() - 16,
							18,
							ClientProfiles.text("skin.region.clear_face"),
							() -> clearMaskFace()));
			addRenderableWidget(
					EditorUi.button(
							controls + 8,
							144,
							layout.controlWidth() - 16,
							18,
							ClientProfiles.text("skin.region.defaults"),
							() -> changeMasks(masks.reset(regionSlot))));
		} else if (tool == Tool.SELECT) {
			int half = (layout.controlWidth() - 20) / 2;
			rotateLeft =
					addRenderableWidget(
							EditorUi.button(
									controls + 8,
									96,
									half,
									18,
									ClientProfiles.text("skin.selection.left"),
									() -> rotateSelection(-1)));
			rotateRight =
					addRenderableWidget(
							EditorUi.button(
									controls + 12 + half,
									96,
									layout.controlWidth() - 20 - half,
									18,
									ClientProfiles.text("skin.selection.right"),
									() -> rotateSelection(1)));
			addRenderableWidget(
					EditorUi.button(
							controls + 8,
							120,
							layout.controlWidth() - 16,
							18,
							ClientProfiles.text("skin.selection.clear"),
							this::clearPixelSelection));
			saveSelection =
					addRenderableWidget(
							EditorUi.button(
									controls + 8,
									144,
									layout.controlWidth() - 16,
									18,
									ClientProfiles.text("skin.selection.save"),
									this::saveSelection));
		} else
			colors =
					new ColorControls(
							controls + 8,
							96,
							layout.controlWidth() - 16,
							Math.min(132, Math.max(70, height - 168)),
							brushRgb,
							this::addRenderableWidget,
							rgb -> brushRgb = rgb);
		int historyWidth = (layout.controlWidth() - 24) / 3;
		undo =
				addRenderableWidget(
						EditorUi.button(
								controls + 8,
								height - 62,
								historyWidth,
								18,
								ClientProfiles.text("skin.undo"),
								() -> history(false)));
		redo =
				addRenderableWidget(
						EditorUi.button(
								controls + 12 + historyWidth,
								height - 62,
								historyWidth,
								18,
								ClientProfiles.text("skin.redo"),
								() -> history(true)));
		addRenderableWidget(
						EditorUi.button(
								controls + 16 + historyWidth * 2,
								height - 62,
								layout.controlWidth() - 24 - 2 * historyWidth,
								18,
								ClientProfiles.text("skin.reset.short"),
								() -> {
									if (tool == Tool.REGION) changeMasks(masks.reset(regionSlot));
									else {
										clearPixelSelection();
										canvas.reset(region());
										refreshHistory();
									}
								}))
				.setTooltip(Tooltip.create(ClientProfiles.text("skin.reset")));
		for (int i = 0; tool != Tool.REGION && tool != Tool.SELECT && i < palette.length; i++) {
			final int rgb = palette[i];
			addRenderableWidget(
							EditorUi.button(
									paletteX(i % 4),
									64 + (i / 4) * 16,
									paletteWidth(i % 4),
									12,
									Component.empty(),
									() -> {
										if (colors != null) colors.load(rgb);
									}))
					.setTooltip(Tooltip.create(Component.literal(String.format("#%06X", rgb))));
		}
		if (tool != Tool.REGION && tool != Tool.SELECT)
			addRenderableWidget(
					new DiscreteSlider(
							left + layout.preview() + 8,
							height - 62,
							layout.previewWidth() - 16,
							3,
							brushSize - 1,
							i -> ClientProfiles.text("skin.size", i + 1),
							i -> brushSize = i + 1));
		addRenderableWidget(
				EditorUi.button(
						width / 2 - 122,
						height - 28,
						118,
						18,
						ClientProfiles.text("skin.apply"),
						() -> {
							if (colors != null && !colors.valid()) return;
							finishStroke();
							apply.accept(draft());
							onClose();
						}));
		addRenderableWidget(
				EditorUi.button(
						width / 2 + 4,
						height - 28,
						118,
						18,
						ClientProfiles.text("cancel"),
						this::onClose));
		refreshHistory();
	}

	private Component label(String group, Enum<?> value) {
		return ClientProfiles.text("skin." + group + "." + value.name().toLowerCase(Locale.ROOT));
	}

	private Region region() {
		return SkinLayout.region(part, face, layer, slim);
	}

	private int zoom() {
		var r = region();
		return Math.max(
				1,
				Math.min(
						(layout().canvasWidth() - 16) / r.width(),
						Math.max(12, height - 146) / r.height()));
	}

	private int canvasX() {
		return layout().left() + (layout().canvasWidth() - region().width() * zoom()) / 2;
	}

	private int canvasY() {
		return 76 + (Math.max(12, height - 146) - region().height() * zoom()) / 2;
	}

	private boolean inside(double x, double y) {
		return x >= canvasX()
				&& y >= canvasY()
				&& x < canvasX() + region().width() * zoom()
				&& y < canvasY() + region().height() * zoom();
	}

	private void draw(double mouseX, double mouseY, int button) {
		if (!inside(mouseX, mouseY)) {
			previousX = previousY = -1;
			return;
		}
		var r = region();
		int x = (int) ((mouseX - canvasX()) / zoom()), y = (int) ((mouseY - canvasY()) / zoom());
		if (tool == Tool.PICKER && button == 0) {
			int argb = canvasPixel(x, y);
			if ((argb >>> 24) != 0) colors.load(argb & 0xFFFFFF);
			return;
		}
		boolean erase = button == 1 || tool == Tool.ERASER;
		if (!erase && !colors.valid()) return;
		int argb =
				erase ? (layer == Layer.OUTER ? SkinPaint.TRANSPARENT : 0) : 0xFF000000 | brushRgb;
		canvas.line(
				r,
				previousX < 0 ? x : previousX,
				previousY < 0 ? y : previousY,
				x,
				y,
				brushSize,
				argb);
		previousX = x;
		previousY = y;
	}

	private CharacterAppearance draft() {
		return appearance.withDyeMasks(masks).withPaint(canvas.snapshot());
	}

	private void refreshHistory() {
		if (rotateLeft != null) rotateLeft.active = pixelSelection != null;
		if (rotateRight != null) rotateRight.active = pixelSelection != null;
		if (saveSelection != null)
			saveSelection.active = pixelSelection != null && !eligibleSlots().isEmpty();
		if (saveSelection != null)
			saveSelection.setTooltip(
					Tooltip.create(
							ClientProfiles.text(
									pixelSelection != null && !saveSelection.active
											? "skin.selection.no_slot"
											: "skin.selection.save")));
		if (undo != null)
			undo.active = tool == Tool.REGION ? !maskUndo.isEmpty() : canvas.canUndo();
		if (redo != null)
			redo.active = tool == Tool.REGION ? !maskRedo.isEmpty() : canvas.canRedo();
	}

	private void finishStroke() {
		canvas.endStroke();
		strokeButton = previousX = previousY = -1;
		selectionButton = selectionX = -1;
		movingSelection = false;
		refreshHistory();
	}

	private void clearPixelSelection() {
		pixelSelection = null;
		movingSelection = false;
		selectionButton = selectionX = -1;
		selectionStatus = Component.empty();
	}

	private void updateMove() {
		var r = pixelSelection.bounds();
		var f = region();
		dragOffsetX =
				Mth.clamp(
						selectionEndX - dragStartX,
						f.x() - r.x(),
						f.x() + f.width() - r.x() - r.width());
		dragOffsetY =
				Mth.clamp(
						selectionEndY - dragStartY,
						f.y() - r.y(),
						f.y() + f.height() - r.y() - r.height());
	}

	private void transformSelection(SkinSelection next) {
		if (!next.fits(region())) {
			selectionStatus = ClientProfiles.text("skin.selection.outside");
			return;
		}
		pixelSelection.replace(canvas, region(), next, layer == Layer.OUTER);
		pixelSelection = next;
		selectionStatus = Component.empty();
		refreshHistory();
	}

	private void rotateSelection(int turns) {
		if (pixelSelection == null) return;
		try {
			transformSelection(pixelSelection.rotate(turns));
		} catch (IllegalArgumentException ex) {
			selectionStatus = ClientProfiles.text("skin.selection.outside");
		}
	}

	/**
	 * Resolve canonical dye masks into this model's UVs; explicit masks override resource defaults.
	 */
	private BitSet allowedPixels(WardrobeSlot slot) {
		var allowed = new BitSet(4096);
		var value = draft();
		var canonical = new BitSet(4096);
		for (var occupied : slot.occupied()) canonical.or(BaseSkinDyes.pixels(value, occupied));
		var bounds = pixelSelection.bounds();
		for (int y = bounds.y(); y < bounds.y() + bounds.height(); y++)
			for (int x = bounds.x(); x < bounds.x() + bounds.width(); x++) {
				int index = BaseSkinDyes.sourceIndex(x, y, slim);
				if (index >= 0 && canonical.get(index)) allowed.set(y * 64 + x);
			}
		// Equipped parts also explicitly own their fragment positions, including undyed parts.
		for (var occupied : slot.occupied()) {
			if (masks.regions().containsKey(occupied)) continue;
			var equipped = WardrobeResources.part(value.wardrobe().parts().get(occupied));
			if (equipped == null || equipped.slot() != occupied) continue;
			for (var fragment : equipped.fragments()) {
				if (!fragment.body().equals("any")
						&& !fragment.body().equals(slim ? "slim" : "wide")) continue;
				for (int y = Math.max(fragment.y(), bounds.y());
						y
								< Math.min(
										fragment.y() + fragment.height(),
										bounds.y() + bounds.height());
						y++)
					for (int x = Math.max(fragment.x(), bounds.x());
							x
									< Math.min(
											fragment.x() + fragment.width(),
											bounds.x() + bounds.width());
							x++)
						if (BaseSkinDyes.sourceIndex(x, y, slim) >= 0) allowed.set(y * 64 + x);
			}
		}
		// A manually assigned eye/mouth/etc. cannot be exported as a covering clothing part.
		var foreign = new BitSet(4096);
		masks.regions()
				.forEach(
						(owner, pixels) -> {
							if (!slot.occupied().contains(owner)) pixels.forEach(foreign::set);
						});
		for (int p = allowed.nextSetBit(0); p >= 0; p = allowed.nextSetBit(p + 1)) {
			int source = BaseSkinDyes.sourceIndex(p % 64, p / 64, slim);
			if (source >= 0 && foreign.get(source)) allowed.clear(p);
		}
		return allowed;
	}

	private List<WardrobeSlot> eligibleSlots() {
		if (pixelSelection == null) return List.of();
		long generation = WardrobeResources.generation();
		if (cachedSelection == pixelSelection
				&& cachedSelectionMasks == masks
				&& cachedWardrobeGeneration == generation) return cachedEligibleSlots;
		cachedEligibleSlots =
				Arrays.stream(WardrobeSlot.values())
						.filter(slot -> SelectedPart.belongsTo(pixelSelection, allowedPixels(slot)))
						.toList();
		cachedSelection = pixelSelection;
		cachedSelectionMasks = masks;
		cachedWardrobeGeneration = generation;
		return cachedEligibleSlots;
	}

	private SkinSelection captureSelection(Region bounds) {
		try (var image = CharacterSkins.beforeCustomDyes(draft())) {
			return SkinSelection.capture(
					bounds, (x, y) -> CharacterSkins.swapRedBlue(image.getPixelRGBA(x, y)));
		}
	}

	private void saveSelection() {
		if (pixelSelection == null) return;
		var slots = eligibleSlots();
		if (slots.isEmpty()) return;
		// Capture visible colors now; parent textures are released while the save dialog is open.
		var selected = SkinSelection.capture(pixelSelection.bounds(), this::displayedPixel);
		minecraft.setScreen(
				new SavePartScreen(
						this,
						slots,
						(name, slot) -> {
							WardrobeResources.saveLocal(
									SelectedPart.json(
											name, slot, selected, allowedPixels(slot), slim));
							selectionStatus = ClientProfiles.text("skin.selection.saved");
						}));
	}

	private void invalidateBaseline() {
		if (baseline != null) {
			baseline.close();
			baseline = null;
		}
		rebuildWidgets();
	}

	private void changeMasks(DyeMasks next) {
		if (next.equals(masks)) return;
		maskUndo.addLast(masks);
		if (maskUndo.size() > 32) maskUndo.removeFirst();
		maskRedo.clear();
		masks = next;
		invalidateBaseline();
	}

	private void history(boolean redo) {
		finishStroke();
		clearPixelSelection();
		if (tool != Tool.REGION) {
			if (redo) canvas.redo();
			else canvas.undo();
			refreshHistory();
			return;
		}
		var from = redo ? maskRedo : maskUndo;
		var to = redo ? maskUndo : maskRedo;
		if (!from.isEmpty()) {
			to.addLast(masks);
			masks = from.removeLast();
			invalidateBaseline();
		}
		refreshHistory();
	}

	private void clearMaskFace() {
		var selected = BaseSkinDyes.pixels(draft(), regionSlot);
		var r = region();
		for (int y = r.y(); y < r.y() + r.height(); y++)
			for (int x = r.x(); x < r.x() + r.width(); x++) {
				int p = BaseSkinDyes.sourceIndex(x, y, slim);
				if (p >= 0) selected.clear(p);
			}
		changeMasks(masks.with(regionSlot, selected.stream().boxed().toList()));
	}

	private void selectionEnd(double x, double y) {
		selectionEndX =
				Mth.clamp((int) Math.floor((x - canvasX()) / zoom()), 0, region().width() - 1);
		selectionEndY =
				Mth.clamp((int) Math.floor((y - canvasY()) / zoom()), 0, region().height() - 1);
	}

	private void commitSelection() {
		var selected = BaseSkinDyes.pixels(draft(), regionSlot);
		var r = region();
		for (int y = Math.min(selectionY, selectionEndY);
				y <= Math.max(selectionY, selectionEndY);
				y++)
			for (int x = Math.min(selectionX, selectionEndX);
					x <= Math.max(selectionX, selectionEndX);
					x++) {
				int p = BaseSkinDyes.sourceIndex(r.x() + x, r.y() + y, slim);
				if (p >= 0) selected.set(p, selectionButton == 0);
			}
		selectionButton = selectionX = -1;
		changeMasks(masks.with(regionSlot, selected.stream().boxed().toList()));
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if (modelPreview.click(x, y, button)) {
			var hit = modelPreview.takeConfirmed();
			if (hit != null) {
				finishStroke();
				clearPixelSelection();
				part = hit.part();
				face = hit.face();
				rebuildWidgets();
			}
			return true;
		}
		if ((button == 0 || button == 1) && inside(x, y)) {
			if (tool == Tool.REGION) {
				setFocused(null);
				selectionEnd(x, y);
				selectionX = selectionEndX;
				selectionY = selectionEndY;
				selectionButton = button;
				return true;
			}
			if (tool == Tool.SELECT) {
				setFocused(null);
				if (button == 1) {
					clearPixelSelection();
					return true;
				}
				selectionEnd(x, y);
				var r = region();
				movingSelection =
						pixelSelection != null
								&& !hasShiftDown()
								&& pixelSelection
										.bounds()
										.contains(r.x() + selectionEndX, r.y() + selectionEndY);
				dragStartX = selectionEndX;
				dragStartY = selectionEndY;
				dragOffsetX = dragOffsetY = 0;
				if (!movingSelection) {
					pixelSelection = null;
					selectionX = selectionEndX;
					selectionY = selectionEndY;
				}
				selectionButton = button;
				return true;
			}
			colors.blurCode();
			setFocused(null);
			canvas.beginStroke();
			strokeButton = button;
			previousX = previousY = -1;
			draw(x, y, button);
			return true;
		}
		return super.mouseClicked(x, y, button);
	}

	@Override
	public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		if (modelPreview.drag(button, dx)) return true;
		if (selectionButton == button) {
			selectionEnd(x, y);
			if (movingSelection) updateMove();
			return true;
		}
		if (strokeButton == button) {
			draw(x, y, button);
			return true;
		}
		return super.mouseDragged(x, y, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		if (modelPreview.release(button)) return true;
		if (selectionButton == button) {
			selectionEnd(x, y);
			if (tool == Tool.SELECT) {
				if (movingSelection) {
					updateMove();
					var r = pixelSelection.bounds();
					if (dragOffsetX != 0 || dragOffsetY != 0)
						transformSelection(
								pixelSelection.at(r.x() + dragOffsetX, r.y() + dragOffsetY));
				} else {
					var r = region();
					var bounds =
							new Region(
									r.x() + Math.min(selectionX, selectionEndX),
									r.y() + Math.min(selectionY, selectionEndY),
									Math.abs(selectionX - selectionEndX) + 1,
									Math.abs(selectionY - selectionEndY) + 1);
					pixelSelection = captureSelection(bounds);
				}
				selectionButton = selectionX = -1;
				movingSelection = false;
				refreshHistory();
			} else commitSelection();
			return true;
		}
		if (strokeButton == button) {
			finishStroke();
			return true;
		}
		return super.mouseReleased(x, y, button);
	}

	@Override
	public boolean keyPressed(int key, int scan, int modifiers) {
		if ((colors == null || !colors.editingCode()) && hasControlDown()) {
			if (key == GLFW.GLFW_KEY_Z) {
				history(hasShiftDown());
				return true;
			}
			if (key == GLFW.GLFW_KEY_Y) {
				history(true);
				return true;
			}
		}
		if (tool == Tool.SELECT && pixelSelection != null) {
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				clearPixelSelection();
				return true;
			}
			if (key == GLFW.GLFW_KEY_R) {
				rotateSelection(hasShiftDown() ? -1 : 1);
				return true;
			}
			int dx = key == GLFW.GLFW_KEY_LEFT ? -1 : key == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
			int dy = key == GLFW.GLFW_KEY_UP ? -1 : key == GLFW.GLFW_KEY_DOWN ? 1 : 0;
			if (dx != 0 || dy != 0) {
				var r = pixelSelection.bounds();
				if (region().contains(r.x() + dx, r.y() + dy)
						&& region().contains(
										r.x() + dx + r.width() - 1, r.y() + dy + r.height() - 1))
					transformSelection(pixelSelection.at(r.x() + dx, r.y() + dy));
				return true;
			}
		}
		return super.keyPressed(key, scan, modifiers);
	}

	private int displayedPixel(int x, int y) {
		int edit = canvas.pixel(x, y);
		return edit == SkinPaint.TRANSPARENT
				? 0
				: edit != 0
						? BaseSkinDyes.dyePaintPixel(appearance.withDyeMasks(masks), x, y, edit)
						: CharacterSkins.swapRedBlue(baseline.getPixelRGBA(x, y));
	}

	private int canvasPixel(int x, int y) {
		if (layerView != LayerView.ALL) {
			var r = region();
			return displayedPixel(r.x() + x, r.y() + y);
		}
		var base = SkinLayout.region(part, face, Layer.BASE, slim);
		var outer = SkinLayout.region(part, face, Layer.OUTER, slim);
		return SkinPixelBlend.compose(
				displayedPixel(base.x() + x, base.y() + y),
				displayedPixel(outer.x() + x, outer.y() + y),
				false,
				true);
	}

	private int movingBackgroundPixel(int x, int y) {
		var r = region();
		if (!movingSelection || !pixelSelection.bounds().contains(r.x() + x, r.y() + y))
			return canvasPixel(x, y);
		int cleared =
				layer == Layer.OUTER
						? 0
						: CharacterSkins.swapRedBlue(baseline.getPixelRGBA(r.x() + x, r.y() + y));
		if (layerView != LayerView.ALL) return cleared;
		var base = SkinLayout.region(part, face, Layer.BASE, slim);
		var outer = SkinLayout.region(part, face, Layer.OUTER, slim);
		return SkinPixelBlend.compose(
				layer == Layer.BASE ? cleared : displayedPixel(base.x() + x, base.y() + y),
				layer == Layer.OUTER ? cleared : displayedPixel(outer.x() + x, outer.y() + y),
				false,
				true);
	}

	private int movingForegroundPixel(int x, int y, int value) {
		int argb = BaseSkinDyes.dyePaintPixel(draft(), x, y, value);
		if (layerView != LayerView.ALL) return argb;
		var r = region();
		var base = SkinLayout.region(part, face, Layer.BASE, slim);
		var outer = SkinLayout.region(part, face, Layer.OUTER, slim);
		return SkinPixelBlend.compose(
				layer == Layer.BASE
						? argb
						: displayedPixel(base.x() + x - r.x(), base.y() + y - r.y()),
				layer == Layer.OUTER
						? argb
						: displayedPixel(outer.x() + x - r.x(), outer.y() + y - r.y()),
				false,
				true);
	}

	private int paletteX(int column) {
		var l = layout();
		return l.left() + l.preview() + 8 + (l.previewWidth() - 12) * column / 4;
	}

	private int paletteWidth(int column) {
		int span = layout().previewWidth() - 12;
		return span * (column + 1) / 4 - span * column / 4 - 4;
	}

	private void fittedHint(GuiGraphics g, Component text, int x, int y, int width) {
		EditorUi.text(g, text, x, y, width, 10, EditorUi.MUTED, true);
	}

	@Override
	public void renderBackground(GuiGraphics g, int x, int y, float delta) {
		super.renderBackground(g, x, y, delta);
		var l = layout();
		EditorUi.panel(g, l.left(), 32, l.canvasWidth(), height - 68);
		EditorUi.panel(g, l.left() + l.controls(), 32, l.controlWidth(), height - 68);
		EditorUi.panel(g, l.left() + l.preview(), 32, l.previewWidth(), height - 68);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		refreshHistory();
		super.render(g, mouseX, mouseY, delta);
		var layout = layout();
		int left = layout.left(), controls = left + layout.controls();
		EditorUi.text(
				g,
				ClientProfiles.text(
						tool == Tool.REGION
								? "skin.region.slot"
								: tool == Tool.SELECT ? "skin.tool.select" : "skin.color"),
				controls + 8,
				84,
				layout.controlWidth() - 16,
				12,
				EditorUi.GOLD,
				false);
		for (int i = 0; tool != Tool.REGION && tool != Tool.SELECT && i < palette.length; i++) {
			int px = paletteX(i % 4) + 3, py = 67 + (i / 4) * 16;
			g.fill(px, py, px + paletteWidth(i % 4) - 6, py + 6, 0xFF000000 | palette[i]);
		}
		if (tool == Tool.SELECT && height >= 260) {
			EditorUi.wrapped(
					g,
					ClientProfiles.text("skin.selection.help"),
					controls + 8,
					168,
					layout.controlWidth() - 16,
					Math.max(0, height - 268),
					EditorUi.MUTED,
					false);
			EditorUi.text(
					g,
					pixelSelection != null && eligibleSlots().isEmpty()
							? ClientProfiles.text("skin.selection.no_slot")
							: selectionStatus,
					controls + 8,
					Math.max(168, height - 92),
					layout.controlWidth() - 16,
					Math.min(24, height - 68 - Math.max(168, height - 92)),
					EditorUi.GOLD,
					true);
		}
		var layerHelp =
				layerView == LayerView.ALL
						? ClientProfiles.text("skin.all.help", label("layer", layer))
						: ClientProfiles.text(
								layer == Layer.BASE ? "skin.base.help" : "skin.outer.help");
		fittedHint(g, layerHelp, left + 8, 62, layout.canvasWidth() - 16);
		var r = region();
		int zoom = zoom(), cx = canvasX(), cy = canvasY();
		var selectedMask = tool == Tool.REGION ? BaseSkinDyes.pixels(draft(), regionSlot) : null;
		for (int y = 0; y < r.height(); y++)
			for (int x = 0; x < r.width(); x++) {
				int px = cx + x * zoom, py = cy + y * zoom, argb = movingBackgroundPixel(x, y);
				g.fill(px, py, px + zoom, py + zoom, ((x + y) % 2 == 0) ? 0xFF343434 : 0xFF555555);
				g.fill(px, py, px + zoom, py + zoom, argb);
				if (selectedMask != null) {
					int p = BaseSkinDyes.sourceIndex(r.x() + x, r.y() + y, slim);
					if (p >= 0 && selectedMask.get(p)) {
						g.fill(px, py, px + zoom, py + zoom, 0x7048DEB0);
						g.renderOutline(px, py, zoom, zoom, EditorUi.GOLD);
					}
				}
			}
		for (int x = 0; x <= r.width(); x++)
			g.fill(cx + x * zoom, cy, cx + x * zoom + 1, cy + r.height() * zoom, 0x40303030);
		for (int y = 0; y <= r.height(); y++)
			g.fill(cx, cy + y * zoom, cx + r.width() * zoom, cy + y * zoom + 1, 0x40303030);
		if (selectionButton >= 0 && !movingSelection)
			g.renderOutline(
					cx + Math.min(selectionX, selectionEndX) * zoom,
					cy + Math.min(selectionY, selectionEndY) * zoom,
					(Math.abs(selectionX - selectionEndX) + 1) * zoom,
					(Math.abs(selectionY - selectionEndY) + 1) * zoom,
					selectionButton == 0 ? EditorUi.GOLD : 0xFFFF7777);
		if (pixelSelection != null) {
			var selected = pixelSelection.bounds();
			int ox = movingSelection ? dragOffsetX : 0, oy = movingSelection ? dragOffsetY : 0;
			if (movingSelection) {
				for (int y = 0; y < selected.height(); y++)
					for (int x = 0; x < selected.width(); x++) {
						int px = cx + (selected.x() - r.x() + x + ox) * zoom;
						int py = cy + (selected.y() - r.y() + y + oy) * zoom;
						g.fill(
								px,
								py,
								px + zoom,
								py + zoom,
								((x + y) % 2 == 0) ? 0xFF343434 : 0xFF555555);
						g.fill(
								px,
								py,
								px + zoom,
								py + zoom,
								movingForegroundPixel(
										selected.x() + x + ox,
										selected.y() + y + oy,
										pixelSelection.pixel(x, y)));
					}
			}
			g.renderOutline(
					cx + (selected.x() - r.x() + ox) * zoom,
					cy + (selected.y() - r.y() + oy) * zoom,
					selected.width() * zoom,
					selected.height() * zoom,
					EditorUi.GOLD);
		}
		if (inside(mouseX, mouseY) && tool != Tool.SELECT) {
			int x = (mouseX - cx) / zoom, y = (mouseY - cy) / zoom;
			int sx = Math.max(0, x - brushSize / 2), sy = Math.max(0, y - brushSize / 2);
			int ex = Math.min(r.width(), x - brushSize / 2 + brushSize),
					ey = Math.min(r.height(), y - brushSize / 2 + brushSize);
			g.renderOutline(
					cx + sx * zoom, cy + sy * zoom, (ex - sx) * zoom, (ey - sy) * zoom, 0xFFFFFFFF);
		}
		int modelBottom = height - 74,
				modelTop = tool == Tool.REGION || tool == Tool.SELECT ? 64 : 116,
				scale =
						Math.max(
								1,
								Math.min(
										54,
										Math.min(
												(modelBottom - modelTop) / 2,
												(layout.previewWidth() - 20) * 10 / 13)));
		modelPreview.bounds(
				left + layout.preview() + layout.previewWidth() / 2, modelBottom, scale);
		modelPreview.interactionArea(
				left + layout.preview() + 8,
				modelTop,
				layout.previewWidth() - 16,
				Math.max(1, modelBottom - modelTop));
		g.enableScissor(
				left + layout.preview() + 8,
				modelTop,
				left + layout.preview() + layout.previewWidth() - 8,
				modelBottom);
		try {
			modelPreview.render(
					g,
					draft(),
					mouseX,
					showOuter || layerView != LayerView.BASE,
					tool == Tool.REGION ? regionSlot : null,
					tool == Tool.REGION || layerView == LayerView.ALL ? null : part,
					tool == Tool.REGION || layerView == LayerView.ALL ? null : layer);
		} finally {
			g.disableScissor();
		}
		fittedHint(
				g,
				ClientProfiles.text(
						tool == Tool.REGION
								? "skin.region.help.short"
								: tool == Tool.SELECT
										? "skin.selection.help.short"
										: "skin.help.short"),
				left + 8,
				height - 52,
				layout.canvasWidth() - 16);
	}

	@Override
	public void removed() {
		finishStroke();
		modelPreview.close();
		if (baseline != null) {
			baseline.close();
			baseline = null;
		}
		super.removed();
	}
}
