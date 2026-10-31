package com.kclucas.beaconrange.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Ein Bereich, der gerade gezeichnet wird.
 *
 * @param box     der Bereich, wie Minecraft ihn prüft
 * @param floor   Unterkante zum Zeichnen: nicht unter dem Weltboden
 * @param ceiling Oberkante zum Zeichnen: nicht über der Bauhöhe (der Bereich selbst reicht noch weiter)
 * @param color   RGB ohne Alpha
 * @param pinned  angepinnt (sonst nur angeschaut)
 */
public record BeaconArea(BlockPos pos, int level, AABB box, double floor, double ceiling, int color, boolean pinned) {

	/** Ab hier blenden die Wände nach oben aus: Oberkante des Beacon-Blocks. */
	public double fadeStart() {
		return this.pos.getY() + 1.0;
	}

	/** Über diese Höhe blenden die Wände aus: kurz genug, dass man den Verlauf auch aus der Nähe sieht. */
	public double fadeHeight() {
		return 24.0;
	}
}
