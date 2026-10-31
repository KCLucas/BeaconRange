package com.kclucas.beaconrange.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** /beaconrange list | remove x y z | clear */
public final class BeaconRangeCommands {

	private BeaconRangeCommands() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("beaconrange")
				.executes(context -> list(context.getSource()))
				.then(literal("list").executes(context -> list(context.getSource())))
				.then(literal("clear").executes(context -> clear(context.getSource())))
				.then(literal("remove")
						.then(argument("pos", StringArgumentType.greedyString())
								.suggests((context, builder) -> {
									for (PinStore.Pin pin : BeaconRangeClient.getPins().current(Minecraft.getInstance())) {
										builder.suggest(text(pin.pos()));
									}
									return builder.buildFuture();
								})
								.executes(context -> remove(context.getSource(),
										StringArgumentType.getString(context, "pos"))))));
	}

	private static int list(FabricClientCommandSource source) {
		Minecraft client = source.getClient();
		PinStore pins = BeaconRangeClient.getPins();
		List<PinStore.Pin> here = pins.current(client);
		int elsewhere = pins.countWorld(client) - here.size();
		if (here.isEmpty()) {
			source.sendFeedback(BeaconRangeClient.prefix().append(
					Component.translatable("beaconrange.command.list.empty").withStyle(ChatFormatting.GRAY)));
		} else {
			source.sendFeedback(BeaconRangeClient.prefix().append(
					Component.translatable("beaconrange.command.list.header", here.size()).withStyle(ChatFormatting.GOLD)));
			Vec3 player = source.getPosition();
			List<PinStore.Pin> nearestFirst = here.stream()
					.sorted(Comparator.comparingDouble(pin -> pin.pos().getCenter().distanceToSqr(player))).toList();
			for (PinStore.Pin pin : nearestFirst) {
				source.sendFeedback(entry(pin, player));
			}
		}
		if (elsewhere > 0) {
			source.sendFeedback(Component.translatable("beaconrange.command.list.elsewhere", elsewhere)
					.withStyle(ChatFormatting.DARK_GRAY));
		}
		return here.size();
	}

	/** " ■ Stufe 4 · 50 Blöcke   12 64 -30   23 Blöcke entfernt [✖]" */
	private static Component entry(PinStore.Pin pin, Vec3 player) {
		BlockPos pos = pin.pos();
		MutableComponent line = Component.literal(" ■ ");
		if (pin.level() > 0) {
			int color = BeaconRangeClient.getConfig().levelColor(pin.level()).rgb;
			line.withColor(color).append(Component.translatable("beaconrange.info", pin.level(), Beacons.range(pin.level()))
					.withColor(color));
		} else {
			line.withStyle(ChatFormatting.GRAY).append(Component.translatable("beaconrange.info.inactive"));
		}
		int distance = Mth.floor(Math.sqrt(pos.getCenter().distanceToSqr(player)));
		line.append(Component.literal("   " + text(pos)).withStyle(ChatFormatting.AQUA))
				.append(Component.literal("   ").append(Component.translatable("beaconrange.command.list.distance", distance))
						.withStyle(ChatFormatting.DARK_GRAY));
		MutableComponent unpin = Component.literal(" [✖]").withStyle(style -> style
				.withColor(ChatFormatting.RED)
				.withClickEvent(new ClickEvent.RunCommand("/beaconrange remove " + text(pos)))
				.withHoverEvent(new HoverEvent.ShowText(
						Component.translatable("beaconrange.command.list.unpin").withStyle(ChatFormatting.RED))));
		return line.append(unpin);
	}

	private static int remove(FabricClientCommandSource source, String input) {
		BlockPos pos = parse(input);
		if (pos == null) {
			source.sendError(Component.translatable("beaconrange.command.remove.invalid", input));
			return 0;
		}
		if (!BeaconRangeClient.getPins().remove(source.getClient(), pos)) {
			source.sendError(Component.translatable("beaconrange.command.remove.missing", text(pos)));
			return 0;
		}
		source.sendFeedback(BeaconRangeClient.prefix().append(
				Component.translatable("beaconrange.pin.removed", text(pos)).withStyle(ChatFormatting.WHITE)));
		return 1;
	}

	private static int clear(FabricClientCommandSource source) {
		int count = BeaconRangeClient.getPins().clearWorld(source.getClient());
		source.sendFeedback(BeaconRangeClient.prefix().append(
				Component.translatable("beaconrange.command.clear", count).withStyle(ChatFormatting.WHITE)));
		return count;
	}

	/** "12 64 -30" oder "12,64,-30" (so stand es in Version 1). */
	static @Nullable BlockPos parse(String input) {
		String[] parts = input.trim().split("[\\s,]+");
		if (parts.length != 3) {
			return null;
		}
		try {
			return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	static String text(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}
}
