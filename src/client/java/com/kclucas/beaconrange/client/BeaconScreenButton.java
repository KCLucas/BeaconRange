package com.kclucas.beaconrange.client;

import com.kclucas.beaconrange.client.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Über dem Beacon-Fenster: Knopf "Bereich anpinnen", links daneben ein Schloss-Knopf (Pins automatisch lösen an/aus)
 * und davor Stufe und Reichweite. Ist über dem Fenster kein Platz, sitzen sie rechts daneben.
 */
public final class BeaconScreenButton {

	public static final int WIDTH = 104;
	/** Der Schloss-Knopf ist quadratisch. */
	public static final int LOCK_WIDTH = 20;
	private static final int HEIGHT = 20;

	/** Offenes Schloss: Pins werden gelöst, sobald der Beacon weg ist (Standard). */
	private static final String[] UNLOCKED = {
			"..###..",
			".#...#.",
			".#.....",
			".#.....",
			"#######",
			"###.###",
			"###.###",
			"#######",
	};
	/** Geschlossenes Schloss: Pins bleiben, auch wenn der Beacon scheinbar fehlt. */
	private static final String[] LOCKED = {
			"..###..",
			".#...#.",
			".#...#.",
			".#...#.",
			"#######",
			"###.###",
			"###.###",
			"#######",
	};

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
			beacon = BeaconRangeClient.openedBeacon(client);
			if (beacon == null) {
				return; // nicht per Klick auf einen Beacon geöffnet - dann wissen wir nicht, welcher es ist
			}
			BEACONS.put(screen, beacon);
		}

		AbstractContainerScreenAccessor window = (AbstractContainerScreenAccessor) screen;
		int left = window.beaconrange$getLeftPos();
		int top = window.beaconrange$getTopPos();
		int right = left + window.beaconrange$getImageWidth();
		int x, y, lockX, lockY;
		boolean infoLeft;
		if (top >= HEIGHT + 4) {
			x = right - WIDTH;
			y = top - HEIGHT - 2;
			lockX = x - LOCK_WIDTH - 2;
			lockY = y;
			infoLeft = true;
		} else if (width - right >= WIDTH + LOCK_WIDTH + 10) {
			x = right + 4;
			y = top + 2;
			lockX = x + WIDTH + 2;
			lockY = y;
			infoLeft = false;
		} else {
			x = width - WIDTH - 4;
			y = 4;
			lockX = x - LOCK_WIDTH - 2;
			lockY = y;
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
		Screens.getButtons(screen).add(button);

		// ohne Beschriftung: das Schloss wird darübergezeichnet, der Tooltip erklärt es
		Button lock = Button.builder(Component.empty(), b -> {
					BeaconRangeClient.setAutoUnpin(!BeaconRangeClient.getConfig().autoUnpin);
					b.setTooltip(lockTooltip());
					if (client.player != null) {
						client.player.displayClientMessage(Component.translatable(BeaconRangeClient.getConfig().autoUnpin
								? "beaconrange.auto_unpin.on" : "beaconrange.auto_unpin.off"), true);
					}
				})
				.bounds(lockX, lockY, LOCK_WIDTH, HEIGHT)
				.tooltip(lockTooltip())
				.build();
		Screens.getButtons(screen).add(lock);

		ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, partialTick) -> {
			drawLock(graphics, lock);
			if (client.level == null) {
				return;
			}
			// "Angepinnt" steht schon auf dem Knopf
			Component info = LookInfoHud.describe(client, pos, Beacons.level(client.level, pos), false);
			int textWidth = client.font.width(info);
			if (infoLeft) {
				graphics.drawString(client.font, info, lock.getX() - 6 - textWidth, button.getY() + 6, 0xFFFFFFFF);
			} else {
				graphics.drawString(client.font, info, button.getX() + 2, button.getY() + HEIGHT + 5, 0xFFFFFFFF);
			}
		});
	}

	/** Das Schloss mitten auf den Knopf - als Pixelgrafik, das Zeichen dafür kennt die Minecraft-Schrift nicht. */
	private static void drawLock(GuiGraphics graphics, Button lock) {
		boolean keep = !BeaconRangeClient.getConfig().autoUnpin;
		String[] icon = keep ? LOCKED : UNLOCKED;
		int color = !lock.active ? 0xFF6B6F75 : keep ? 0xFFFFD24D : 0xFFE6E6E6;
		int x0 = lock.getX() + (LOCK_WIDTH - icon[0].length()) / 2;
		int y0 = lock.getY() + (HEIGHT - icon.length) / 2;
		for (int row = 0; row < icon.length; row++) {
			for (int col = 0; col < icon[row].length(); col++) {
				if (icon[row].charAt(col) == '#') {
					graphics.fill(x0 + col, y0 + row, x0 + col + 1, y0 + row + 1, color);
				}
			}
		}
	}

	private static Tooltip lockTooltip() {
		boolean on = BeaconRangeClient.getConfig().autoUnpin;
		return Tooltip.create(Component.translatable(on ? "beaconrange.button.lock.on" : "beaconrange.button.lock.off")
				.withStyle(on ? ChatFormatting.WHITE : ChatFormatting.GOLD)
				.append("\n")
				.append(Component.translatable(on ? "beaconrange.button.lock.on.desc" : "beaconrange.button.lock.off.desc")
						.withStyle(ChatFormatting.GRAY)));
	}

	private static Component label(Minecraft client, BlockPos pos) {
		return BeaconRangeClient.getPins().isPinned(client, pos)
				? Component.translatable("beaconrange.button.pinned").withStyle(ChatFormatting.GREEN)
				: Component.translatable("beaconrange.button.pin");
	}
}
