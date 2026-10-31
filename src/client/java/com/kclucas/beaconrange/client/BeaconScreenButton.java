package com.kclucas.beaconrange.client;

import com.kclucas.beaconrange.client.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Knopf "Bereich anpinnen" über dem Beacon-Fenster, daneben Stufe und Reichweite. Ist über dem Fenster kein Platz,
 * sitzt er rechts daneben.
 */
public final class BeaconScreenButton {

	public static final int WIDTH = 104;
	private static final int HEIGHT = 20;

	/** Welcher Beacon zu welchem offenen Fenster gehört - bleibt beim Neuaufbau (Fenstergröße) erhalten. */
	private static final Map<Screen, BlockPos> BEACONS = new WeakHashMap<>();

	private BeaconScreenButton() {
	}

	public static void afterInit(Minecraft client, Screen screen, int width, int height) {
		if (!(screen instanceof BeaconScreen)) {
			return;
		}
		BlockPos beacon = BEACONS.get(screen);
		if (beacon == null) {
			beacon = BeaconRangeClient.beaconUnderCrosshair(client);
			if (beacon == null) {
				return; // nicht per Klick auf einen Beacon geöffnet - dann wissen wir nicht, welcher es ist
			}
			BEACONS.put(screen, beacon);
		}

		AbstractContainerScreenAccessor window = (AbstractContainerScreenAccessor) screen;
		int left = window.beaconrange$getLeftPos();
		int top = window.beaconrange$getTopPos();
		int right = left + window.beaconrange$getImageWidth();
		int x, y;
		boolean infoLeft;
		if (top >= HEIGHT + 4) {
			x = right - WIDTH;
			y = top - HEIGHT - 2;
			infoLeft = true;
		} else if (width - right >= WIDTH + 8) {
			x = right + 4;
			y = top + 2;
			infoLeft = false;
		} else {
			x = width - WIDTH - 4;
			y = 4;
			infoLeft = true;
		}

		BlockPos pos = beacon;
		Button button = Button.builder(label(client, pos), b -> {
					BeaconRangeClient.togglePin(client, pos);
					b.setMessage(label(client, pos));
				})
				.bounds(x, y, WIDTH, HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("beaconrange.button.tooltip")))
				.build();
		Screens.getWidgets(screen).add(button);

		ScreenEvents.afterForeground(screen).register((s, graphics, mouseX, mouseY, partialTick) -> {
			if (client.level == null) {
				return;
			}
			// "Angepinnt" steht schon auf dem Knopf
			Component info = LookInfoHud.describe(client, pos, Beacons.level(client.level, pos), false);
			int textWidth = client.font.width(info);
			if (infoLeft) {
				graphics.text(client.font, info, button.getX() - 6 - textWidth, button.getY() + 6, 0xFFFFFFFF);
			} else {
				graphics.text(client.font, info, button.getX() + 2, button.getY() + HEIGHT + 5, 0xFFFFFFFF);
			}
		});
	}

	private static Component label(Minecraft client, BlockPos pos) {
		return BeaconRangeClient.getPins().isPinned(client, pos)
				? Component.translatable("beaconrange.button.pinned").withStyle(ChatFormatting.GREEN)
				: Component.translatable("beaconrange.button.pin");
	}
}
