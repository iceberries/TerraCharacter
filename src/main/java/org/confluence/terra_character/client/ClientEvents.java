package org.confluence.terra_character.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import org.confluence.terra_character.TerraCharacter;
import org.confluence.terra_character.api.model.CharacterDifficulty;

import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = TerraCharacter.MODID, value = Dist.CLIENT)
public final class ClientEvents {
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void init(ScreenEvent.Init.Post event) {
		var screen = event.getScreen();
		if (screen instanceof TitleScreen) {
			ClientProfiles.setSelected(null);
			MultiplayerClient.setSelection(null);
			int row = screen.height / 4 + 32;
			for (var listener : List.copyOf(event.getListenersList())) {
				if (!(listener instanceof AbstractWidget widget)) continue;
				String key =
						widget.getMessage().getContents() instanceof TranslatableContents c
								? c.getKey()
								: "";
				if (Set.of("menu.singleplayer", "menu.multiplayer", "menu.online").contains(key))
					event.removeListener(listener);
				else if (widget.getY() >= row + 48 && widget.getY() <= row + 126)
					widget.setY(widget.getY() - 48);
			}
			event.addListener(
					Button.builder(
									ClientProfiles.text("start"),
									b ->
											Minecraft.getInstance()
													.setScreen(
															new CharacterSelectionScreen(screen)))
							.bounds(screen.width / 2 - 100, row, 200, 20)
							.build());
		} else if (screen instanceof SelectWorldScreen && ClientProfiles.getSelected() != null) {
			event.addListener(
					new WorldToolsButton(
							screen.width - 28,
							6,
							() ->
									Minecraft.getInstance()
											.setScreen(new CharacterWorldScreen(screen))));
		} else if (screen instanceof CreateWorldScreen creation
				&& ClientProfiles.getSelected() != null) {
			event.addListener(
					Button.builder(
									ClientProfiles.text("world.options"),
									b ->
											Minecraft.getInstance()
													.setScreen(
															new CharacterWorldOptionsScreen(
																	creation)))
							.bounds(screen.width - 134, screen.height - 53, 126, 20)
							.build());
		} else if (screen instanceof PauseScreen && ClientState.profile() != null) {
			var profile = ClientState.profile();
			if (profile.difficulty() == CharacterDifficulty.JOURNEY)
				event.addListener(
						Button.builder(
										ClientProfiles.text("journey"),
										b ->
												Minecraft.getInstance()
														.setScreen(new JourneyScreen(screen)))
								.bounds(8, 8, 120, 20)
								.build());
			event.addListener(
					Button.builder(
									ClientProfiles.text("character.info"),
									b ->
											Minecraft.getInstance()
													.setScreen(
															new NoticeScreen(
																	screen,
																	Component.literal(
																			profile.name()),
																	ClientProfiles.difficulty(
																			profile.difficulty()))))
							.bounds(8, 32, 120, 20)
							.build());
		}
	}

	@SubscribeEvent
	public static void opening(ScreenEvent.Opening event) {
		if (event.getNewScreen() instanceof DeathScreen && ClientState.hardcore())
			event.setNewScreen(new CharacterDeathScreen());
	}
}
