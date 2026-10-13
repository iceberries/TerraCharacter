package org.confluence.terra_character.api.model;

import java.util.Map;
import java.util.Objects;

/**
 * Resource-based styles, named RGB channels and immutable pixel edits; rendered by the client
 * layer.
 */
public record CharacterAppearance(
		String bodyStyle,
		String hairStyle,
		Map<String, Integer> colors,
		SkinPaint paint,
		Wardrobe wardrobe,
		DyeMasks dyeMasks,
		SkinBase baseSkin) {
	public CharacterAppearance(
			String bodyStyle,
			String hairStyle,
			Map<String, Integer> colors,
			SkinPaint paint,
			Wardrobe wardrobe,
			DyeMasks dyeMasks) {
		this(bodyStyle, hairStyle, colors, paint, wardrobe, dyeMasks, SkinBase.EMPTY);
	}

	public CharacterAppearance(
			String bodyStyle,
			String hairStyle,
			Map<String, Integer> colors,
			SkinPaint paint,
			Wardrobe wardrobe) {
		this(bodyStyle, hairStyle, colors, paint, wardrobe, DyeMasks.EMPTY);
	}

	public CharacterAppearance(
			String bodyStyle, String hairStyle, Map<String, Integer> colors, SkinPaint paint) {
		this(bodyStyle, hairStyle, colors, paint, Wardrobe.EMPTY);
	}

	public CharacterAppearance(String bodyStyle, String hairStyle, Map<String, Integer> colors) {
		this(bodyStyle, hairStyle, colors, SkinPaint.EMPTY);
	}

	public CharacterAppearance withPaint(SkinPaint value) {
		return new CharacterAppearance(
				bodyStyle, hairStyle, colors, value, wardrobe, dyeMasks, baseSkin);
	}

	public CharacterAppearance withWardrobe(Wardrobe value) {
		return new CharacterAppearance(
				bodyStyle, hairStyle, colors, paint, value, dyeMasks, baseSkin);
	}

	public CharacterAppearance withDyeMasks(DyeMasks value) {
		return new CharacterAppearance(
				bodyStyle, hairStyle, colors, paint, wardrobe, value, baseSkin);
	}

	public CharacterAppearance withBaseSkin(SkinBase value) {
		return new CharacterAppearance(
				bodyStyle, hairStyle, colors, paint, wardrobe, dyeMasks, value);
	}

	public CharacterAppearance {
		paint = Objects.requireNonNull(paint, "paint");
		wardrobe = Objects.requireNonNull(wardrobe, "wardrobe");
		dyeMasks = Objects.requireNonNull(dyeMasks, "dyeMasks");
		baseSkin = Objects.requireNonNull(baseSkin, "baseSkin");
		bodyStyle = ProfileValidation.resourceId(bodyStyle);
		hairStyle = ProfileValidation.resourceId(hairStyle);
		colors = Map.copyOf(Objects.requireNonNull(colors, "colors"));
		if (colors.size() > 32)
			throw new IllegalArgumentException("At most 32 color channels are allowed");
		colors.forEach(
				(channel, rgb) -> {
					ProfileValidation.resourceId(channel);
					if (channel.length() > 255)
						throw new IllegalArgumentException("Color channel ID too long");
					if (rgb < 0 || rgb > 0xFFFFFF) {
						throw new IllegalArgumentException("Color must be a 24-bit RGB value");
					}
				});
	}
}
