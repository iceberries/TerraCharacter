package org.confluence.terra_character.service;

/** Pure ARGB math shared by the vanilla atlas compositor and focused tests. */
public final class SkinPixelBlend {
	private SkinPixelBlend() {}

	public static int tint(int argb, int rgb) {
		return (argb & 0xFF000000)
				| (((argb >>> 16 & 255) * (rgb >>> 16 & 255) / 255) << 16)
				| (((argb >>> 8 & 255) * (rgb >>> 8 & 255) / 255) << 8)
				| ((argb & 255) * (rgb & 255) / 255);
	}

	public static int compose(int destination, int source, boolean replace, boolean base) {
		if (replace && !base) return source;
		int a = source >>> 24, da = base ? 255 : destination >>> 24;
		int alphaNumerator = a * 255 + da * (255 - a);
		int outA = (alphaNumerator + 127) / 255;
		if (outA == 0) return 0;
		int result = outA << 24;
		for (int shift : new int[] {16, 8, 0}) {
			int numerator =
					(source >>> shift & 255) * a * 255
							+ (destination >>> shift & 255) * da * (255 - a);
			result |= ((numerator + alphaNumerator / 2) / alphaNumerator) << shift;
		}
		return result;
	}
}
