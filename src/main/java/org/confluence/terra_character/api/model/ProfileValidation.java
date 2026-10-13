package org.confluence.terra_character.api.model;

import java.util.Objects;

final class ProfileValidation {
	private ProfileValidation() {}

	static String name(String value) {
		String name = Objects.requireNonNull(value, "name").strip();
		if (name.isEmpty()
				|| name.codePointCount(0, name.length()) > 64
				|| name.codePoints().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException(
					"Name must contain 1 to 64 characters without control characters");
		}
		return name;
	}

	static String resourceId(String value) {
		if (!Objects.requireNonNull(value, "resourceId").matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
			throw new IllegalArgumentException("Expected a namespaced resource ID: " + value);
		}
		return value;
	}
}
