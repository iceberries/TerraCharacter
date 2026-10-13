package org.confluence.terra_character.service;

import org.confluence.terra_character.api.model.SkinBase;

import java.util.Arrays;

/** Mirrors vanilla's legacy limb conversion and preserves modern outer-layer alpha. */
public final class SkinBasePixels {
	private SkinBasePixels() {}

	public static SkinBase normalize(int[] source, int width, int height, boolean slim) {
		if (width != 64 || (height != 32 && height != 64) || source.length != width * height)
			throw new IllegalArgumentException("Skin must be 64x32 or 64x64");
		int[] pixels = Arrays.copyOf(source, 4096);
		if (height == 32) {
			slim = false;
			int[][] copies = {
				{4, 16, 16, 32, 4, 4},
				{8, 16, 16, 32, 4, 4},
				{0, 20, 24, 32, 4, 12},
				{4, 20, 16, 32, 4, 12},
				{8, 20, 8, 32, 4, 12},
				{12, 20, 16, 32, 4, 12},
				{44, 16, -8, 32, 4, 4},
				{48, 16, -8, 32, 4, 4},
				{40, 20, 0, 32, 4, 12},
				{44, 20, -8, 32, 4, 12},
				{48, 20, -16, 32, 4, 12},
				{52, 20, -8, 32, 4, 12}
			};
			for (var c : copies)
				for (int y = 0; y < c[5]; y++)
					for (int x = 0; x < c[4]; x++)
						pixels[(c[1] + c[3] + y) * 64 + c[0] + c[2] + x] =
								pixels[(c[1] + y) * 64 + c[0] + c[4] - 1 - x];
			boolean opaqueHat = true;
			for (int y = 0; y < 32; y++)
				for (int x = 32; x < 64; x++)
					if ((pixels[y * 64 + x] >>> 24) < 128) opaqueHat = false;
			if (opaqueHat)
				for (int y = 0; y < 32; y++)
					for (int x = 32; x < 64; x++) pixels[y * 64 + x] &= 0xFFFFFF;
		}
		for (int y = 0; y < 64; y++)
			for (int x = 0; x < 64; x++) {
				boolean base = SkinLayout.contains(SkinLayout.Layer.BASE, slim, x, y),
						outer = SkinLayout.contains(SkinLayout.Layer.OUTER, slim, x, y);
				if (base) pixels[y * 64 + x] |= 0xFF000000;
				else if (!outer) pixels[y * 64 + x] = 0;
			}
		return new SkinBase(slim, pixels);
	}
}
