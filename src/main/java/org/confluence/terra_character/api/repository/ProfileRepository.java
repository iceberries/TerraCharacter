package org.confluence.terra_character.api.repository;

import org.confluence.terra_character.api.model.Profile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage boundary. Implementations must isolate corrupt entries and return immutable list
 * snapshots.
 */
public interface ProfileRepository<P extends Profile> {
	List<P> list() throws IOException;

	Optional<P> find(UUID id) throws IOException;

	/** Atomically inserts a new identity; never overwrites an existing profile. */
	void create(P profile) throws IOException;
}
