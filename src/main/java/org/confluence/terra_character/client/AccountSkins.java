package org.confluence.terra_character.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.SignatureState;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.nbt.CompoundTag;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.model.*;
import org.confluence.terra_character.service.SkinBasePixels;
import org.confluence.terra_character.storage.AtomicNbt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Official account lookup runs off-thread. Saved characters carry their pixels, never a remote URL.
 */
final class AccountSkins {
	static final String STYLE = "terra_character:account";

	record Result(SkinBase skin, boolean official) {}

	private static volatile Result current;
	private static String account;
	private static long generation;
	private static CompletableFuture<Result> request;

	private AccountSkins() {}

	static synchronized SkinBase current() {
		var mc = Minecraft.getInstance();
		String name = mc.getUser().getName().toLowerCase(Locale.ROOT);
		if (!name.equals(account)) {
			account = name;
			generation++;
			request = null;
			current = new Result(defaultSkin(mc.getUser().getProfileId()), false);
		}
		return current.skin();
	}

	static CharacterAppearance appearance() {
		var skin = current();
		return new CharacterAppearance(
				skin.slim() ? "terra_character:slim" : "terra_character:wide",
				STYLE,
				Map.of(),
				SkinPaint.EMPTY,
				Wardrobe.EMPTY,
				DyeMasks.EMPTY,
				skin);
	}

	static CharacterAppearance preset(CharacterAppearance value) {
		if (!value.hairStyle().equals(STYLE)) return value;
		var skin = current();
		return new CharacterAppearance(
				skin.slim() ? "terra_character:slim" : "terra_character:wide",
				STYLE,
				value.colors(),
				value.paint(),
				value.wardrobe(),
				value.dyeMasks(),
				skin);
	}

	static synchronized CompletableFuture<Result> load() {
		current();
		if (request != null) return request;
		var mc = Minecraft.getInstance();
		String name = mc.getUser().getName();
		if (!name.matches("[A-Za-z0-9_]{1,16}"))
			return request = CompletableFuture.completedFuture(current);
		Path cache =
				mc.gameDirectory
						.toPath()
						.resolve("terra_character/account_skins")
						.resolve(name.toLowerCase(Locale.ROOT) + ".nbt");
		long requestedGeneration = generation;
		var fallback = new AtomicReference<>(current);
		var source =
				CompletableFuture.supplyAsync(
								() -> readAccount(name, cache, fallback), Util.nonCriticalIoPool())
						.orTimeout(8, TimeUnit.SECONDS);
		request = completeRequest(source, requestedGeneration, fallback, name);
		return request;
	}

	private static Result readAccount(String name, Path cache, AtomicReference<Result> fallback) {
		try {
			if (Files.isRegularFile(cache)) {
				try {
					var tag = AtomicNbt.read(cache);
					if (!tag.contains("Pixels", 11)
							|| tag.getIntArray("Pixels").length != 4096
							|| !tag.contains("Slim", 1))
						throw new IOException("Invalid cached account skin");
					var cached =
							new Result(
									new SkinBase(tag.getBoolean("Slim"), tag.getIntArray("Pixels")),
									true);
					fallback.set(cached);
					long age = System.currentTimeMillis() - tag.getLong("Fetched");
					if (age >= 0 && age < 6 * 60 * 60 * 1000L) return cached;
				} catch (Exception ex) {
					TerraCharacter.LOGGER.debug("Cannot use account skin cache", ex);
				}
			}
			var result = download(name);
			// A cache failure must not discard an otherwise valid downloaded skin.
			try {
				save(cache, result.skin());
			} catch (IOException ex) {
				TerraCharacter.LOGGER.warn("Cannot cache account skin", ex);
			}
			return result;
		} catch (Exception ex) {
			throw new CompletionException(ex);
		}
	}

	private static CompletableFuture<Result> completeRequest(
			CompletableFuture<Result> source,
			long requestedGeneration,
			AtomicReference<Result> fallback,
			String name) {
		return source.handle(
				(result, error) -> {
					if (error != null) {
						TerraCharacter.LOGGER.debug("Account skin lookup failed", error);
						TerraCharacter.LOGGER.info(
								"Account skin unavailable; using cached or vanilla skin for {}",
								name);
						result = fallback.get();
					}
					synchronized (AccountSkins.class) {
						if (generation == requestedGeneration) current = result;
					}
					return result;
				});
	}

	private static Result download(String name) throws IOException {
		var mc = Minecraft.getInstance();
		var found = new AtomicReference<GameProfile>();
		var failed = new AtomicReference<Exception>();
		new YggdrasilAuthenticationService(mc.getProxy())
				.createProfileRepository()
				.findProfilesByNames(
						new String[] {name},
						new ProfileLookupCallback() {
							@Override
							public void onProfileLookupSucceeded(GameProfile profile) {
								found.set(profile);
							}

							@Override
							public void onProfileLookupFailed(String profileName, Exception error) {
								failed.set(error);
							}
						});
		var profile = found.get();
		if (profile == null) throw new IOException("Account lookup failed", failed.get());
		var result = mc.getMinecraftSessionService().fetchProfile(profile.getId(), true);
		if (result == null) throw new IOException("Account profile unavailable");
		var textures = mc.getMinecraftSessionService().getTextures(result.profile());
		if (textures.skin() == null) return new Result(defaultSkin(profile.getId()), true);
		if (textures.signatureState() != SignatureState.SIGNED)
			throw new IOException("Unsigned account skin");
		URI address = URI.create(textures.skin().getUrl());
		if (!Set.of("http", "https").contains(address.getScheme())
				|| !"textures.minecraft.net".equalsIgnoreCase(address.getHost())
				|| !address.getPath().matches("/texture/[a-fA-F0-9]+")
				|| address.getUserInfo() != null
				|| address.getPort() != -1
				|| address.getQuery() != null)
			throw new IOException("Invalid official texture address");
		var connection =
				(HttpURLConnection)
						URI.create("https://textures.minecraft.net" + address.getPath())
								.toURL()
								.openConnection(mc.getProxy());
		connection.setConnectTimeout(4000);
		connection.setReadTimeout(4000);
		connection.setInstanceFollowRedirects(false);
		try {
			if (connection.getResponseCode() != 200)
				throw new IOException("Account skin download failed");
			byte[] bytes;
			try (var stream = connection.getInputStream()) {
				bytes = stream.readNBytes(256 * 1024 + 1);
			}
			if (bytes.length > 256 * 1024
					|| bytes.length < 24
					|| !Arrays.equals(
							Arrays.copyOf(bytes, 8),
							new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
				throw new IOException("Invalid skin PNG");
			int width = ByteBuffer.wrap(bytes, 16, 4).getInt(),
					height = ByteBuffer.wrap(bytes, 20, 4).getInt();
			if (width != 64 || (height != 32 && height != 64))
				throw new IOException("Unsupported skin dimensions");
			try (var image = NativeImage.read(new ByteArrayInputStream(bytes))) {
				return new Result(
						pixels(image, "slim".equals(textures.skin().getMetadata("model"))), true);
			}
		} finally {
			connection.disconnect();
		}
	}

	private static SkinBase defaultSkin(UUID id) {
		var skin = DefaultPlayerSkin.get(id);
		try (var stream = Minecraft.getInstance().getResourceManager().open(skin.texture());
				var image = NativeImage.read(stream)) {
			return pixels(image, skin.model() == PlayerSkin.Model.SLIM);
		} catch (IOException ex) {
			throw new IllegalStateException("Cannot load vanilla default skin", ex);
		}
	}

	private static SkinBase pixels(NativeImage image, boolean slim) {
		int w = image.getWidth(), h = image.getHeight();
		int[] pixels = new int[w * h];
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++)
				pixels[y * w + x] = CharacterSkins.swapRedBlue(image.getPixelRGBA(x, y));
		return SkinBasePixels.normalize(pixels, w, h, slim);
	}

	private static void save(Path cache, SkinBase skin) throws IOException {
		var tag = new CompoundTag();
		tag.putBoolean("Slim", skin.slim());
		tag.putIntArray("Pixels", skin.pixels());
		tag.putLong("Fetched", System.currentTimeMillis());
		AtomicNbt.write(cache, tag, false);
		try (var image = new NativeImage(64, 64, true)) {
			for (int y = 0; y < 64; y++)
				for (int x = 0; x < 64; x++)
					image.setPixelRGBA(x, y, CharacterSkins.swapRedBlue(skin.pixel(x, y)));
			Path png = cache.resolveSibling(cache.getFileName().toString().replace(".nbt", ".png"));
			if (Files.isSymbolicLink(png)) throw new IOException("Symbolic link cache rejected");
			image.writeToFile(png);
		}
	}
}
