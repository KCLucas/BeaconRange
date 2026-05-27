package com.kclucas.beaconrange.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import com.kclucas.beaconrange.client.mixin.BeaconAccessor;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class BeaconRangeClient implements ClientModInitializer {

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
		// --- ADDED COMMAND REGISTRATION ---
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			registerCommands(dispatcher);
		});

		WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.world == null || client.player == null) return;

			Iterator<BlockPos> it = pinnedBeacons.iterator();
			while (it.hasNext()) {
				BlockPos pos = it.next();
				if (client.world.getBlockEntity(pos) instanceof BeaconBlockEntity beacon) {
					int level = ((BeaconAccessor) beacon).getLevel();
					if (level > 0) {
						renderBeaconBox(context, pos, level);
					}
				} else {
					it.remove();
				}
			}

			HitResult hit = client.crosshairTarget;
			if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
				BlockPos pos = ((BlockHitResult) hit).getBlockPos();
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

	// --- NEW COMMAND METHOD ---
	private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("beaconrange")
				// 1. /beaconrange clear
				.then(literal("clear")
						.executes(context -> {
							int count = pinnedBeacons.size();
							pinnedBeacons.clear();
							context.getSource().sendFeedback(Text.literal("Cleared all " + count + " pinned beacons."));
							return 1;
						})
				)
				// 2. /beaconrange remove [x,y,z]
				.then(literal("remove")
						.then(argument("coords", StringArgumentType.greedyString())
								.suggests((context, builder) -> {
									// This populates the list of beacons in the chat autocomplete
									for (BlockPos pos : pinnedBeacons) {
										builder.suggest(pos.getX() + "," + pos.getY() + "," + pos.getZ());
									}
									return builder.buildFuture();
								})
								.executes(context -> {
									String input = StringArgumentType.getString(context, "coords");

									BlockPos target = null;
									for (BlockPos pos : pinnedBeacons) {
										String check = pos.getX() + "," + pos.getY() + "," + pos.getZ();
										if (check.equals(input)) {
											target = pos;
											break;
										}
									}

									if (target != null) {
										pinnedBeacons.remove(target);
										context.getSource().sendFeedback(Text.literal("Removed beacon: " + input));
									} else {
										context.getSource().sendError(Text.literal("Beacon not found in list."));
									}
									return 1;
								})
						)
				)
		);
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