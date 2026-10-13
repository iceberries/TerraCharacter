package org.confluence.terra_character.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.model.CharacterProfile;
import org.confluence.terra_character.network.*;
import org.confluence.terra_character.network.CharacterHandshake;
import org.confluence.terra_character.storage.*;

import java.io.IOException;
import java.util.*;

@EventBusSubscriber(modid = TerraCharacter.MODID, value = Dist.CLIENT)
public final class MultiplayerClient {
	private static CharacterProfile selection;

	public static CharacterProfile getSelection() {
		return selection;
	}

	public static void setSelection(CharacterProfile value) {
		if (value != null && accepted != null && accepted.isConnected())
			throw new IllegalStateException("Cannot change characters while connected");
		selection = value;
	}

	private static Connection accepted;
	private static CharacterStore.IdentityLock lock;
	private static UUID token, identity;
	private static long localRevision, lastRevision = -1, receivingRevision, leaveStarted;
	private static boolean receivingLeave, bypass;
	private static CharacterTransfer.Assembly assembly;
	private static Runnable afterLeave;
	private static final Map<UUID, CharacterProfile> avatars = new HashMap<>();

	public static CharacterProfile avatar(UUID account) {
		return avatars.get(account);
	}

	public static void select(CharacterHandshake.Request request, IPayloadContext context) {
		if (selection == null) {
			context.disconnect(ClientProfiles.text("network.select_first"));
			return;
		}
		try {
			release();
			identity = selection.id();
			lock = ClientProfiles.store().lock(identity);
			var document = PortableCharacter.readForTravel(ClientProfiles.store(), identity);
			var portable = PortableCharacter.extract(document);
			localRevision = document.getLong("Revision");
			lastRevision = -1;
			accepted = context.connection();
			token = request.nonce();
			var parts = CharacterTransfer.parts(portable);
			for (int i = 0; i < parts.size(); i++)
				context.reply(
						new CharacterHandshake.Response(token, i, parts.size(), parts.get(i)));
		} catch (Exception ex) {
			release();
			context.disconnect(ClientProfiles.text("network.rejected", ex.getMessage()));
		}
	}

	public static void sync(CharacterSync payload, IPayloadContext context) {
		if (context.connection() != accepted || !payload.token().equals(token) || lock == null)
			return;
		try {
			if (payload.revision() < lastRevision) return;
			if (payload.index() == 0) {
				if (assembly != null)
					throw new IOException("Incomplete preceding character snapshot");
				assembly = new CharacterTransfer.Assembly();
				receivingRevision = payload.revision();
				receivingLeave = payload.leaving();
			}
			if (assembly == null
					|| receivingRevision != payload.revision()
					|| receivingLeave != payload.leaving())
				throw new IOException("Mismatched character snapshot");
			var portable = assembly.accept(payload.index(), payload.total(), payload.bytes());
			if (portable == null) return;
			assembly = null;
			var local = ClientProfiles.store().read(identity);
			if (local.getLong("Revision") != localRevision)
				throw new IOException("Local character changed during multiplayer");
			PortableCharacter.validate(portable);
			var updated = ProfileCodec.character(portable.getCompound("Profile"));
			if (!updated.id().equals(identity))
				throw new IOException("Character snapshot identity mismatch");
			// A leave acknowledgement may repeat the just-saved revision. Validate it,
			// but do not commit it a second time or discard the acknowledgement.
			if (payload.revision() == lastRevision) {
				if (!PortableCharacter.extract(local).equals(portable))
					throw new IOException("Conflicting character snapshots at the same revision");
			} else {
				if (updated.status() == CharacterProfile.Status.DEAD) {
					var tombstone = new CompoundTag();
					tombstone.putUUID("Id", identity);
					tombstone.putLong("DiedAt", System.currentTimeMillis());
					AtomicNbt.write(
							ClientProfiles.store().directory(identity).resolve("dead.nbt"),
							tombstone,
							false);
				}
				PortableCharacter.merge(ClientProfiles.store(), identity, portable);
				localRevision++;
				lastRevision = payload.revision();
				selection = updated;
			}
			if (payload.leaving() && afterLeave != null) {
				var action = afterLeave;
				afterLeave = null;
				bypass = true;
				try {
					action.run();
				} finally {
					bypass = false;
				}
			}
		} catch (Exception ex) {
			TerraCharacter.LOGGER.error("Cannot save multiplayer character locally", ex);
			afterLeave = null;
			context.disconnect(ClientProfiles.text("network.sync_failed", ex.getMessage()));
		}
	}

	/**
	 * Returns true when the caller must defer its disconnect until the final snapshot is durable.
	 */
	public static boolean leave(Runnable action) {
		if (bypass
				|| accepted == null
				|| !accepted.isConnected()
				|| Minecraft.getInstance().getConnection() == null) return false;
		if (afterLeave == null) {
			afterLeave = action;
			leaveStarted = System.currentTimeMillis();
			Minecraft.getInstance()
					.setScreen(new GenericMessageScreen(ClientProfiles.text("network.saving")));
			PacketDistributor.sendToServer(new CharacterSync.Leave());
		}
		return true;
	}

	private static void release() {
		if (lock != null)
			try {
				lock.close();
			} catch (Exception ex) {
				TerraCharacter.LOGGER.error("Cannot release local character lock", ex);
			}
		lock = null;
		accepted = null;
		token = null;
		identity = null;
		assembly = null;
		afterLeave = null;
	}

	@SubscribeEvent
	public static void tick(ClientTickEvent.Post event) {
		if (accepted != null && !accepted.isConnected()) release();
		if (afterLeave != null && System.currentTimeMillis() - leaveStarted > 15000) {
			afterLeave = null;
			Minecraft.getInstance().setScreen(new PauseScreen(true));
			ClientProfiles.error(
					Minecraft.getInstance().screen,
					new IOException(ClientProfiles.text("network.sync_timeout").getString()));
		}
	}

	public static void avatar(CompoundTag data) {
		try {
			UUID account = data.getUUID("Account");
			if (data.contains("Profile"))
				avatars.put(account, ProfileCodec.character(data.getCompound("Profile")));
			else avatars.remove(account);
		} catch (Exception ex) {
			TerraCharacter.LOGGER.warn("Invalid character appearance received", ex);
		}
	}

	@SubscribeEvent
	public static void loggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
		if (!event.getConnection().isMemoryConnection() && accepted != event.getConnection())
			event.getConnection().disconnect(ClientProfiles.text("network.required"));
	}

	@SubscribeEvent
	public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
		// Connecting to a new server also emits a null LoggingOut before configuration.
		avatars.clear();
		if (event.getConnection() == accepted) release();
	}
}
