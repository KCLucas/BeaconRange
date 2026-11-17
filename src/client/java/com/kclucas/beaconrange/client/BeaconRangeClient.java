package com.kclucas.beaconrange.client;

import com.kclucas.beaconrange.client.onboarding.Onboarding;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	private static PinStore.Kind lookedAtKind = PinStore.Kind.BEACON;
	/** Stufe des angeschauten Beacons bzw. Rahmenblöcke des angeschauten Conduits. */
	private static int lookedAtLevel;
	private static List<BeaconArea> visible = List.of();
	private static List<ConduitArea> visibleConduits = List.of();
	/**
	 * Angepinnte Blöcke, die wir in dieser Welt gesehen haben, seit ihr Chunk geladen ist (mit dem Spieltick des ersten
	 * Blicks). Nur was vor unseren Augen verschwindet, ist wirklich abgebaut - Anti-Xray schickt beim Laden eines
	 * Chunks oft Tiefenschiefer statt des Beacons und deckt ihn erst später auf.
	 */
	private static final Map<BlockPos, Long> SEEN = new HashMap<>();
	private static @Nullable ClientLevel seenLevel;
	/** So nah muss man sein, damit ein verschwundener Pin gelöst wird - weiter weg verstecken manche Server Blöcke. */
	private static final double UNPIN_DISTANCE = 12.0;
	/** So lange (Ticks) muss ein Pin inaktiv sein, bevor er als inaktiv gemerkt wird (der Beacon rechnet alle 80 Ticks). */
	private static final long INACTIVE_TICKS = 100;
	/** Der Beacon, den man zuletzt angeklickt hat - auf Servern öffnet sich sein Fenster erst etwas später. */
	private static @Nullable BlockPos usedBeacon;
	private static long usedBeaconTime;

	public static BeaconRangeConfig getConfig() {
		return config;
	}

	public static PinStore getPins() {
		return PINS;
	}

	public static boolean isHidden() {
		return hidden;
	}

	/** Der Beacon oder Conduit unter dem Fadenkreuz (bis {@link #LOOK_DISTANCE} Blöcke weit), oder null. */
	public static @Nullable BlockPos getLookedAt() {
		return lookedAt;
	}

	public static PinStore.Kind getLookedAtKind() {
		return lookedAtKind;
	}

	/** Beacon: Stufe (0 = inaktiv). Conduit: Zahl der Rahmenblöcke. */
	public static int getLookedAtLevel() {
		return lookedAtLevel;
	}

	/** Die Beacon-Bereiche, die gerade gezeichnet werden. */
	public static List<BeaconArea> getVisible() {
		return visible;
	}

	/** Die Conduit-Kugeln, die gerade gezeichnet werden. */
	public static List<ConduitArea> getVisibleConduits() {
		return visibleConduits;
	}

	@Override
	public void onInitializeClient() {
		config = BeaconRangeConfig.load();
		PINS.load();

		pinKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.beaconrange.pin", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.beaconrange.toggle", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
		settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.beaconrange.settings", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(BeaconRangeClient::onClientTick);
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> RangeRenderer.render(context, visible, visibleConduits));
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR,
				Identifier.fromNamespaceAndPath(MOD_ID, "look_info"), LookInfoHud::render);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				BeaconRangeCommands.register(dispatcher));
		ScreenEvents.AFTER_INIT.register(BeaconScreenButton::afterInit);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() && Beacons.isBeacon(world, hit.getBlockPos())) {
				usedBeacon = hit.getBlockPos().immutable();
				usedBeaconTime = world.getGameTime();
			}
			return InteractionResult.PASS;
		});

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
								Component.translatable("beaconrange.tutorial.conduit.title"),
								Component.translatable("beaconrange.tutorial.conduit.text"),
								Identifier.fromNamespaceAndPath(MOD_ID, "textures/tutorial/conduit.png")),
						new Onboarding.Page(
								Component.translatable("beaconrange.tutorial.settings.title"),
								Component.translatable("beaconrange.tutorial.settings.text"),
								Identifier.fromNamespaceAndPath(MOD_ID, "textures/tutorial/settings.png"))),
				List.of(
						new Onboarding.Release("2.1.0", List.of(
								Component.translatable("beaconrange.news.2_1_0.conduit"),
								Component.translatable("beaconrange.news.2_1_0.keep"),
								Component.translatable("beaconrange.news.2_1_0.fixes"))),
						new Onboarding.Release("2.0.1", List.of(
								Component.translatable("beaconrange.news.2_0_1.edges"))),
						new Onboarding.Release("2.0.0", List.of(
								Component.translatable("beaconrange.news.2_0_0.range"),
								Component.translatable("beaconrange.news.2_0_0.look"),
								Component.translatable("beaconrange.news.2_0_0.saved"),
								Component.translatable("beaconrange.news.2_0_0.button"),
								Component.translatable("beaconrange.news.2_0_0.settings")))));
	}

	private static void onClientTick(Minecraft client) {
		while (settingsKey.consumeClick()) {
			if (client.gui.screen() == null) {
				client.gui.setScreen(new BeaconRangeConfigScreen(null));
			}
		}

		ClientLevel level = client.level;
		if (level == null || client.player == null) {
			lookedAt = null;
			visible = List.of();
			visibleConduits = List.of();
			return;
		}

		while (toggleKey.consumeClick()) {
			hidden = !hidden;
			client.player.sendOverlayMessage(Component.translatable(
					hidden ? "beaconrange.toggle.hidden" : "beaconrange.toggle.shown"));
		}

		lookedAt = findLookedAt(client, level);
		lookedAtKind = lookedAt != null && Conduits.isConduit(level, lookedAt) ? PinStore.Kind.CONDUIT : PinStore.Kind.BEACON;
		lookedAtLevel = lookedAt == null ? 0 : currentLevel(level, lookedAt, lookedAtKind);

		while (pinKey.consumeClick()) {
			if (lookedAt == null) {
				client.player.sendOverlayMessage(Component.translatable("beaconrange.pin.no_beacon"));
			} else {
				togglePin(client, lookedAt);
			}
		}

		collect(client, level);
	}

	/** Beacon: aktuelle Stufe. Conduit: Rahmenblöcke (0, wenn er nicht im Wasser steht). */
	private static int currentLevel(Level level, BlockPos pos, PinStore.Kind kind) {
		return kind == PinStore.Kind.CONDUIT ? Conduits.frameBlocks(level, pos) : Beacons.level(level, pos);
	}

	private static boolean isTarget(Level level, BlockPos pos) {
		return Beacons.isBeacon(level, pos) || config.conduits && Conduits.isConduit(level, pos);
	}

	/** Pyramide (bis 4 Blöcke um den Beacon) bzw. Rahmen (2 Blöcke) ganz geladen? Sonst fehlen Blöcke beim Zählen. */
	private static boolean surroundingsLoaded(Level level, BlockPos pos, boolean conduit) {
		int r = conduit ? 2 : Beacons.MAX_LEVEL;
		return level.hasChunksAt(pos.getX() - r, pos.getZ() - r, pos.getX() + r, pos.getZ() + r);
	}

	/**
	 * Was man anschaut: der Beacon oder Conduit selbst - oder ein Block seiner Pyramide bzw. seines Rahmens. Unter einem
	 * hoch gebauten Beacon sieht man sonst nur die Pyramide, nie den Beacon.
	 */
	private static @Nullable BlockPos targetAt(Level level, BlockPos hit) {
		if (isTarget(level, hit)) {
			return hit.immutable();
		}
		if (level.getBlockState(hit).is(net.minecraft.tags.BlockTags.BEACON_BASE_BLOCKS)) {
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			for (int layer = 1; layer <= Beacons.MAX_LEVEL; layer++) {
				for (int dx = -layer; dx <= layer; dx++) {
					for (int dz = -layer; dz <= layer; dz++) {
						cursor.set(hit.getX() + dx, hit.getY() + layer, hit.getZ() + dz);
						if (Beacons.isBeacon(level, cursor) && Beacons.level(level, cursor) >= layer) {
							return cursor.immutable();
						}
					}
				}
			}
		}
		if (config.conduits && Conduits.isFrameBlock(level.getBlockState(hit))) {
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = -2; dy <= 2; dy++) {
					for (int dz = -2; dz <= 2; dz++) {
						// Der getroffene Block liegt auf einem Rahmenplatz des Conduits (vom Conduit aus gesehen -dx/-dy/-dz).
						if (Conduits.isFramePosition(-dx, -dy, -dz)
								&& Conduits.isConduit(level, cursor.set(hit.getX() + dx, hit.getY() + dy, hit.getZ() + dz))) {
							return cursor.immutable();
						}
					}
				}
			}
		}
		return null;
	}

	private static @Nullable BlockPos findLookedAt(Minecraft client, ClientLevel level) {
		if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos target = targetAt(level, hit.getBlockPos());
			if (target != null) {
				return target;
			}
		}
		Entity camera = client.getCameraEntity();
		if (camera == null) {
			return null;
		}
		if (camera.pick(LOOK_DISTANCE, 1.0F, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			return targetAt(level, hit.getBlockPos());
		}
		return null;
	}

	/**
	 * Der Beacon, dessen Fenster gerade aufgeht: der zuletzt angeklickte (bis 5 Sekunden her), sonst der unter dem
	 * Fadenkreuz. Auf Servern kommt das Fenster eine Weile nach dem Klick - bis dahin kann das Fadenkreuz woanders sein.
	 */
	public static @Nullable BlockPos openedBeacon(Minecraft client) {
		if (usedBeacon != null && client.level != null && client.level.getGameTime() - usedBeaconTime <= 100
				&& Beacons.isBeacon(client.level, usedBeacon)) {
			return usedBeacon;
		}
		return beaconUnderCrosshair(client);
	}

	/** Der Beacon unter dem Fadenkreuz (nur in Griffweite). */
	public static @Nullable BlockPos beaconUnderCrosshair(Minecraft client) {
		if (client.level != null && client.hitResult instanceof BlockHitResult hit
				&& hit.getType() == HitResult.Type.BLOCK && Beacons.isBeacon(client.level, hit.getBlockPos())) {
			return hit.getBlockPos().immutable();
		}
		return null;
	}

	/** Anpinnen bzw. lösen - Beacon oder Conduit, je nachdem was dort steht. */
	public static void togglePin(Minecraft client, BlockPos pos) {
		if (client.level == null || client.player == null) {
			return;
		}
		if (PinStore.worldKey(client) == null) {
			client.player.sendOverlayMessage(Component.translatable("beaconrange.pin.unavailable"));
			return;
		}
		PinStore.Kind kind = Conduits.isConduit(client.level, pos) ? PinStore.Kind.CONDUIT : PinStore.Kind.BEACON;
		boolean pinned = PINS.toggle(client, pos, currentLevel(client.level, pos, kind), kind);
		client.player.sendOverlayMessage(Component.translatable(
				pinned ? "beaconrange.pin.added" : "beaconrange.pin.removed", coordinates(pos)));
		collect(client, client.level);
	}

	/**
	 * Angepinnte Beacons und Conduits dieser Dimension plus der angeschaute - mit aktueller Stufe und Farbe. Steht dort
	 * keiner mehr, wird der Pin gelöst; ist das ausgeschaltet (Server, die Blöcke verstecken), bleibt er mit der zuletzt
	 * gesehenen Stufe stehen.
	 */
	private static void collect(Minecraft client, ClientLevel level) {
		if (hidden) {
			visible = List.of();
			visibleConduits = List.of();
			return;
		}
		List<BeaconArea> areas = new ArrayList<>();
		List<ConduitArea> conduits = new ArrayList<>();
		if (seenLevel != level) {
			SEEN.clear();
			seenLevel = level;
		}
		long now = level.getGameTime();
		for (PinStore.Pin pin : PINS.current(client)) {
			BlockPos pos = pin.pos();
			boolean conduit = pin.kind() == PinStore.Kind.CONDUIT;
			int pinLevel = pin.level();
			// Nur wenn der Chunk geladen ist, lässt sich sagen, ob der Block noch da ist.
			if (!level.isLoaded(pos)) {
				SEEN.remove(pos);
			} else if (conduit ? Conduits.isConduit(level, pos) : Beacons.isBeacon(level, pos)) {
				long firstSeen = SEEN.computeIfAbsent(pos, p -> now);
				if (!surroundingsLoaded(level, pos, conduit)) {
					// Nachbar-Chunks mit einem Teil der Pyramide fehlen noch - sie sähe kleiner aus, als sie ist.
					firstSeen = now;
					SEEN.put(pos, now);
				}
				int current = surroundingsLoaded(level, pos, conduit) ? currentLevel(level, pos, pin.kind()) : pinLevel;
				boolean active = conduit ? current >= Conduits.MIN_FRAME : current > 0;
				if (active) {
					PINS.updateLevel(client, pos, current);
					pinLevel = current;
				} else if (config.autoUnpin) {
					// Pyramide weg, Strahl verdeckt, kein Wasser: nichts zeichnen - und nach kurzer Zeit auch so merken,
					// sonst käme der alte Bereich aus dem Gedächtnis zurück, sobald der Chunk entladen ist.
					pinLevel = current;
					if (now - firstSeen > INACTIVE_TICKS) {
						PINS.updateLevel(client, pos, current);
					}
				}
				// Schloss zu: der Server versteckt vielleicht die Pyramide - dann mit der gemerkten Stufe weiterzeichnen.
			} else if (config.autoUnpin && SEEN.containsKey(pos) && near(client, pos)) {
				// Vor unseren Augen verschwunden: abgebaut.
				SEEN.remove(pos);
				PINS.remove(client, pos);
				if (client.player != null) {
					client.player.sendSystemMessage(prefix().append(Component.translatable(
							conduit ? "beaconrange.pin.gone_conduit" : "beaconrange.pin.gone", coordinates(pos))));
				}
				continue;
			}
			// Sonst: seit dem Laden des Chunks nie gesehen - vielleicht versteckt ihn der Server nur. Mit der gemerkten
			// Stufe weiterzeichnen; /beaconrange list zeigt ihn als "gerade nicht da".
			if (conduit) {
				if (config.conduits && Conduits.range(pinLevel) > 0) {
					conduits.add(conduitArea(pos, pinLevel, true));
				}
			} else if (pinLevel > 0) {
				areas.add(area(level, pos, pinLevel, true));
			}
		}
		if (config.showOnLook && lookedAt != null && !PINS.isPinned(client, lookedAt)) {
			if (lookedAtKind == PinStore.Kind.CONDUIT) {
				if (Conduits.range(lookedAtLevel) > 0) {
					conduits.add(conduitArea(lookedAt, lookedAtLevel, false));
				}
			} else if (lookedAtLevel > 0) {
				areas.add(area(level, lookedAt, lookedAtLevel, false));
			}
		}
		visible = List.copyOf(areas);
		visibleConduits = List.copyOf(conduits);
	}

	private static boolean near(Minecraft client, BlockPos pos) {
		if (client.player == null) {
			return false;
		}
		double dx = client.player.getX() - (pos.getX() + 0.5);
		double dy = client.player.getY() - (pos.getY() + 0.5);
		double dz = client.player.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dy * dy + dz * dz <= UNPIN_DISTANCE * UNPIN_DISTANCE;
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

	private static ConduitArea conduitArea(BlockPos pos, int frameBlocks, boolean pinned) {
		return new ConduitArea(pos, frameBlocks, Conduits.range(frameBlocks), config.conduitColor.rgb, pinned);
	}

	/** Automatisch lösen an/aus (Knopf im Beacon-Fenster und Einstellungen) - wird gleich gespeichert. */
	public static void setAutoUnpin(boolean value) {
		config.autoUnpin = value;
		config.save();
	}

	public static Component coordinates(BlockPos pos) {
		return Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
	}

	/** "[Beacon Range] " vor Chat-Meldungen. */
	public static MutableComponent prefix() {
		return Component.literal("[Beacon Range] ").withStyle(ChatFormatting.AQUA);
	}
}
