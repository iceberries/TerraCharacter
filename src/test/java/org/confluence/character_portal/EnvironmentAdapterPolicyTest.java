package org.confluence.character_portal;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.confluence.character_portal.api.integration.CharacterDataAdapter.Scope;
import org.confluence.character_portal.api.integration.DiscoverStateAdaptersEvent;
import org.confluence.character_portal.integration.EnvironmentAdapters;
import org.confluence.character_portal.storage.WorldStore;
import org.confluence.character_portal.storage.AtomicNbt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;

class EnvironmentAdapterPolicyTest {
	@TempDir Path world;
	@Test void unknownOwnershipStaysWorldLocalAndCanBeDeclaredPortable() {
		var policy = new DiscoverStateAdaptersEvent();
		var id = ResourceLocation.parse("other_mod:progress");
		assertEquals(Scope.CHARACTER_WORLD, policy.attachmentScope(id));
		policy.attachmentScope(id, Scope.CHARACTER);
		assertEquals(Scope.CHARACTER, policy.attachmentScope(id));
		assertFalse(policy.excluded(id));
		policy.excludeAttachment(id);
		assertTrue(policy.excluded(id));
	}
	@Test void liveWorldInspectionPreservesTheOriginalCreationRecord() throws Exception {
		var creation = new CompoundTag();
		creation.putString("Generator", "other_mod:custom");
		WorldStore.environment(world, "Creation", creation);
		creation.putString("Generator", "changed");
		var live = new CompoundTag();
		live.putString("Biome", "other_mod:forest");
		WorldStore.environment(world, "Runtime", live);
		var stored = AtomicNbt.read(WorldStore.directory(world).resolve("environment.nbt"));
		assertEquals("other_mod:custom", stored.getCompound("Creation").getString("Generator"));
		assertEquals("other_mod:forest", stored.getCompound("Runtime").getString("Biome"));
	}
	@Test void catalogIsStableAndOwnedByTheCaller() {
		assertEquals(EnvironmentAdapters.strings(List.of("b:two", "a:one", "b:two")),
				EnvironmentAdapters.strings(List.of("a:one", "b:two")));
		var snapshot = EnvironmentAdapters.snapshot();
		snapshot.putString("NotShared", "changed");
		assertFalse(EnvironmentAdapters.snapshot().contains("NotShared"));
	}
}
