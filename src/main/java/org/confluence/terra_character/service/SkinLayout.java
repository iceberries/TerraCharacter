package org.confluence.terra_character.service;

/** Standard modern Minecraft skin UVs, including independent left limbs and slim arm widths. */
public final class SkinLayout {
	public enum Part {
		HEAD,
		BODY,
		RIGHT_ARM,
		LEFT_ARM,
		RIGHT_LEG,
		LEFT_LEG
	}

	public enum Face {
		FRONT,
		BACK,
		LEFT,
		RIGHT,
		TOP,
		BOTTOM
	}

	public enum Layer {
		BASE,
		OUTER
	}

	public record Region(int x, int y, int width, int height) {
		public boolean contains(int px, int py) {
			return px >= x && py >= y && px < x + width && py < y + height;
		}
	}

	private SkinLayout() {}

	public static Region region(Part part, Face face, Layer layer, boolean slim) {
		int u, v, w, h, d;
		boolean outer = layer == Layer.OUTER;
		switch (part) {
			case HEAD -> {
				u = outer ? 32 : 0;
				v = 0;
				w = 8;
				h = 8;
				d = 8;
			}
			case BODY -> {
				u = 16;
				v = outer ? 32 : 16;
				w = 8;
				h = 12;
				d = 4;
			}
			case RIGHT_ARM -> {
				u = 40;
				v = outer ? 32 : 16;
				w = slim ? 3 : 4;
				h = 12;
				d = 4;
			}
			case LEFT_ARM -> {
				u = outer ? 48 : 32;
				v = 48;
				w = slim ? 3 : 4;
				h = 12;
				d = 4;
			}
			case RIGHT_LEG -> {
				u = 0;
				v = outer ? 32 : 16;
				w = 4;
				h = 12;
				d = 4;
			}
			case LEFT_LEG -> {
				u = outer ? 0 : 16;
				v = 48;
				w = 4;
				h = 12;
				d = 4;
			}
			default -> throw new IllegalArgumentException("Unknown skin part");
		}
		return switch (face) {
			case TOP -> new Region(u + d, v, w, d);
			case BOTTOM -> new Region(u + d + w, v, w, d);
			case RIGHT -> new Region(u, v + d, d, h);
			case FRONT -> new Region(u + d, v + d, w, h);
			case LEFT -> new Region(u + d + w, v + d, d, h);
			case BACK -> new Region(u + d + w + d, v + d, w, h);
		};
	}

	public static boolean contains(Layer layer, boolean slim, int x, int y) {
		for (Part part : Part.values())
			for (Face face : Face.values()) {
				if (region(part, face, layer, slim).contains(x, y)) return true;
			}
		return false;
	}
}
