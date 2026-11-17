package com.kclucas.beaconrange.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Rahmen, Reichweite und Angriffsbereich eines Conduits - genau wie Minecraft sie prüft
 * (ConduitBlockEntity.updateShape, applyEffects, getDestroyRangeAABB).
 */
public final class Conduits {

	/** Ab so vielen Rahmenblöcken ist ein Conduit aktiv. */
	public static final int MIN_FRAME = 16;
	/** Voller Rahmen: dann greift der Conduit auch Monster an. */
	public static final int FULL_FRAME = 42;
	/** So weit (in Blöcken um den Conduit herum) sucht er sich Monster zum Angreifen. */
	public static final int ATTACK_RANGE = 8;

	/** Die Rahmenblöcke, wie in ConduitBlockEntity.VALID_BLOCKS (erst ab 26.3 ein Block-Tag). */
	private static final Block[] FRAME_BLOCKS = {Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.DARK_PRISMARINE};

	private Conduits() {
	}

	public static boolean isConduit(Level level, BlockPos pos) {
		return level.getBlockState(pos).is(Blocks.CONDUIT);
	}

	/** Reichweite der Conduit-Kraft: 32 Blöcke bei 16 Rahmenblöcken, je 7 weitere 16 Blöcke mehr, bis 96. */
	public static int range(int frameBlocks) {
		return frameBlocks < MIN_FRAME ? 0 : frameBlocks / 7 * 16;
	}

	public static boolean attacks(int frameBlocks) {
		return frameBlocks >= FULL_FRAME;
	}

	/**
	 * Zahl der Rahmenblöcke wie in Minecraft: 0, solange nicht der ganze 3×3×3-Würfel um den Conduit Wasser ist. Gezählt
	 * werden Prismarin-Blöcke auf den drei Ringen um den Conduit (je 5×5 groß, einer pro Achse).
	 */
	public static int frameBlocks(Level level, BlockPos pos) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					if (!level.isWaterAt(cursor.setWithOffset(pos, x, y, z))) {
						return 0;
					}
				}
			}
		}
		int count = 0;
		for (int x = -2; x <= 2; x++) {
			for (int y = -2; y <= 2; y++) {
				for (int z = -2; z <= 2; z++) {
					if (isFramePosition(x, y, z) && isFrameBlock(level.getBlockState(cursor.setWithOffset(pos, x, y, z)))) {
						count++;
					}
				}
			}
		}
		return count;
	}

	/** Ein Block, der als Rahmen zählt (Prismarin, Prismarinziegel, dunkles Prismarin, Seelaterne). */
	public static boolean isFrameBlock(BlockState state) {
		for (Block block : FRAME_BLOCKS) {
			if (state.is(block)) {
				return true;
			}
		}
		return false;
	}

	/** Die Plätze des Rahmens: der äußere Ring eines 5×5-Quadrats, einmal quer zu jeder Achse durch den Conduit. */
	public static boolean isFramePosition(int x, int y, int z) {
		int ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
		if (ax <= 1 && ay <= 1 && az <= 1) {
			return false;
		}
		return x == 0 && (ay == 2 || az == 2) || y == 0 && (ax == 2 || az == 2) || z == 0 && (ax == 2 || ay == 2);
	}

	/** Der Würfel, in dem ein voller Conduit Monster (im Wasser oder Regen) angreift. */
	public static AABB attackArea(BlockPos pos) {
		return new AABB(pos).inflate(ATTACK_RANGE);
	}
}
