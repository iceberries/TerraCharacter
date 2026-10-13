package org.confluence.terra_character.api.model;

import java.util.UUID;

/** Profile identity is independent of account UUIDs, display names and save folder names. */
public interface Profile {
	UUID id();

	String name();
}
