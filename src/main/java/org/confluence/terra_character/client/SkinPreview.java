package org.confluence.terra_character.client;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;

import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.service.SkinLayout;
import org.confluence.terra_character.service.SkinLayout.*;

import java.util.Arrays;

/** Shared interactive skin preview. Its animated alpha never enters saved skin pixels. */
final class SkinPreview implements AutoCloseable {
	record Hit(Part part, Face face, int u, int v) {}

	private CharacterAppearance appearance;
	private NativeImage source;
	private DynamicTexture texture;
	private PlayerSkin skin;
	private int center, bottom, scale;
	private float yaw = 180, target = 180;
	private boolean manual, dragging;
	private long lastFrame;
	private final DoubleClickSelection<Part> clicks = new DoubleClickSelection<>();
	private Hit confirmed;
	private final boolean[] mask = new boolean[4096];
	private final boolean[] baseMask = new boolean[4096];
	private WardrobeSlot maskSlot;
	private Part maskPart;
	private Layer maskLayer;
	private boolean maskDirty = true;
	private boolean maskAll;
	private int[] interactionArea;

	record Focus(float width, float height, float centerY) {}

	/** Model-space vertical extent from the same UV masks used for category highlighting. */
	static Focus focus(CharacterAppearance value, WardrobeSlot slot) {
		if (slot == null) return new Focus(16, 32, 8);
		boolean slim = value.bodyStyle().endsWith(":slim");
		float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY, radius = 0;
		for (var part : Part.values())
			for (var layer : Layer.values())
				for (var face : Face.values()) {
					var region = SkinLayout.region(part, face, layer, slim);
					float top =
							part == Part.HEAD
									? -8
									: part == Part.LEFT_LEG || part == Part.RIGHT_LEG ? 12 : 0;
					float length = part == Part.HEAD ? 8 : 12;
					for (int v = 0; v < region.height(); v++)
						for (int u = 0; u < region.width(); u++)
							if (marked(
									value,
									slim,
									region.x() + u,
									region.y() + v,
									slot,
									null,
									null)) {
								float low =
										face == Face.TOP
												? top
												: face == Face.BOTTOM ? top + length - 1 : top + v;
								min = Math.min(min, low);
								max = Math.max(max, low + 1);
								radius =
										Math.max(
												radius,
												part == Part.LEFT_ARM || part == Part.RIGHT_ARM
														? 8
														: part == Part.HEAD ? 6 : 4.5f);
							}
				}
		return Float.isFinite(min)
				? new Focus(radius * 2, Math.max(4, max - min), (min + max) / 2)
				: new Focus(16, 32, 8);
	}

	void interactionArea(int x, int y, int width, int height) {
		interactionArea = new int[] {x, y, width, height};
	}

	void bounds(int center, int bottom, int scale) {
		this.center = center;
		this.bottom = bottom;
		this.scale = scale;
	}

	void followMouse() {
		manual = false;
		dragging = false;
		clicks.reset();
	}

	boolean inside(double x, double y) {
		if (interactionArea != null)
			return x >= interactionArea[0]
					&& y >= interactionArea[1]
					&& x < interactionArea[0] + interactionArea[2]
					&& y < interactionArea[1] + interactionArea[3];
		return x >= center - scale * .65
				&& x <= center + scale * .65
				&& y >= bottom - scale * 2.05
				&& y <= bottom;
	}

	boolean click(double x, double y, int button) {
		if (!inside(x, y) || (button != 0 && button != 1)) return false;
		if (button == 1) {
			manual = true;
			dragging = true;
			target = yaw;
			clicks.reset();
		} else {
			Hit hit = hit(x, y);
			if (hit != null) {
				if (clicks.click(hit.part(), Util.getMillis())) confirmed = hit;
			} else clicks.reset();
			manual = false;
			dragging = false;
		}
		return true;
	}

	Hit takeConfirmed() {
		Hit result = confirmed;
		confirmed = null;
		return result;
	}

	// CharacterSkins renders a raw PlayerModel with negative Z scale: rightward drag decreases yaw.
	// CharacterPortraits instead uses the vanilla entity renderer and increments its portrait yaw.
	boolean drag(int button, double dx) {
		if (button != 1 || !dragging) return false;
		target -= (float) dx;
		return true;
	}

	boolean release(int button) {
		if (button != 1 || !dragging) return false;
		dragging = false;
		return true;
	}

	void render(
			GuiGraphics g,
			CharacterAppearance value,
			int mouseX,
			boolean outer,
			WardrobeSlot slot,
			Part selected,
			Layer selectedLayer) {
		render(g, value, mouseX, outer, slot, selected, selectedLayer, false);
	}

	void render(
			GuiGraphics g,
			CharacterAppearance value,
			int mouseX,
			boolean outer,
			WardrobeSlot slot,
			Part selected,
			Layer selectedLayer,
			boolean all) {
		if (!value.equals(appearance) || texture == null) {
			releaseTexture();
			appearance = value;
			maskDirty = true;
			source = CharacterSkins.image(value);
			texture = new DynamicTexture(new NativeImage(64, 64, true));
			var location =
					Minecraft.getInstance()
							.getTextureManager()
							.register("terra_character/interactive_preview", texture);
			skin =
					new PlayerSkin(
							location,
							null,
							null,
							null,
							value.bodyStyle().endsWith(":slim")
									? PlayerSkin.Model.SLIM
									: PlayerSkin.Model.WIDE,
							false);
			for (int py = 0; py < 64; py++)
				for (int px = 0; px < 64; px++)
					baseMask[py * 64 + px] =
							SkinLayout.contains(
									Layer.BASE, value.bodyStyle().endsWith(":slim"), px, py);
		}
		long now = Util.getMillis();
		float elapsed = lastFrame == 0 ? 0 : Math.min(.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		if (!manual) target = 180 - Mth.clamp((mouseX - center) * .25f, -35, 35);
		yaw += Mth.wrapDegrees(target - yaw) * (1 - (float) Math.exp(-elapsed * 16));
		float alpha = .6f + .4f * (float) Math.sin(now * Math.PI / 800);
		boolean slim = value.bodyStyle().endsWith(":slim");
		if (maskDirty
				|| slot != maskSlot
				|| selected != maskPart
				|| selectedLayer != maskLayer
				|| all != maskAll) {
			for (int y = 0; y < 64; y++)
				for (int x = 0; x < 64; x++)
					mask[y * 64 + x] =
							all || marked(value, slim, x, y, slot, selected, selectedLayer);
			maskSlot = slot;
			maskPart = selected;
			maskLayer = selectedLayer;
			maskAll = all;
			maskDirty = false;
		}
		var pixels = texture.getPixels();
		for (int y = 0; y < 64; y++)
			for (int x = 0; x < 64; x++) {
				int rgba = source.getPixelRGBA(x, y);
				if (selectedLayer != null
						&& ((selectedLayer == Layer.OUTER && baseMask[y * 64 + x])
								|| (selectedLayer == Layer.BASE && !baseMask[y * 64 + x]))) {
					int gray = ((rgba & 255) + (rgba >> 8 & 255) + (rgba >> 16 & 255)) / 3;
					rgba =
							gray
									| (gray << 8)
									| (gray << 16)
									| (Math.round((rgba >>> 24) * .08f) << 24);
				}
				if (mask[y * 64 + x])
					rgba = (rgba & 0xFFFFFF) | Math.round((rgba >>> 24) * alpha) << 24;
				pixels.setPixelRGBA(x, y, rgba);
			}
		texture.upload();
		CharacterSkins.preview(g, skin, center, bottom, scale, yaw, outer);
	}

	private static boolean marked(
			CharacterAppearance value,
			boolean slim,
			int x,
			int y,
			WardrobeSlot slot,
			Part selected,
			Layer selectedLayer) {
		if (selected != null)
			return SkinLayout.region(selected, Face.FRONT, selectedLayer, slim).contains(x, y)
					|| Arrays.stream(Face.values())
							.anyMatch(
									face ->
											SkinLayout.region(selected, face, selectedLayer, slim)
													.contains(x, y));
		if (slot == null) return false;
		if (value.dyeMasks().regions().containsKey(slot))
			return Boolean.TRUE.equals(BaseSkinDyes.marked(value, slot, x, y));
		String equippedId = value.wardrobe().parts().get(slot);
		var equipped = equippedId == null ? null : WardrobeResources.part(equippedId);
		if (equipped != null)
			for (var fragment : equipped.fragments()) {
				if (!fragment.body().equals("any")
						&& !fragment.body().equals(slim ? "slim" : "wide")) continue;
				if (x >= fragment.x()
						&& x < fragment.x() + fragment.width()
						&& y >= fragment.y()
						&& y < fragment.y() + fragment.height()) return true;
			}
		if (equipped != null) return false;
		Boolean dyed = BaseSkinDyes.marked(value, slot, x, y);
		if (dyed != null) return dyed;
		// Useful category highlighting even before a resource pack supplies a part.
		for (var layer : Layer.values())
			for (var part : Part.values())
				for (var face : Face.values()) {
					var region = SkinLayout.region(part, face, layer, slim);
					if (!region.contains(x, y)) continue;
					int row = y - region.y();
					boolean leg = part == Part.LEFT_LEG || part == Part.RIGHT_LEG,
							arm = part == Part.LEFT_ARM || part == Part.RIGHT_ARM;
					return switch (slot) {
						case SKIN -> true;
						case HAIR -> part == Part.HEAD && (face != Face.FRONT || row < 3);
						case EYES ->
								part == Part.HEAD && face == Face.FRONT && (row == 4 || row == 5);
						case MOUTH -> part == Part.HEAD && face == Face.FRONT && row >= 6;
						case SKIN_DETAIL -> part == Part.HEAD && face == Face.FRONT && row >= 3;
						case GLOVES -> arm && row >= 8;
						case SHOES -> leg && row >= 9;
						case SOCKS -> leg && row >= 6;
						case PANTS -> leg;
						case UNDERWEAR -> part == Part.BODY;
						case OUTERWEAR -> part == Part.BODY || arm;
						case OVERALLS -> part == Part.BODY || leg;
						case OUTFIT -> part != Part.HEAD;
					};
				}
		return false;
	}

	/** Orthographic ray against the six vanilla skin cuboids, at the current animated angle. */
	private Hit hit(double mouseX, double mouseY) {
		if (scale <= 0) return null;
		double sx = (mouseX - center) * 16 / scale,
				sy = (mouseY - (bottom - scale * 1.5)) * 16 / scale;
		double angle = Math.toRadians(yaw - 180), c = Math.cos(angle), s = Math.sin(angle);
		// Screen depth is reversed by the preview's negative Z scale.
		double ox = c * sx + s * 100, oz = s * sx - c * 100, dx = -s, dz = c;
		boolean slim = appearance != null && appearance.bodyStyle().endsWith(":slim");
		double arm = slim ? 3 : 4;
		double[][] boxes = {
			{-4, -8, -4, 4, 0, 4},
			{-4, 0, -2, 4, 12, 2},
			{-4 - arm, 0, -2, -4, 12, 2},
			{4, 0, -2, 4 + arm, 12, 2},
			{-4, 12, -2, 0, 24, 2},
			{0, 12, -2, 4, 24, 2}
		};
		Hit best = null;
		double nearest = Double.POSITIVE_INFINITY;
		for (int i = 0; i < boxes.length; i++) {
			var b = boxes[i];
			if (sy < b[1] || sy > b[4]) continue;
			double near = 0, far = Double.POSITIVE_INFINITY;
			Face face = Face.FRONT;
			double[] origins = {ox, oz}, directions = {dx, dz};
			for (int axis = 0; axis < 2; axis++) {
				int k = axis == 0 ? 0 : 2;
				double origin = origins[axis], direction = directions[axis];
				if (Math.abs(direction) < 1e-7) {
					if (origin < b[k] || origin > b[k + 3]) {
						far = -1;
						break;
					}
					continue;
				}
				double a = (b[k] - origin) / direction, z = (b[k + 3] - origin) / direction;
				Face side =
						axis == 0
								? (direction > 0 ? Face.RIGHT : Face.LEFT)
								: (direction > 0 ? Face.FRONT : Face.BACK);
				if (a > z) {
					double swap = a;
					a = z;
					z = swap;
				}
				if (a > near) {
					near = a;
					face = side;
				}
				far = Math.min(far, z);
			}
			if (near > far || near >= nearest) continue;
			double hx = ox + near * dx, hz = oz + near * dz;
			int u =
					(int)
							(face == Face.FRONT
									? hx - b[0]
									: face == Face.BACK
											? b[3] - hx
											: face == Face.RIGHT ? b[5] - hz : hz - b[2]);
			best = new Hit(Part.values()[i], face, u, (int) (sy - b[1]));
			nearest = near;
		}
		return best;
	}

	static WardrobeSlot slot(Hit hit) {
		return switch (hit.part()) {
			case HEAD ->
					hit.face() == Face.FRONT
							? (hit.v() < 3
									? WardrobeSlot.HAIR
									: hit.v() < 6 ? WardrobeSlot.EYES : WardrobeSlot.MOUTH)
							: WardrobeSlot.HAIR;
			case BODY -> WardrobeSlot.OUTERWEAR;
			case LEFT_ARM, RIGHT_ARM -> hit.v() >= 8 ? WardrobeSlot.GLOVES : WardrobeSlot.OUTERWEAR;
			case LEFT_LEG, RIGHT_LEG ->
					hit.v() >= 9
							? WardrobeSlot.SHOES
							: hit.v() >= 6 ? WardrobeSlot.SOCKS : WardrobeSlot.PANTS;
		};
	}

	private void releaseTexture() {
		if (skin != null) Minecraft.getInstance().getTextureManager().release(skin.texture());
		if (source != null) source.close();
		skin = null;
		source = null;
		texture = null;
	}

	@Override
	public void close() {
		releaseTexture();
		lastFrame = 0;
		dragging = false;
	}
}
