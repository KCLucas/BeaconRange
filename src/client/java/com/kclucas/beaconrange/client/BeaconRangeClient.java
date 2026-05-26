package com.kclucas.beaconrange.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import com.kclucas.beaconrange.client.mixin.BeaconAccessor;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class BeaconRangeClient implements ClientModInitializer {

	// Now storing multiple positions
	public static final Set<BlockPos> pinnedBeacons = new HashSet<>();

	public static void togglePin(BlockPos pos) {
		if (pinnedBeacons.contains(pos)) {
			pinnedBeacons.remove(pos);
		} else {
			pinnedBeacons.add(pos);
		}
	}

	@Override
	public void onInitializeClient() {
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.world == null || client.player == null) return;

			// 1. Render all pinned beacons
			Iterator<BlockPos> it = pinnedBeacons.iterator();
			while (it.hasNext()) {
				BlockPos pos = it.next();
				if (client.world.getBlockEntity(pos) instanceof BeaconBlockEntity beacon) {
					int level = ((BeaconAccessor) beacon).getLevel();
					if (level > 0) {
						renderBeaconBox(context, pos, level);
					}
				} else {
					// Remove if beacon no longer exists (broken)
					it.remove();
				}
			}

			// 2. Render Crosshair Beacon (only if NOT already pinned)
			HitResult hit = client.crosshairTarget;
			if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
				BlockPos pos = ((BlockHitResult) hit).getBlockPos();

				// Only render crosshair box if it's not already in the pinned set
				if (!pinnedBeacons.contains(pos)) {
					if (client.world.getBlockEntity(pos) instanceof BeaconBlockEntity beacon) {
						int level = ((BeaconAccessor) beacon).getLevel();
						if (level > 0) {
							renderBeaconBox(context, pos, level);
						}
					}
				}
			}
		});
	}

	private void renderBeaconBox(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context, BlockPos pos, int level) {
		int range = 10 + level * 10;
		Box box = new Box(
				pos.getX() - range, pos.getY() - range, pos.getZ() - range,
				pos.getX() + range + 1, pos.getY() + range + 1, pos.getZ() + range + 1
		);
		BeaconRangeRenderer.render(context, box, level);
	}
}