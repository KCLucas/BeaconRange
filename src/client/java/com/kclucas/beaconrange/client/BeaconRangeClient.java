package com.kclucas.beaconrange.client;

import com.kclucas.beaconrange.client.onboarding.Onboarding;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BeaconRangeClient implements ClientModInitializer {

	public static final String MOD_ID = "beaconrange";

	/** So weit reicht "Anschauen": ein Beacon in Sichtweite zeigt seinen Bereich, nicht nur in Griffweite. */
	public static final double LOOK_DISTANCE = 96.0;

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private static KeyMapping pinKey;
	private static KeyMapping toggleKey;
	private static KeyMapping settingsKey;
	private static BeaconRangeConfig config = new BeaconRangeConfig();
	private static final PinStore PINS = new PinStore();
	/** Alle Bereiche ausgeblendet (Taste) - gilt bis zum Neustart. */
	private static boolean hidden = false;
	private static @Nullable BlockPos lookedAt;
	private static int lookedAtLevel;
	private static List<BeaconArea> visible = List.of();

	public static BeaconRangeConfig getConfig() {
		return config;
	}

	public static PinStore getPins() {
		return PINS;
	}

	public static boolean isHidden() {
		return hidden;
	}

	/** Der Beacon unter dem Fadenkreuz (bis {@link #LOOK_DISTANCE} Blöcke weit), oder null. */
	public static @Nullable BlockPos getLookedAt() {
		return lookedAt;
	}

	public static int getLookedAtLevel() {
		return lookedAtLevel;
	}

	/** Die Bereiche, die gerade gezeichnet werden. */
	public static List<BeaconArea> getVisible() {
		return visible;
	}

	@Override
	public void onInitializeClient() {
		config = BeaconRangeConfig.load();
		PINS.load();

		pinKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.beaconrange.pin", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.beaconrange.toggle", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
		settingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.beaconrange.settings", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(BeaconRangeClient::onClientTick);
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> RangeRenderer.render(context, visible));
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR,
				Identifier.fromNamespaceAndPath(MOD_ID, "look_info"), LookInfoHud::render);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				BeaconRangeCommands.register(dispatcher));
		ScreenEvents.AFTER_INIT.register(BeaconScreenButton::afterInit);

		Onboarding.register(createTutorial());
	}

	/** Die kurze Tour beim ersten Start in einem Profil, dazu was in welcher Version neu ist (neueste zuerst). */
	private static Onboarding.Tutorial createTutorial() {
		return new Onboarding.Tutorial(MOD_ID,
				Component.literal("Beacon Range"),
				Component.translatable("beaconrange.tutorial.description"),
				Identifier.fromNamespaceAndPath(MOD_ID, "icon.png"),
				List.of(
						new Onboarding.Page(
								Component.translatable("beaconrange.tutorial.look.title"),
								Component.translatable("beaconrange.tutorial.look.text"),
								Identifier.fromNamespaceAndPath(MOD_ID, "textures/tutorial/look.png")),
						new Onboarding.Page(
								Component.translatable("beaconrange.tutorial.pin.title"),
								Component.translatable("beaconrange.tutorial.pin.text"),
								Identifier.fromNamespaceAndPath(MOD_ID, "textures/tutorial/pin.png")),
						new Onboarding.Page(
								Component.translatable("beaconrange.tutorial.settings.title"),
								Component.translatable("beaconrange.tutorial.settings.text"),
								Identifier.fromNamespaceAndPath(MOD_ID, "textures/tutorial/settings.png"))),
				List.of(
						new Onboarding.Release("2.0.0", List.of(
								Component.translatable("beaconrange.news.2_0_0.range"),
								Component.translatable("beaconrange.news.2_0_0.look"),
								Component.translatable("beaconrange.news.2_0_0.saved"),
								Component.translatable("beaconrange.news.2_0_0.button"),
								Component.translatable("beaconrange.news.2_0_0.settings")))));
	}

	private static void onClientTick(Minecraft client) {
		while (settingsKey.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(new BeaconRangeConfigScreen(null));
			}
		}

		ClientLevel level = client.level;
		if (level == null || client.player == null) {
			lookedAt = null;
			visible = List.of();
			return;
		}

		while (toggleKey.consumeClick()) {
			hidden = !hidden;
			client.player.displayClientMessage(Component.translatable(
					hidden ? "beaconrange.toggle.hidden" : "beaconrange.toggle.shown"), true);
		}

		lookedAt = findLookedAtBeacon(client, level);
		lookedAtLevel = lookedAt == null ? 0 : Beacons.level(level, lookedAt);

		while (pinKey.consumeClick()) {
			if (lookedAt == null) {
				client.player.displayClientMessage(Component.translatable("beaconrange.pin.no_beacon"), true);
			} else {
				togglePin(client, lookedAt);
			}
		}

		visible = hidden ? List.of() : collect(client, level);
	}

	private static @Nullable BlockPos findLookedAtBeacon(Minecraft client, ClientLevel level) {
		if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
				&& Beacons.isBeacon(level, hit.getBlockPos())) {
			return hit.getBlockPos().immutable();
		}
		Entity camera = client.getCameraEntity();
		if (camera == null) {
			return null;
		}
		if (camera.pick(LOOK_DISTANCE, 1.0F, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
				&& Beacons.isBeacon(level, hit.getBlockPos())) {
			return hit.getBlockPos().immutable();
		}
		return null;
	}

	/** Der Beacon, auf den man beim Öffnen seines Fensters geklickt hat (nur in Griffweite). */
	public static @Nullable BlockPos beaconUnderCrosshair(Minecraft client) {
		if (client.level != null && client.hitResult instanceof BlockHitResult hit
				&& hit.getType() == HitResult.Type.BLOCK && Beacons.isBeacon(client.level, hit.getBlockPos())) {
			return hit.getBlockPos().immutable();
		}
		return null;
	}

	public static void togglePin(Minecraft client, BlockPos pos) {
		if (client.level == null || client.player == null) {
			return;
		}
		if (PinStore.worldKey(client) == null) {
			client.player.displayClientMessage(Component.translatable("beaconrange.pin.unavailable"), true);
			return;
		}
		boolean pinned = PINS.toggle(client, pos, Beacons.level(client.level, pos));
		client.player.displayClientMessage(Component.translatable(
				pinned ? "beaconrange.pin.added" : "beaconrange.pin.removed", coordinates(pos)), true);
		visible = hidden ? List.of() : collect(client, client.level);
	}

	/** Angepinnte Beacons dieser Dimension plus der angeschaute - mit aktueller Stufe und Farbe. */
	private static List<BeaconArea> collect(Minecraft client, ClientLevel level) {
		List<BeaconArea> areas = new ArrayList<>();
		for (PinStore.Pin pin : PINS.current(client)) {
			BlockPos pos = pin.pos();
			int beaconLevel = pin.level();
			if (level.isLoaded(pos)) {
				// Nur wenn der Chunk geladen ist, lässt sich sagen, ob der Beacon abgebaut wurde.
				if (!Beacons.isBeacon(level, pos)) {
					PINS.remove(client, pos);
					if (client.player != null) {
						client.player.displayClientMessage(prefix().append(
								Component.translatable("beaconrange.pin.gone", coordinates(pos))), false);
					}
					continue;
				}
				beaconLevel = Beacons.level(level, pos);
				if (beaconLevel > 0) {
					PINS.updateLevel(client, pos, beaconLevel);
				}
			}
			if (beaconLevel > 0) {
				areas.add(area(level, pos, beaconLevel, true));
			}
		}
		if (config.showOnLook && lookedAt != null && lookedAtLevel > 0 && !PINS.isPinned(client, lookedAt)) {
			areas.add(area(level, lookedAt, lookedAtLevel, false));
		}
		return List.copyOf(areas);
	}

	private static BeaconArea area(ClientLevel level, BlockPos pos, int beaconLevel, boolean pinned) {
		int color = config.levelColor(beaconLevel).rgb;
		if (config.colorMode == BeaconRangeConfig.ColorMode.BEAM && level.isLoaded(pos)) {
			Integer beam = Beacons.beamColor(level, pos);
			if (beam != null) {
				color = beam;
			}
		}
		AABB box = Beacons.area(level, pos, beaconLevel);
		return new BeaconArea(pos, beaconLevel, box, Math.max(box.minY, level.getMinY()),
				Math.min(box.maxY, level.getMaxY() + 1), color, pinned);
	}

	public static Component coordinates(BlockPos pos) {
		return Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
	}

	/** "[Beacon Range] " vor Chat-Meldungen. */
	public static MutableComponent prefix() {
		return Component.literal("[Beacon Range] ").withStyle(ChatFormatting.AQUA);
	}
}
