package com.kclucas.beaconrange.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Unter dem Fadenkreuz: Stufe und Reichweite des Beacons, auf den man schaut. */
public final class LookInfoHud {

	private LookInfoHud() {
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		BlockPos pos = BeaconRangeClient.getLookedAt();
		if (pos == null || !BeaconRangeClient.getConfig().lookInfo || BeaconRangeClient.isHidden()
				|| client.gui.screen() != null) {
			return;
		}
		Component text = describe(client, pos, BeaconRangeClient.getLookedAtLevel(), true);
		int x = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2 + 14;
		graphics.centeredText(client.font, text, x, y, 0xFFFFFFFF);
	}

	/** "Stufe 4 · 50 Blöcke · Angepinnt" in der Farbe der Stufe - auch neben dem Knopf im Beacon-Fenster. */
	public static MutableComponent describe(Minecraft client, BlockPos pos, int level, boolean showPinned) {
		if (level <= 0) {
			return Component.translatable("beaconrange.info.inactive").withStyle(ChatFormatting.GRAY);
		}
		int color = BeaconRangeClient.getConfig().levelColor(level).rgb;
		MutableComponent text = Component.translatable("beaconrange.info", level, Beacons.range(level)).withColor(color);
		if (showPinned && BeaconRangeClient.getPins().isPinned(client, pos)) {
			text.append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
					.append(Component.translatable("beaconrange.info.pinned").withStyle(ChatFormatting.WHITE));
		}
		return text;
	}
}
