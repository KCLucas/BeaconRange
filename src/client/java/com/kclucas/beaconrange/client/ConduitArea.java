package com.kclucas.beaconrange.client;

import net.minecraft.core.BlockPos;

/**
 * Ein Conduit, dessen Bereich gerade gezeichnet wird: eine Kugel um die Mitte des Conduits. Minecraft gibt die
 * Conduit-Kraft Spielern, deren Block näher als {@link #range} am Conduit liegt (und die im Wasser oder Regen sind).
 *
 * @param frameBlocks Rahmenblöcke (16 bis 42)
 * @param range       Radius der Kugel in Blöcken (32 bis 96)
 * @param color       RGB ohne Alpha
 * @param pinned      angepinnt (sonst nur angeschaut)
 */
public record ConduitArea(BlockPos pos, int frameBlocks, int range, int color, boolean pinned) {

	public double centerX() {
		return this.pos.getX() + 0.5;
	}

	public double centerY() {
		return this.pos.getY() + 0.5;
	}

	public double centerZ() {
		return this.pos.getZ() + 0.5;
	}

	/** Voller Rahmen: greift Monster in einem Würfel von 8 Blöcken um den Conduit an. */
	public boolean attacks() {
		return Conduits.attacks(this.frameBlocks);
	}
}
