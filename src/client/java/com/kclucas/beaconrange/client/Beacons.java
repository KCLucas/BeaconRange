package com.kclucas.beaconrange.client;

import com.kclucas.beaconrange.client.mixin.BeaconBlockEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Reichweite und Stufe eines Beacons - genau wie Minecraft sie berechnet. */
public final class Beacons {

	public static final int MAX_LEVEL = 4;

	private Beacons() {
	}

	/** Seitlicher Abstand vom Beacon, bis zu dem die Effekte reichen: 20, 30, 40 oder 50 Blöcke. */
	public static int range(int level) {
		return 10 + 10 * level;
	}

	/**
	 * Der Bereich, in dem Spieler die Effekte bekommen. Minecraft vergrößert den Beacon-Block um die Reichweite und
	 * zieht ihn dann um die ganze Welthöhe nach oben: nach unten reicht er nur {@link #range} Blöcke, nach oben bis
	 * über die Bauhöhe hinaus.
	 */
	public static AABB area(LevelHeightAccessor world, BlockPos pos, int level) {
		return new AABB(pos).inflate(range(level)).expandTowards(0, world.getHeight(), 0);
	}

	public static boolean isBeacon(Level level, BlockPos pos) {
		return level.getBlockState(pos).is(Blocks.BEACON);
	}

	/**
	 * Aktuelle Stufe (0 = inaktiv). Der Beacon rechnet sie nur alle 4 Sekunden neu aus und schickt sie beim Laden des
	 * Chunks nicht mit - bis dahin steht dort 0. Dann zählt die Pyramide darunter, wie Minecraft sie prüft. Ohne Strahl
	 * (ein Block darüber hält ihn auf) wirkt ein Beacon gar nicht.
	 */
	public static int level(Level level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof BeaconBlockEntity beacon) {
			int known = ((BeaconBlockEntityAccessor) beacon).beaconrange$getLevels();
			if (known > 0) {
				// Die Stufe bleibt stehen, wenn der Strahl später verdeckt wird - dann wirkt er trotzdem nicht mehr.
				return beacon.getBeamSections().isEmpty() ? 0 : known;
			}
		}
		return beamBlocked(level, pos) ? 0 : pyramidLevel(level, pos);
	}

	/**
	 * Wie Minecraft den Strahl prüft: jeder Block über dem Beacon bis zur Oberfläche, der kein Licht durchlässt, hält
	 * ihn auf - außer Grundgestein und Buntglas (färbt ihn nur).
	 */
	static boolean beamBlocked(Level level, BlockPos pos) {
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = pos.getY() + 1; y <= top; y++) {
			BlockState state = level.getBlockState(cursor.set(pos.getX(), y, pos.getZ()));
			if (!(state.getBlock() instanceof BeaconBeamBlock) && state.getLightBlock() >= 15 && !state.is(Blocks.BEDROCK)) {
				return true;
			}
		}
		return false;
	}

	/** Stufe i braucht unter dem Beacon eine volle Lage von (2i+1)×(2i+1) Beacon-Basisblöcken (Eisen, Gold, ...). */
	static int pyramidLevel(Level level, BlockPos pos) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int result = 0;
		for (int i = 1; i <= MAX_LEVEL; i++) {
			int y = pos.getY() - i;
			if (y < level.getMinY()) {
				break;
			}
			for (int x = pos.getX() - i; x <= pos.getX() + i; x++) {
				for (int z = pos.getZ() - i; z <= pos.getZ() + i; z++) {
					if (!level.getBlockState(cursor.set(x, y, z)).is(BlockTags.BEACON_BASE_BLOCKS)) {
						return result;
					}
				}
			}
			result = i;
		}
		return result;
	}

	/** Farbe des Strahls oben am Himmel (gefärbt durch Glas darüber) als RGB, oder null ohne Strahl. */
	public static @Nullable Integer beamColor(Level level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof BeaconBlockEntity beacon) {
			List<BeaconBeamOwner.Section> sections = beacon.getBeamSections();
			if (!sections.isEmpty()) {
				return sections.getLast().getColor() & 0xFFFFFF;
			}
		}
		return null;
	}
}
