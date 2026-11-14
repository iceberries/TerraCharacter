package org.confluence.terra_character.runtime;

import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.storage.LevelResource;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.integration.CharacterDataAdapter;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.api.session.LaunchSelection;
import org.confluence.terra_character.integration.*;
import org.confluence.terra_character.network.AvatarPayload;
import org.confluence.terra_character.network.CharacterHandshake;
import org.confluence.terra_character.network.CharacterSync;
import org.confluence.terra_character.network.ProfileStatePayload;
import org.confluence.terra_character.service.CharacterPlayTime;
import org.confluence.terra_character.service.CharacterStatisticsService;
import org.confluence.terra_character.storage.*;
import org.confluence.terra_character.storage.CharacterStore;

import java.io.IOException;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Local owner and authenticated remote accounts bind separate authoritative character stores. */
public final class CharacterSession {
	private static volatile LaunchRequest pending;
	private static volatile CharacterSession active;
	private static final Map<UUID, CharacterSession> remotes = new ConcurrentHashMap<>();
	private final LaunchRequest request;
	private final MinecraftServer server;
	private final CompoundTag document;
	private final CompoundTag originalPlayer;
	private final WorldProfile world;
	private CharacterProfile profile;
	private boolean failed;
	private volatile boolean joined;
	private final UUID account;
	private final Connection connection;
	private final CharacterPlayTime playTime;
	private final Set<String> deferredAdapters = new HashSet<>();
	private CompoundTag deferredItems = new CompoundTag();
	private CompoundTag deferredRecipes = new CompoundTag();
	private CompoundTag deferredEffects = new CompoundTag();
	private ListTag deferredAttributes = new ListTag();
	private boolean shared;
	private UUID transferToken;

	public void transferToken(UUID token) {
		transferToken = token;
	}

	public void snapshot(ServerPlayer player, boolean leaving) throws IOException {
		if (account != null && connection.isConnected())
			CharacterSync.send(
					player,
					transferToken,
					document.getLong("Revision"),
					PortableCharacter.extract(document),
					leaving);
	}

	public void syncIfChanged(ServerPlayer player) throws IOException {
		if (account == null || failed) return;
		var portable = document.getCompound("Adapters");
		for (var adapter : AdapterRegistry.all())
			if (adapter.scope() == CharacterDataAdapter.Scope.CHARACTER
					&& !deferredAdapters.contains(adapter.id().toString())
					&& !AdapterRegistry.envelope(adapter, capture(player, adapter))
							.equals(portable.getCompound(adapter.id().toString()))) {
				save(player);
				return;
			}
	}

	private CharacterSession(LaunchRequest request, MinecraftServer server) throws IOException {
		this(request, server, null, null);
	}

	private CharacterSession(
			LaunchRequest request, MinecraftServer server, UUID account, Connection connection)
			throws IOException {
		this.request = request;
		this.server = server;
		this.account = account;
		this.connection = connection;
		shared = account != null || WorldStore.shared(request.worldDirectory());
		document = request.store().read(request.character());
		playTime = new CharacterPlayTime(CharacterStatisticsCodec.read(document).playTicks());
		profile = ProfileCodec.character(document.getCompound("Profile"));
		world = WorldStore.read(request.worldDirectory()).orElse(request.world());
		new LaunchSelection(profile, world);
		originalPlayer =
				account != null || server.getWorldData().getLoadedPlayerTag() == null
						? null
						: server.getWorldData().getLoadedPlayerTag().copy();
		AdapterRegistry.validateWorld(world.settings());
		if (server.getWorldData().isHardcore())
			throw new IOException(
					"Import this hardcore world's character into a new non-hardcore world first");
		WorldStore.write(request.worldDirectory(), world);
		if (!shared) {
			var owner = new CompoundTag();
			owner.putUUID("Character", profile.id());
			AtomicNbt.write(
					WorldStore.directory(request.worldDirectory()).resolve("session.nbt"),
					owner,
					false);
		}
	}

	public static synchronized void arm(
			CharacterStore store, UUID character, Path directory, WorldProfile world)
			throws IOException {
		if (active != null) throw new IOException("A character is already playing");
		cancelPending();
		var lock = store.lock(character);
		try {
			var document = store.read(character);
			if (document.getBoolean("Archived")
					|| Files.exists(store.directory(character).resolve("dead.nbt")))
				throw new IOException("Character is unavailable");
			new LaunchSelection(ProfileCodec.character(document.getCompound("Profile")), world);
			AdapterRegistry.validateWorld(world.settings());
			if (Files.exists(WorldStore.directory(directory).resolve("session.nbt")))
				throw new IOException(
						"This world has an interrupted character session; recover that character"
								+ " first");
			boolean shared = WorldStore.shared(directory);
			if (!shared)
				SessionCheckpoint.begin(store, character, directory.toAbsolutePath().normalize());
			if (!shared && Files.isDirectory(directory)) {
				var owner = new CompoundTag();
				owner.putUUID("Character", character);
				AtomicNbt.write(
						WorldStore.directory(directory).resolve("session.nbt"), owner, false);
			}
			pending =
					new LaunchRequest(
							store, character, directory.toAbsolutePath().normalize(), world, lock);
		} catch (Exception ex) {
			lock.close();
			if (ex instanceof IOException io) throw io;
			throw new IOException(ex.getMessage(), ex);
		}
	}

	public static synchronized void start(MinecraftServer server) throws IOException {
		if (pending == null) return;
		if (!server.isSingleplayer()
				|| !server.getWorldPath(LevelResource.ROOT)
						.toAbsolutePath()
						.normalize()
						.equals(pending.worldDirectory())) {
			cancelPending();
			throw new IOException("Character launch does not match the integrated world");
		}
		active = new CharacterSession(pending, server);
		pending = null;
	}

	public static synchronized void cancelPending() throws IOException {
		if (pending != null) {
			var previous = pending;
			pending = null;
			previous.lock().close();
		}
	}

	public static synchronized void stop() {
		CharacterHandshake.clear();
		remotes.values().forEach(CharacterSession::release);
		remotes.clear();
		if (active != null) {
			try {
				active.request.lock().close();
			} catch (IOException ex) {
				TerraCharacter.LOGGER.error("Cannot release character lock", ex);
			}
			active = null;
		}
		try {
			cancelPending();
		} catch (IOException ex) {
			TerraCharacter.LOGGER.error("Cannot release pending lock", ex);
		}
	}

	public static CharacterSession of(Player player) {
		var session = active;
		if (!(player instanceof ServerPlayer sp)) return null;
		if (session != null
				&& sp.server == session.server
				&& session.server.isSingleplayerOwner(sp.getGameProfile())) return session;
		session = remotes.get(sp.getUUID());
		return session != null && session.server == sp.server ? session : null;
	}

	public static File cosmeticArmorFile(MinecraftServer server, UUID account) throws IOException {
		var session = remotes.get(account);
		if (session == null) {
			var owner = server.getSingleplayerProfile();
			if (owner != null && account.equals(owner.getId())) session = active;
		}
		return session != null && session.server == server
				? session.progressPath("cosmetic_armor", ".dat").toFile() : null;
	}

	public static synchronized void prepareRemote(
			MinecraftServer server, UUID account, CompoundTag portable, Connection connection)
			throws IOException {
		if (remotes.containsKey(account) || server.getPlayerList().getPlayer(account) != null)
			throw new IOException("This account is already playing");
		Path directory = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
		var world =
				WorldStore.read(directory)
						.orElseGet(
								() ->
										new WorldProfile(
												UUID.randomUUID(),
												server.getWorldData().getLevelName(),
												new WorldSettings(
														Long.toString(
																server.getWorldData()
																		.worldGenOptions()
																		.seed()),
														"minecraft:normal",
														WorldDifficulty.valueOf(
																server.getWorldData()
																		.getDifficulty()
																		.name()))));
		var store = RemoteCharacters.store(server.getServerDirectory(), account);
		var metadata = RemoteCharacters.metadata(portable.getCompound("Profile"));
		var lock = RemoteCharacters.prepare(store, portable);
		try {
			var request = new LaunchRequest(store, metadata.id(), directory, world, lock);
			var remote = new CharacterSession(request, server, account, connection);
			WorldStore.markShared(directory);
			remotes.put(account, remote);
		} catch (Exception ex) {
			lock.close();
			if (ex instanceof IOException io) throw io;
			throw new IOException(ex.getMessage(), ex);
		}
	}

	public static synchronized void token(UUID account, UUID token) throws IOException {
		var session = remotes.get(account);
		if (session == null)
			throw new IOException("Character disconnected before configuration completed");
		session.transferToken(Objects.requireNonNull(token, "token"));
	}

	public static synchronized void disconnected(Connection connection) {
		remotes.entrySet()
				.removeIf(
						e -> {
							var session = e.getValue();
							if (session.connection != connection || session.joined) return false;
							session.release();
							return true;
						});
	}

	public static synchronized void sweep(MinecraftServer server) {
		remotes.entrySet()
				.removeIf(
						e -> {
							var s = e.getValue();
							if (s.server != server || s.joined || s.connection.isConnected())
								return false;
							s.release();
							return true;
						});
	}

	public static void removed(ServerPlayer player) {
		var session = remotes.get(player.getUUID());
		if (session != null
				&& session.server == player.server
				&& session.connection == player.connection.getConnection()
				&& remotes.remove(player.getUUID(), session)) {
			session.release();
		}
		AvatarPayload.departed(player);
	}

	private void release() {
		try {
			request.lock().close();
		} catch (IOException ex) {
			TerraCharacter.LOGGER.error("Cannot release remote character lock", ex);
		}
	}

	public void prepareShare() throws IOException {
		if (shared) return;
		if (failed || !joined)
			throw new IOException("Character must finish loading and saving before sharing");
		// Publish only after the offline transaction has committed; future recovery cannot rewind
		// guests.
		server.saveEverything(false, true, true);
		WorldStore.markShared(request.worldDirectory());
		shared = true;
		Files.deleteIfExists(WorldStore.directory(request.worldDirectory()).resolve("session.nbt"));
		SessionCheckpoint.finish(request.store(), profile.id());
	}

	/**
	 * Integrated owner only. Player-specific hooks must use of(player), including on dedicated
	 * servers.
	 */
	public static CharacterSession current() {
		return active;
	}

	public static boolean hasPending() {
		return pending != null;
	}

	public void finish() {
		if (!shared && !failed && joined)
			try {
				Files.deleteIfExists(
						WorldStore.directory(request.worldDirectory()).resolve("session.nbt"));
				SessionCheckpoint.finish(request.store(), profile.id());
			} catch (IOException ex) {
				TerraCharacter.LOGGER.error(
						"Cannot finish checkpoint; recovery remains required", ex);
			}
	}

	public CharacterProfile profile() {
		return profile;
	}

	public WorldProfile world() {
		return world;
	}

	public CompoundTag journey() {
		return document.getCompound("Journey");
	}

	public void journey(CompoundTag value) {
		document.put("Journey", value);
	}

	public CompoundTag originalPlayer() {
		return originalPlayer == null ? null : originalPlayer.copy();
	}

	public boolean failed() {
		return failed;
	}

	public boolean keepInventory() {
		return profile.difficulty() == CharacterDifficulty.SOFTCORE
				|| profile.difficulty() == CharacterDifficulty.JOURNEY;
	}

	public static boolean keepInventory(Player player, boolean fallback) {
		var session = of(player);
		return session == null ? fallback : session.keepInventory();
	}

	public static boolean hardcore(Player player) {
		var session = of(player);
		return session != null && session.profile.difficulty() == CharacterDifficulty.HARDCORE;
	}

	public CompoundTag load(ServerPlayer player) throws IOException {
		var portable = document.getCompound("Adapters");
		var local = document.getCompound("Worlds").getCompound(world.id().toString());
		var base =
				VanillaDataAdapter.select(
						player.saveWithoutId(new CompoundTag()), VanillaDataAdapter.WORLD_KEYS);
		var vanilla = vanillaData(portable, VanillaDataAdapter.PORTABLE);
		var location = vanillaData(local, VanillaDataAdapter.LOCAL);
		if (!VanillaDataAdapter.CHARACTER_KEYS.containsAll(vanilla.getAllKeys())
				|| location.getAllKeys().stream().anyMatch(
						key -> !key.equals("NeedsRespawn") && !VanillaDataAdapter.WORLD_KEYS.contains(key)))
			throw new IOException("Unexpected core character fields");
		VanillaDataAdapter.validateWorldDimensions(player, location);
		deferredItems = PortableInventory.prepare(vanilla, player.registryAccess());
		deferredEffects = PortableEffects.prepare(vanilla, player.registryAccess());
		deferredRecipes = PortableRecipes.prepare(vanilla, key -> {
			var id = ResourceLocation.tryParse(key);
			return id != null && player.server.getRecipeManager().byKey(id).isPresent();
		});
		var vanillaWithoutItems = vanilla.copy();
		vanillaWithoutItems.remove("Inventory");
		vanillaWithoutItems.remove("EnderItems");
		DataIntegrity.validate(vanillaWithoutItems, player.registryAccess());
		base.merge(vanilla);
		if (!location.getBoolean("NeedsRespawn")) base.merge(location.copy());
		else {
			for (String key : VanillaDataAdapter.WORLD_KEYS)
				if (key.startsWith("Spawn") && location.contains(key))
					base.put(key, location.get(key).copy());
		}
		// A fresh character never inherits the account's inventory or mod attachments.
		for (var adapter : AdapterRegistry.all()) adapter.initialize(player);
		player.load(base);
		if (location.getBoolean("NeedsRespawn")) {
			var respawn =
					player.findRespawnPositionAndUseSpawnBlock(
							false, DimensionTransition.DO_NOTHING);
			player.setServerLevel(respawn.newLevel());
			player.moveTo(
					respawn.pos().x,
					respawn.pos().y,
					respawn.pos().z,
					respawn.yRot(),
					respawn.xRot());
			var fresh =
					VanillaDataAdapter.select(
							player.saveWithoutId(new CompoundTag()), VanillaDataAdapter.WORLD_KEYS);
			base.merge(fresh);
		}
		for (var adapter : AdapterRegistry.all()) {
			if (adapter instanceof VanillaDataAdapter) continue;
			var group = adapter.scope() == CharacterDataAdapter.Scope.CHARACTER ? portable : local;
			String id = adapter.id().toString();
			if (!group.contains(id)) continue;
			var entry = group.getCompound(id);
			try {
				int version = entry.getInt("Version");
				var data = entry.getCompound("Data").copy();
				if (version != adapter.schemaVersion()) {
					if (version > adapter.schemaVersion())
						throw new IOException("Newer adapter schema: " + version);
					data = adapter.migrate(player, data, version)
							.orElseThrow(() -> new IOException("No migration for schema " + version));
				}
				DataIntegrity.validate(data, player.registryAccess());
				if (adapter instanceof PlayerAttributesAdapter attributes)
					deferredAttributes = attributes.deferUnavailable(player, data);
				adapter.restore(player, data, adapter.schemaVersion());
			} catch (IOException | RuntimeException | LinkageError ex) {
				adapter.initialize(player);
				deferredAdapters.add(id);
				TerraCharacter.LOGGER.warn("Preserving incompatible character data for adapter {}", id, ex);
			}
		}
		return base;
	}

	private static CompoundTag vanillaData(CompoundTag group, String id) throws IOException {
		if (!group.contains(id)) return new CompoundTag();
		var entry = group.getCompound(id);
		if (entry.getInt("Version") != 1)
			throw new IOException("Unsupported core character schema: " + id);
		return entry.getCompound("Data").copy();
	}

	boolean isAdapterActive(CharacterDataAdapter adapter) {
		return !deferredAdapters.contains(adapter.id().toString());
	}

	private CompoundTag capture(ServerPlayer player, CharacterDataAdapter adapter) {
		var data = adapter.capture(player);
		if (adapter.id().toString().equals(VanillaDataAdapter.PORTABLE)) {
			if (!deferredItems.isEmpty()) data.put(PortableInventory.DEFERRED, deferredItems.copy());
			if (!deferredRecipes.isEmpty()) data.put(PortableRecipes.DEFERRED, deferredRecipes.copy());
			if (!deferredEffects.isEmpty() && !player.isDeadOrDying())
				data.put(PortableEffects.DEFERRED, deferredEffects.copy());
		}
		if (adapter instanceof PlayerAttributesAdapter && !deferredAttributes.isEmpty())
			data.getList("Attributes", Tag.TAG_COMPOUND).addAll(deferredAttributes.copy());
		return data;
	}

	public void joined(ServerPlayer player) {
		try {
			verifyAttachments(player);
			for (var adapter : AdapterRegistry.all())
				if (isAdapterActive(adapter)) adapter.afterRestore(player);
			var vanilla =
					document.getCompound("Adapters")
							.getCompound(VanillaDataAdapter.PORTABLE)
							.getCompound("Data");
			if (vanilla.contains("Health"))
				player.setHealth(
						Math.max(1, Math.min(vanilla.getFloat("Health"), player.getMaxHealth())));
			player.setGameMode(GameType.SURVIVAL);
			JourneyController.apply(player);
			ProfileStatePayload.send(player);
			save(player);
			joined = true;
			if (!deferredItems.isEmpty() || !deferredRecipes.isEmpty() || !deferredEffects.isEmpty()
					|| !deferredAttributes.isEmpty() || !deferredAdapters.isEmpty())
				player.sendSystemMessage(Component.translatable("terra_character.compatibility.deferred"));
			AvatarPayload.joined(player);
		} catch (Exception ex) {
			fail(player, ex);
		}
	}

	public void tickPlayTime() {
		playTime.tick(joined && !failed);
	}

	public void save(ServerPlayer player) throws IOException {
		if (failed) throw new IOException("Character session suspended after a storage failure");
		verifyAttachments(player);
		var next = document.copy();
		var portable = next.getCompound("Adapters");
		var worlds = next.getCompound("Worlds");
		var local = worlds.getCompound(world.id().toString());
		for (var adapter : AdapterRegistry.all()) {
			if (!isAdapterActive(adapter)) continue;
			var data = capture(player, adapter);
			if (adapter.id().toString().equals(VanillaDataAdapter.PORTABLE)
					&& player.isDeadOrDying()) {
				data.putFloat("Health", player.getMaxHealth());
				data.remove("active_effects");
				data.remove("ActiveEffects");
			}
			if (adapter.id().toString().equals(VanillaDataAdapter.LOCAL))
				data.putBoolean("NeedsRespawn", player.isDeadOrDying());
			(adapter.scope() == CharacterDataAdapter.Scope.CHARACTER ? portable : local)
					.put(adapter.id().toString(), AdapterRegistry.envelope(adapter, data));
		}
		worlds.put(world.id().toString(), local);
		next.put("Worlds", worlds);
		next.put("Adapters", portable);
		next.put("Profile", ProfileCodec.character(profile));
		next.put(
				"Statistics",
				CharacterStatisticsCodec.encode(
						CharacterStatisticsService.capture(player, playTime.getTicks())));
		request.store().commit(profile.id(), next, document.getLong("Revision"));
		// Replace snapshots wholesale so removed recovery entries cannot return in multiplayer sync.
		for (String key : next.getAllKeys()) document.put(key, next.get(key));
		if (player.isDeadOrDying()) deferredEffects = new CompoundTag();
		snapshot(player, false);
		// The character bundle is authoritative. A failed mirror is repaired from it next time.
		var mirror = new CompoundTag();
		mirror.putLong("Revision", document.getLong("Revision"));
		mirror.putUUID("Character", profile.id());
		mirror.putUUID("World", world.id());
		mirror.put("Adapters", local.copy());
		try {
			AtomicNbt.write(
					WorldStore.directory(request.worldDirectory())
							.resolve("characters/" + identityPath() + ".nbt"),
					mirror,
					false);
		} catch (IOException ex) {
			TerraCharacter.LOGGER.warn(
					"World mirror will be regenerated from the committed character", ex);
		}
	}

	public void die(ServerPlayer player) throws IOException {
		if (profile.difficulty() == CharacterDifficulty.HARDCORE) {
			var tombstone = new CompoundTag();
			tombstone.putUUID("Id", profile.id());
			tombstone.putLong("DiedAt", System.currentTimeMillis());
			// The tombstone is authoritative even if the subsequent bundle commit fails.
			AtomicNbt.write(
					request.store().directory(profile.id()).resolve("dead.nbt"), tombstone, false);
			profile =
					new CharacterProfile(
							profile.id(),
							profile.name(),
							profile.difficulty(),
							profile.appearance(),
							CharacterProfile.Status.DEAD);
			save(player);
			ProfileStatePayload.send(player);
		}
		for (var adapter : AdapterRegistry.all())
			if (isAdapterActive(adapter)) adapter.onDeath(player, keepInventory());
	}

	public Path progressPath(String kind) throws IOException {
		return progressPath(kind, ".json");
	}

	private Path progressPath(String kind, String extension) throws IOException {
		Path path =
				WorldStore.directory(request.worldDirectory())
						.resolve("progress/" + identityPath());
		Files.createDirectories(path);
		return path.resolve(kind + extension);
	}

	private String identityPath() {
		return account == null ? profile.id().toString() : account + "/" + profile.id();
	}

	private void verifyAttachments(ServerPlayer player) throws IOException {
		var known = new HashSet<String>();
		AdapterRegistry.all().forEach(adapter -> known.addAll(adapter.legacyAttachmentIds()));
		var attachments = player.serializeAttachments(player.registryAccess());
		if (attachments != null)
			for (String key : attachments.getAllKeys()) {
				if (!known.contains(key))
					throw new IOException("No character ownership adapter for attachment: " + key);
			}
	}

	public void fail(ServerPlayer player, Exception ex) {
		failed = true;
		TerraCharacter.LOGGER.error(
				"Character session suspended; keeping the last committed save", ex);
		if (player.connection != null)
			player.connection.disconnect(
					Component.translatable("terra_character.error.save_failed"));
	}
}
