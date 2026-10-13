package org.confluence.terra_character.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;

import org.confluence.terra_character.api.session.LaunchSelection;
import org.confluence.terra_character.runtime.CharacterSession;
import org.confluence.terra_character.service.WorldIndex;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Keep the native (and Confluence-enhanced) world UI; bind only at the launch boundary. */
public final class WorldLaunchCoordinator {
	private static boolean launching;

	private WorldLaunchCoordinator() {}

	public static void open(String folder, Runnable failure) {
		if (launching) return;
		var mc = Minecraft.getInstance();
		var back = mc.screen;
		var character = ClientProfiles.getSelected();
		if (character == null) {
			ClientProfiles.error(back, new IOException("Select a character first"));
			return;
		}
		// Capture mutable client state before entering the worker pool.
		var store = ClientProfiles.store();
		var saves = ClientProfiles.saves();
		launching = true;
		mc.forceSetScreen(new GenericMessageScreen(ClientProfiles.text("checkpoint")));
		CompletableFuture.runAsync(
						() -> {
							try {
								var target =
										WorldIndex.scan(saves).stream()
												.filter(e -> e.folder().equals(folder))
												.findFirst()
												.orElseThrow(
														() -> new IOException("World not found"));
								if (target.profile() == null) throw new IOException(target.error());
								if (target.duplicate())
									throw new IOException(
											"Duplicate world identity: use world tools to assign a"
													+ " new identity");
								if (target.hardcore())
									throw new IOException(
											"Import this hardcore character using world tools, then"
													+ " create a non-hardcore world");
								var profile =
										store.find(character)
												.orElseThrow(
														() ->
																new IOException(
																		"Character is missing"));
								new LaunchSelection(profile, target.profile());
								CharacterSession.arm(
										store, character, target.directory(), target.profile());
							} catch (IOException ex) {
								throw new CompletionException(ex);
							}
						},
						Util.ioPool())
				.whenComplete(
						(unused, error) ->
								mc.tell(
										() -> {
											launching = false;
											if (error != null) {
												ClientProfiles.error(
														back,
														error.getCause() instanceof Exception ex
																? ex
																: new IOException(error));
												return;
											}
											mc.createWorldOpenFlows()
													.openWorld(
															folder,
															() -> {
																try {
																	CharacterSession
																			.cancelPending();
																} catch (IOException ex) {
																	ClientProfiles.error(back, ex);
																	return;
																}
																failure.run();
															});
										}));
	}
}
