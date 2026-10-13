package org.confluence.terra_character.client;

import com.mojang.blaze3d.platform.*;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import org.confluence.terra_character.api.model.CharacterAppearance;
import org.confluence.terra_character.api.model.SkinPaint;
import org.confluence.terra_character.service.SkinLayout;
import org.joml.Quaternionf;

import java.io.IOException;
import java.util.*;

/** Saved account skin pixels, legacy bundled skins and independent local edits. */
public final class CharacterSkins {
	private static final Map<CharacterAppearance, PlayerSkin> CACHE = new LinkedHashMap<>();
	private static PlayerModel<?> wide, slim;

	private CharacterSkins() {}

	public static void clearCache() {
		var manager = Minecraft.getInstance().getTextureManager();
		CACHE.values().forEach(skin -> manager.release(skin.texture()));
		CACHE.clear();
	}

	public static PlayerSkin skin(CharacterAppearance appearance) {
		var found = CACHE.get(appearance);
		if (found != null) return found;
		var image = image(appearance);
		var manager = Minecraft.getInstance().getTextureManager();
		var location = manager.register("terra_character/skin", new DynamicTexture(image));
		var result =
				new PlayerSkin(
						location,
						null,
						null,
						null,
						appearance.bodyStyle().endsWith(":slim")
								? PlayerSkin.Model.SLIM
								: PlayerSkin.Model.WIDE,
						false);
		CACHE.put(appearance, result);
		if (CACHE.size() > 128) {
			var oldest = CACHE.entrySet().iterator();
			var previous = oldest.next();
			manager.release(previous.getValue().texture());
			oldest.remove();
		}
		return result;
	}

	public static final ResourceLocation DEFAULT_TEXTURE =
			ResourceLocation.parse("terra_character:textures/entity/fire_berry.png");

	public static boolean bundled(CharacterAppearance appearance) {
		return appearance.baseSkin().isEmpty()
				&& appearance.hairStyle().equals("terra_character:fire_berry");
	}

	public static boolean textured(CharacterAppearance appearance) {
		return bundled(appearance) || !appearance.baseSkin().isEmpty();
	}

	private static NativeImage accountImage(CharacterAppearance appearance, boolean slim) {
		var source = appearance.baseSkin();
		var target = new NativeImage(64, 64, true);
		for (var layer : SkinLayout.Layer.values())
			for (var part : SkinLayout.Part.values())
				for (var face : SkinLayout.Face.values()) {
					var from = SkinLayout.region(part, face, layer, source.slim());
					var to = SkinLayout.region(part, face, layer, slim);
					for (int y = 0; y < to.height(); y++)
						for (int x = 0; x < to.width(); x++)
							target.setPixelRGBA(
									to.x() + x,
									to.y() + y,
									swapRedBlue(
											source.pixel(
													from.x() + x * from.width() / to.width(),
													from.y() + y)));
				}
		return target;
	}

	private static NativeImage bundledImage(boolean slim) {
		var target = new NativeImage(64, 64, true);
		try (var stream = Minecraft.getInstance().getResourceManager().open(DEFAULT_TEXTURE);
				var source = NativeImage.read(stream)) {
			if (source.getWidth() != 64 || source.getHeight() != 64)
				throw new IOException("Default skin must be 64x64");
			for (var layer : SkinLayout.Layer.values())
				for (var part : SkinLayout.Part.values())
					for (var face : SkinLayout.Face.values()) {
						var from = SkinLayout.region(part, face, layer, true);
						var to = SkinLayout.region(part, face, layer, slim);
						for (int y = 0; y < to.height(); y++)
							for (int x = 0; x < to.width(); x++)
								target.setPixelRGBA(
										to.x() + x,
										to.y() + y,
										source.getPixelRGBA(
												from.x() + x * from.width() / to.width(),
												from.y() + y));
					}
			return target;
		} catch (IOException ex) {
			target.close();
			throw new IllegalStateException("Cannot load bundled fire_berry skin", ex);
		}
	}

	/** Caller owns the image. Preserve the bundled outer layer; mask unused UVs. */
	public static NativeImage image(CharacterAppearance appearance) {
		boolean slim = appearance.bodyStyle().endsWith(":slim");
		var image =
				!appearance.baseSkin().isEmpty()
						? accountImage(appearance, slim)
						: bundled(appearance) ? bundledImage(slim) : new NativeImage(64, 64, true);
		if (!textured(appearance)) {
			int skin = color(appearance, "skin", 0xDFAF88),
					hair = color(appearance, "hair", 0x553322),
					shirt = color(appearance, "shirt", 0x2B9D94),
					pants = color(appearance, "pants", 0x354968),
					shoes = color(appearance, "shoes", 0x493A33);
			rect(image, 0, 0, 32, 16, skin);
			rect(image, 8, 0, 16, 8, hair);
			rect(image, 0, 8, 32, 3, hair);
			rect(image, 24, 8, 8, 8, hair);
			String style = appearance.hairStyle();
			if (style.endsWith(":long")) {
				rect(image, 0, 8, 8, 8, hair);
				rect(image, 16, 8, 8, 8, hair);
				rect(image, 8, 8, 2, 5, hair);
			} else if (style.endsWith(":crest")) {
				rect(image, 8, 8, 8, 1, skin);
				rect(image, 11, 8, 2, 4, hair);
			}
			rect(image, 10, 11, 1, 1, 0xFFFFFF);
			rect(image, 13, 11, 1, 1, 0xFFFFFF);
			rect(image, 10, 12, 1, 1, color(appearance, "eyes", 0x3377AA));
			rect(image, 13, 12, 1, 1, color(appearance, "eyes", 0x3377AA));
			rect(image, 11, 14, 2, 1, 0xA76F5A);
			rect(image, 16, 16, 24, 16, shirt);
			rect(image, 40, 16, 16, 16, skin);
			rect(image, 40, 16, 16, 8, shirt);
			rect(image, 32, 48, 16, 16, skin);
			rect(image, 32, 48, 16, 8, shirt);
			rect(image, 0, 16, 16, 16, pants);
			rect(image, 0, 28, 16, 4, shoes);
			rect(image, 16, 48, 16, 16, pants);
			rect(image, 16, 60, 16, 4, shoes);
		}
		if (textured(appearance)) BaseSkinDyes.apply(image, appearance);
		WardrobePixels.apply(image, appearance);
		for (int y = 0; y < 64; y++)
			for (int x = 0; x < 64; x++) {
				boolean base = SkinLayout.contains(SkinLayout.Layer.BASE, slim, x, y);
				boolean outer = SkinLayout.contains(SkinLayout.Layer.OUTER, slim, x, y);
				if (!base && !outer) image.setPixelRGBA(x, y, 0);
				if (base) image.setPixelRGBA(x, y, image.getPixelRGBA(x, y) | 0xFF000000);
				int paint = appearance.paint().pixel(x, y);
				if (outer && paint == SkinPaint.TRANSPARENT) image.setPixelRGBA(x, y, 0);
				else if ((base || outer) && paint != 0 && paint != SkinPaint.TRANSPARENT)
					image.setPixelRGBA(x, y, swapRedBlue(paint));
			}
		BaseSkinDyes.applyCustom(image, appearance);
		return image;
	}

	public static int swapRedBlue(int argb) {
		return (argb & 0xFF00FF00) | ((argb & 255) << 16) | ((argb >>> 16) & 255);
	}

	private static int color(CharacterAppearance a, String key, int fallback) {
		return a.colors().getOrDefault("terra_character:" + key, fallback);
	}

	private static void rect(NativeImage image, int x, int y, int w, int h, int rgb) {
		int rgba = 0xFF000000 | ((rgb & 255) << 16) | (rgb & 0xFF00) | ((rgb >>> 16) & 255);
		for (int py = y; py < y + h; py++)
			for (int px = x; px < x + w; px++) image.setPixelRGBA(px, py, rgba);
	}

	public static void preview(
			GuiGraphics graphics,
			CharacterAppearance appearance,
			int x,
			int bottom,
			int scale,
			float rotation) {
		preview(graphics, skin(appearance), x, bottom, scale, rotation, true);
	}

	public static void preview(
			GuiGraphics graphics,
			PlayerSkin texture,
			int x,
			int bottom,
			int scale,
			float rotation,
			boolean outer) {
		var minecraft = Minecraft.getInstance();
		if (wide == null) {
			wide =
					new PlayerModel<>(
							minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
			slim =
					new PlayerModel<>(
							minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
		}
		var model = texture.model() == PlayerSkin.Model.SLIM ? slim : wide;
		model.young = false; // EntityModel defaults to young; otherwise hat and head use different
		// scales.
		model.setAllVisible(true);
		model.hat.visible = outer;
		model.jacket.visible = outer;
		model.leftSleeve.visible = outer;
		model.rightSleeve.visible = outer;
		model.leftPants.visible = outer;
		model.rightPants.visible = outer;
		// GUI model coordinates follow inventory rendering: invert depth, retain the player's
		// left/right.
		var pose = graphics.pose();
		pose.pushPose();
		pose.translate(x, bottom - scale * 1.5, 150);
		pose.scale(scale, scale, -scale);
		pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(rotation - 180)));
		Lighting.setupForEntityInInventory();
		RenderSystem.enableDepthTest();
		model.renderToBuffer(
				pose,
				graphics.bufferSource().getBuffer(RenderType.entityTranslucent(texture.texture())),
				15728880,
				OverlayTexture.NO_OVERLAY,
				0xFFFFFFFF);
		graphics.flush();
		pose.popPose();
		Lighting.setupFor3DItems();
	}
}
