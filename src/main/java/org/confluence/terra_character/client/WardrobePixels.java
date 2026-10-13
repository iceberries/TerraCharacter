package org.confluence.terra_character.client;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.service.SkinLayout;
import org.confluence.terra_character.service.SkinPixelBlend;
import org.confluence.terra_character.service.WardrobeDefinitions.Fragment;

import java.io.IOException;

/** Flattens pixel fragments into the existing vanilla base/outer UVs. No extra model layers. */
final class WardrobePixels {
	private WardrobePixels() {}

	static void apply(NativeImage image, CharacterAppearance appearance) {
		boolean slim = appearance.bodyStyle().endsWith(":slim");
		for (var slot : WardrobeSlot.values()) {
			String id = appearance.wardrobe().parts().get(slot);
			if (id == null) continue;
			var part = WardrobeResources.part(id);
			if (part == null || part.slot() != slot) continue;
			for (var fragment : part.fragments()) {
				if (!fragment.body().equals("any")
						&& !fragment.body().equals(slim ? "slim" : "wide")) continue;
				try {
					if (fragment.texture() == null) stamp(image, null, fragment, appearance, slim);
					else
						try (var stream =
										Minecraft.getInstance()
												.getResourceManager()
												.open(ResourceLocation.parse(fragment.texture()));
								var source = NativeImage.read(stream)) {
							if (source.getWidth() != 64 || source.getHeight() != 64)
								throw new IOException(
										"Wardrobe texture must use the 64x64 skin atlas");
							stamp(image, source, fragment, appearance, slim);
						}
				} catch (Exception ex) {
					WardrobeResources.warn(id + "/" + fragment.texture(), ex);
				}
			}
		}
	}

	private static void stamp(
			NativeImage target,
			NativeImage texture,
			Fragment f,
			CharacterAppearance appearance,
			boolean slim) {
		int tint =
				f.tint() == null ? 0xFFFFFF : appearance.colors().getOrDefault(f.tint(), 0xFFFFFF);
		for (int dy = 0; dy < f.height(); dy++)
			for (int dx = 0; dx < f.width(); dx++) {
				int x = f.x() + dx, y = f.y() + dy;
				boolean base = SkinLayout.contains(SkinLayout.Layer.BASE, slim, x, y);
				if (!base && !SkinLayout.contains(SkinLayout.Layer.OUTER, slim, x, y)) continue;
				int argb =
						texture == null
								? f.pixels().get(dy * f.width() + dx)
								: CharacterSkins.swapRedBlue(texture.getPixelRGBA(x, y));
				argb = SkinPixelBlend.tint(argb, tint);
				int destination = CharacterSkins.swapRedBlue(target.getPixelRGBA(x, y));
				int result = SkinPixelBlend.compose(destination, argb, f.replace(), base);
				target.setPixelRGBA(x, y, CharacterSkins.swapRedBlue(result));
			}
	}
}
