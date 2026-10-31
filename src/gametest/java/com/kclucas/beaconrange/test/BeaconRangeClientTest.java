package com.kclucas.beaconrange.test;

import com.kclucas.beaconrange.client.BeaconArea;
import com.kclucas.beaconrange.client.BeaconRangeClient;
import com.kclucas.beaconrange.client.BeaconRangeConfig;
import com.kclucas.beaconrange.client.BeaconRangeConfigScreen;
import com.kclucas.beaconrange.client.Beacons;
import com.kclucas.beaconrange.client.PinStore;
import com.kclucas.beaconrange.client.onboarding.Onboarding;
import com.kclucas.beaconrange.client.onboarding.OnboardingScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Baut in einer flachen Welt vier Beacons (Stufe 1 bis 4), schaut sie an, pinnt sie über das Beacon-Fenster, die
 * Taste und Befehle an und prüft Speichern, Entladen und Abbauen. Screenshots landen im Run-Ordner.
 */
public class BeaconRangeClientTest implements FabricClientGameTest {

	/** Boden der flachen Welt: Gras bei y=-61, man steht auf y=-60. */
	private static final int GROUND = -60;
	private static final BlockPos MAIN = new BlockPos(0, GROUND + 4, 0);
	private static final BlockPos LEVEL3 = new BlockPos(-84, GROUND + 3, 12);
	private static final BlockPos LEVEL2 = new BlockPos(-148, GROUND + 2, 2);
	private static final BlockPos LEVEL1 = new BlockPos(-196, GROUND + 1, 14);
	/** Von hier sieht man alle vier Bereiche von schräg oben. */
	private static final double[] OVERVIEW = {-82.5, GROUND + 24, -96.5};
	private static final BlockPos OVERVIEW_TARGET = new BlockPos(-82, GROUND + 2, 25);
	/** Glas über jedem Beacon für das Bild mit Strahlfarbe. */
	private static final List<String> GLASS = List.of("magenta", "lime", "red", "light_blue");
	private static final String[] LAYERS = {"iron_block", "gold_block", "emerald_block", "diamond_block"};

	@Override
	public void runTest(ClientGameTestContext context) {
		// Test-Client stumm: Lucas streamt oft nebenbei, Töne aus dem Testlauf sollen nicht durchkommen.
		context.runOnClient(c -> c.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0));
		context.getInput().resizeWindow(2560, 1440);
		context.runOnClient(c -> {
			c.options.guiScale().set(3);
			c.resizeDisplay();
		});
		testOnboarding(context);
		testRanges();

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("gamemode creative @a");
			server.runCommand("time set 1500");

			context.getInput().resizeWindow(2560, 1440);
			context.runOnClient(c -> {
				c.options.fov().set(80);
				c.options.guiScale().set(3);
				c.options.renderDistance().set(12);
				c.options.simulationDistance().set(12); // auch weit entfernte Beacons aktualisieren ihren Strahl
				c.options.broadcastOptions(); // sonst schickt der Server weiter nur so viele Chunks wie beim Beitreten
				c.resizeDisplay();
			});
			// Der Server lädt nur Chunks um den Spieler - den ganzen Bauplatz vorher laden, sonst gehen die Befehle ins Leere.
			server.runCommand("forceload add -240 -80 80 110");
			for (int i = 0; i < 200 && !server.computeOnServer(s -> s.overworld().isLoaded(new BlockPos(-240, GROUND, -80))
					&& s.overworld().isLoaded(new BlockPos(80, GROUND, 110))
					&& s.overworld().isLoaded(new BlockPos(-240, GROUND, 110))); i++) {
				context.waitTicks(5);
			}
			buildScenery(server);
			pyramid(server, MAIN, 4);
			pyramid(server, LEVEL3, 3);
			pyramid(server, LEVEL2, 2);
			pyramid(server, LEVEL1, 1);

			// 1. Von weit weg auf den Beacon schauen: Bereich und Info unter dem Fadenkreuz erscheinen.
			lookFrom(context, server, 34.5, GROUND + 9, -78.5, MAIN);
			context.waitTicks(100); // der Server rechnet die Stufe nur alle 4 Sekunden aus (fürs Beacon-Fenster)
			server.runCommand("forceload remove all");
			waitForWorld(context, world);
			expectLookedAt(context, MAIN, 4);
			BeaconArea area = expectVisible(context, MAIN, 4, false);
			AABB expected = new AABB(-50, MAIN.getY() - 50, -50, 51, MAIN.getY() + 51 + 384, 51);
			if (!area.box().equals(expected)) {
				throw new AssertionError("Bereich falsch: " + area.box() + " statt " + expected);
			}
			calm(context);
			context.takeScreenshot("beaconrange-1-anschauen");

			// 2. Beacon öffnen und über den Knopf anpinnen.
			// auf der zweiten Pyramidenstufe (Oberkante y=-58), 3 Blöcke vor dem Beacon
			lookFrom(context, server, 0.5, GROUND + 2, -2.7, MAIN);
			context.waitTicks(10);
			if (!context.computeOnClient(c -> MAIN.equals(BeaconRangeClient.beaconUnderCrosshair(c)))) {
				throw new AssertionError("Der Beacon ist nicht im Fadenkreuz");
			}
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(BeaconScreen.class);
			context.waitTicks(10);
			context.takeScreenshot("beaconrange-2-fenster");
			context.clickScreenButton("beaconrange.button.pin");
			context.waitTicks(5);
			if (!isPinned(context, MAIN)) {
				throw new AssertionError("Der Knopf hat den Beacon nicht angepinnt");
			}
			context.takeScreenshot("beaconrange-2b-angepinnt");
			// Fenstergröße ändern baut das Fenster neu auf - der Knopf muss bleiben und den Zustand kennen.
			context.runOnClient(c -> c.resizeDisplay());
			context.waitTicks(5);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);

			// 3. Wegschauen: angepinnt bleibt der Bereich sichtbar.
			server.runCommand("tp @a 0.5 " + GROUND + " -20.5 180 0");
			context.waitTicks(10);
			expectVisible(context, MAIN, 4, true);

			// 4. Gespeichert pro Welt und Dimension, mit Stufe.
			Path pinsFile = FabricLoader.getInstance().getConfigDir().resolve("KCMods/beaconrange/pins.json");
			String saved = read(pinsFile);
			if (!saved.contains("local/") || !saved.contains("minecraft:overworld") || !saved.contains("\"level\": 4")) {
				throw new AssertionError("pins.json unvollständig: " + saved);
			}
			PinStore reloaded = new PinStore(pinsFile);
			reloaded.load();
			if (reloaded.countTotal() != 1) {
				throw new AssertionError("Nach dem Neuladen nicht 1 Beacon: " + reloaded.countTotal());
			}

			// 5. Weit weg (Chunk entladen) und zurück: der Beacon bleibt angepinnt - in Version 1 ging er dabei verloren.
			server.runCommand("tp @a 3000 " + GROUND + " 3000");
			context.waitFor(c -> c.level != null && !c.level.isLoaded(MAIN), 600);
			context.waitTicks(20);
			if (!isPinned(context, MAIN)) {
				throw new AssertionError("Beim Entladen des Chunks ging der Beacon verloren");
			}
			expectVisible(context, MAIN, 4, true); // aus der gemerkten Stufe, ohne geladenen Chunk
			server.runCommand("tp @a 0.5 " + GROUND + " -20.5 0 0");
			context.waitFor(c -> c.level != null && c.level.isLoaded(MAIN), 600);
			context.waitTicks(20);
			if (!isPinned(context, MAIN)) {
				throw new AssertionError("Nach der Rückkehr ist der Beacon nicht mehr angepinnt");
			}

			// 6. Abgebaut: der Beacon wird gelöst.
			server.runCommand("setblock " + MAIN.getX() + " " + MAIN.getY() + " " + MAIN.getZ() + " air");
			context.waitTicks(10);
			if (isPinned(context, MAIN)) {
				throw new AssertionError("Abgebauter Beacon ist noch angepinnt");
			}
			server.runCommand("setblock " + MAIN.getX() + " " + MAIN.getY() + " " + MAIN.getZ() + " beacon");

			// 7. Taste "anpinnen" (im Test auf P gelegt) beim Anschauen aus der Ferne.
			context.runOnClient(c -> {
				KeyMapping pin = Arrays.stream(c.options.keyMappings)
						.filter(k -> k.getName().equals("key.beaconrange.pin")).findFirst().orElseThrow();
				pin.setKey(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_P));
				KeyMapping.resetMapping();
			});
			lookFrom(context, server, 34.5, GROUND + 9, -78.5, MAIN);
			context.waitTicks(10);
			context.getInput().pressKey(InputConstants.KEY_P);
			context.waitTicks(5);
			if (!isPinned(context, MAIN)) {
				throw new AssertionError("Die Taste hat den angeschauten Beacon nicht angepinnt");
			}

			// 8. Alle vier Stufen angepinnt, von schräg oben.
			context.runOnClient(c -> {
				for (BlockPos pos : List.of(LEVEL1, LEVEL2, LEVEL3)) {
					BeaconRangeClient.togglePin(c, pos);
				}
			});
			lookFrom(context, server, OVERVIEW[0], OVERVIEW[1], OVERVIEW[2], OVERVIEW_TARGET);
			context.waitFor(c -> List.of(LEVEL1, LEVEL2, LEVEL3, MAIN).stream().allMatch(c.level::isLoaded), 1200);
			waitForWorld(context, world);
			context.waitTicks(100); // die letzten Chunks fertig zeichnen lassen
			calm(context);
			screenshotWithoutHud(context, "beaconrange-3-stufen");
			expectVisible(context, LEVEL1, 1, true);
			expectVisible(context, LEVEL2, 2, true);
			expectVisible(context, LEVEL3, 3, true);
			expectVisible(context, MAIN, 4, true);

			// 9. Kurz vor der Grenze stehen (von hinten, wie mit F5): Wand und Linie am Boden davor.
			lookFrom(context, server, 6.5, GROUND, -43.5, new BlockPos(-6, GROUND + 6, -64));
			context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(20);
			calm(context);
			screenshotWithoutHud(context, "beaconrange-4-drin");
			context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));

			// 10. Wände in voller Höhe und Strahlfarbe (buntes Glas über jedem Beacon, rot über Stufe 3).
			List<BlockPos> beacons = List.of(LEVEL1, LEVEL2, LEVEL3, MAIN);
			for (int i = 0; i < beacons.size(); i++) {
				BlockPos above = beacons.get(i).above();
				server.runCommand("setblock " + above.getX() + " " + above.getY() + " " + above.getZ() + " "
						+ GLASS.get(i) + "_stained_glass");
			}
			context.runOnClient(c -> {
				BeaconRangeClient.getConfig().walls = BeaconRangeConfig.WallStyle.FULL;
				BeaconRangeClient.getConfig().colorMode = BeaconRangeConfig.ColorMode.BEAM;
			});
			lookFrom(context, server, OVERVIEW[0], OVERVIEW[1], OVERVIEW[2], OVERVIEW_TARGET);
			context.waitTicks(60);
			int beam = context.computeOnClient(c -> BeaconRangeClient.getVisible().stream()
					.filter(a -> a.pos().equals(LEVEL3)).findFirst().orElseThrow().color());
			if (beam == BeaconRangeConfig.BoxColor.GOLD.rgb || (beam >> 16 & 0xFF) < 0x80) {
				throw new AssertionError("Strahlfarbe nicht übernommen: " + Integer.toHexString(beam));
			}
			calm(context);
			screenshotWithoutHud(context, "beaconrange-5-volle-hoehe-strahlfarbe");
			context.runOnClient(c -> {
				BeaconRangeClient.getConfig().walls = BeaconRangeConfig.WallStyle.FADE;
				BeaconRangeClient.getConfig().colorMode = BeaconRangeConfig.ColorMode.LEVEL;
			});
			for (BlockPos pos : beacons) {
				server.runCommand("setblock " + pos.getX() + " " + (pos.getY() + 1) + " " + pos.getZ() + " air");
			}

			// 11. Befehle: Liste, Entfernen (auch im alten Format x,y,z), alle lösen.
			server.runCommand("tp @a 0.5 " + GROUND + " -20.5 0 0");
			context.waitTicks(10);
			context.runOnClient(c -> c.gui.getChat().clearMessages(false));
			command(context, "beaconrange list");
			context.waitTicks(5);
			context.takeScreenshot("beaconrange-6-liste");
			command(context, "beaconrange remove " + LEVEL1.getX() + "," + LEVEL1.getY() + "," + LEVEL1.getZ());
			context.waitTicks(5);
			if (isPinned(context, LEVEL1) || !isPinned(context, LEVEL2)) {
				throw new AssertionError("/beaconrange remove hat nicht genau einen Beacon gelöst");
			}
			command(context, "beaconrange clear");
			context.waitTicks(5);
			if (context.computeOnClient(c -> BeaconRangeClient.getPins().countWorld(c)) != 0) {
				throw new AssertionError("/beaconrange clear hat nicht alle gelöst");
			}

			// 12. Einstellungen (mit Beacons in dieser Welt, damit die Zahl dort steht).
			context.runOnClient(c -> {
				BeaconRangeClient.getPins().toggle(c, MAIN, 4);
				BeaconRangeClient.getPins().toggle(c, LEVEL3, 3);
			});
			context.setScreen(() -> new BeaconRangeConfigScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("beaconrange-7-einstellungen");
			context.getInput().setCursorPos(1280, 700);
			context.getInput().scroll(-20);
			context.waitTicks(5);
			context.takeScreenshot("beaconrange-7b-einstellungen-unten");
			context.runOnClient(c -> c.setScreen(null));
			context.waitTicks(5);

			// 13. Wirkt Minecraft genau im gezeichneten Bereich? Echter Beacon mit Eile, Spieler oben, unten, seitlich.
			testVanillaArea(context, server);
		}

		// Kaputte Einstellungen in der Datei: unbekannte Werte werden zu Standardwerten.
		try {
			Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("KCMods/beaconrange/config.json"),
					"{\"walls\": \"SPIRAL\", \"level2\": null, \"edges\": false}");
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		BeaconRangeConfig loaded = BeaconRangeConfig.load();
		if (loaded.walls != BeaconRangeConfig.WallStyle.FADE || loaded.level2 != BeaconRangeConfig.BoxColor.GREEN || loaded.edges) {
			throw new AssertionError("Einstellungen nicht repariert: " + loaded.walls + " " + loaded.level2 + " " + loaded.edges);
		}
	}

	/** Erster Start: Tour mit drei Seiten. Danach ein "Update" von 1.0.2: nur "Was ist neu". */
	private static void testOnboarding(ClientGameTestContext context) {
		context.waitForScreen(OnboardingScreen.class);
		context.waitTicks(10);
		expectStep(context, -1);
		context.takeScreenshot("onboarding-1-willkommen");
		for (int step = 0; step < 3; step++) {
			context.getInput().pressKey(InputConstants.KEY_RIGHT);
			context.waitTicks(5);
			expectStep(context, step);
			context.takeScreenshot("onboarding-" + (step + 2) + "-schritt");
		}
		context.getInput().pressKey(InputConstants.KEY_RIGHT);
		context.waitTicks(10);
		if (context.computeOnClient(c -> c.screen instanceof OnboardingScreen)) {
			throw new AssertionError("Die Tour sollte nach der letzten Seite geschlossen sein");
		}
		if (!read(Onboarding.seenFilePath()).contains("\"beaconrange\": \"" + MOD_VERSION + "\"")) {
			throw new AssertionError("Version nicht als gesehen vermerkt: " + read(Onboarding.seenFilePath()));
		}

		write(Onboarding.seenFilePath(), "{\"modVersions\": {\"beaconrange\": \"1.0.2\"}}");
		context.runOnClient(c -> Onboarding.showPending());
		context.waitForScreen(OnboardingScreen.class);
		context.waitTicks(5);
		context.getInput().pressKey(InputConstants.KEY_RIGHT);
		context.waitTicks(5);
		if (!context.computeOnClient(c -> c.screen instanceof OnboardingScreen screen && screen.isNewsStep())) {
			throw new AssertionError("Nach dem Update sollte \"Was ist neu\" kommen");
		}
		context.takeScreenshot("onboarding-5-was-ist-neu");
		context.getInput().pressKey(InputConstants.KEY_RIGHT);
		context.waitTicks(10);
		if (context.computeOnClient(c -> c.screen instanceof OnboardingScreen)) {
			throw new AssertionError("Nach den Neuigkeiten sollte der Screen geschlossen sein");
		}
	}

	private static final String MOD_VERSION = FabricLoader.getInstance().getModContainer(BeaconRangeClient.MOD_ID)
			.orElseThrow().getMetadata().getVersion().getFriendlyString();

	/** Stufe und Reichweite wie in Minecraft. */
	private static void testRanges() {
		int[] ranges = {Beacons.range(1), Beacons.range(2), Beacons.range(3), Beacons.range(4)};
		if (!Arrays.equals(ranges, new int[]{20, 30, 40, 50})) {
			throw new AssertionError("Reichweiten falsch: " + Arrays.toString(ranges));
		}
	}

	/**
	 * Am echten Beacon: Minecraft wirkt genau dort, wo die Mod den Bereich zeichnet - 200 Blöcke darüber noch, knapp
	 * unter dem Boden des Bereichs und knapp neben der Wand nicht mehr.
	 */
	private static void testVanillaArea(ClientGameTestContext context, TestServerContext server) {
		BlockPos beacon = new BlockPos(60, 100, -60); // Stufe 1: 20 Blöcke, der Bereich beginnt unten auf Höhe 80
		pyramid(server, beacon, 1);
		server.runCommand("data merge block " + beacon.getX() + " " + beacon.getY() + " " + beacon.getZ()
				+ " {primary_effect:\"minecraft:haste\"}");
		AABB area = server.computeOnServer(s -> Beacons.area(s.overworld(), beacon, 1));
		if (area.minY != 80 || area.maxY < 320) {
			throw new AssertionError("Bereich falsch berechnet: " + area);
		}
		context.waitTicks(100); // Strahl aufbauen, Stufe berechnen
		double x = beacon.getX() + 0.5, z = beacon.getZ() + 0.5;
		expectHaste(context, server, area, x, 300, z, true);       // 200 Blöcke über dem Beacon
		expectHaste(context, server, area, x, 79, z, true);        // Kopf ragt in den Boden des Bereichs
		expectHaste(context, server, area, x, 78, z, false);       // knapp darunter
		expectHaste(context, server, area, x + 20, 100, z, true);  // innen an der Wand
		expectHaste(context, server, area, x + 21, 100, z, false); // knapp daneben
	}

	/** Spieler (Füße auf y, 0,6 breit und 1,8 hoch) hinstellen, einen Beacon-Takt abwarten, Eile prüfen. */
	private static void expectHaste(ClientGameTestContext context, TestServerContext server, AABB area,
			double x, double y, double z, boolean expected) {
		boolean drawn = area.intersects(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
		server.runCommand("effect clear @a");
		String tp = "tp @a " + x + " " + y + " " + z;
		server.runCommand(tp);
		context.waitTicks(2);
		context.runOnClient(c -> {
			c.player.getAbilities().flying = true;
			c.player.onUpdateAbilities();
		});
		server.runCommand(tp);
		context.waitTicks(100); // Beacons wirken alle 80 Ticks
		boolean vanilla = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().hasEffect(MobEffects.HASTE));
		if (drawn != expected || vanilla != expected) {
			throw new AssertionError("Bei " + x + " " + y + " " + z + ": erwartet " + expected + ", gezeichnet " + drawn
					+ ", Minecraft " + vanilla);
		}
	}

	/** Gras-Ebene mit Bäumen, Teich und Weg, damit die Bilder nach etwas aussehen. */
	private static void buildScenery(TestServerContext server) {
		server.runCommand("fill -36 " + (GROUND - 2) + " 22 -20 " + (GROUND - 1) + " 34 water");
		int[][] trees = {
				{-30, -40}, {-58, -24}, {-14, 40}, {26, 34}, {40, -12}, {-110, -30}, {-120, 40}, {-64, 48},
				{-170, -24}, {-176, 40}, {-214, -10}, {-212, 44}, {14, 62}, {-44, 70}, {-100, 72}, {-160, 66},
				{58, 20}, {-84, -46}, {-136, -46}, {-200, -44}, {-6, -58}, {48, 48}, {-226, 60}, {-24, 92},
		};
		String[] kinds = {"fancy_oak", "birch", "oak", "fancy_oak", "spruce"};
		for (int i = 0; i < trees.length; i++) {
			server.runCommand("place feature minecraft:" + kinds[i % kinds.length] + " " + trees[i][0] + " " + GROUND + " " + trees[i][1]);
		}
	}

	/** Pyramide aus Eisen, Gold, Smaragd und Diamant (unten nach oben), Beacon obendrauf. */
	private static void pyramid(TestServerContext server, BlockPos beacon, int level) {
		for (int i = level; i >= 1; i--) {
			int y = beacon.getY() - i;
			String block = LAYERS[level - i];
			server.runCommand("fill " + (beacon.getX() - i) + " " + y + " " + (beacon.getZ() - i) + " "
					+ (beacon.getX() + i) + " " + y + " " + (beacon.getZ() + i) + " " + block);
		}
		server.runCommand("setblock " + beacon.getX() + " " + beacon.getY() + " " + beacon.getZ() + " beacon");
	}

	/**
	 * Spieler an eine Stelle setzen und auf die Mitte eines Blocks blicken lassen. In der Luft fliegt er, damit er
	 * nicht fällt - Minecraft schaltet das Fliegen am Boden selbst ab, deshalb erst nach dem Teleport.
	 */
	private static void lookFrom(ClientGameTestContext context, TestServerContext server, double x, double y, double z,
			BlockPos target) {
		double dx = target.getX() + 0.5 - x;
		double dy = target.getY() + 0.5 - (y + 1.62);
		double dz = target.getZ() + 0.5 - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		String tp = "tp @a " + x + " " + y + " " + z + " " + yaw + " " + pitch;
		server.runCommand(tp);
		context.waitTicks(2);
		context.runOnClient(c -> {
			c.player.getAbilities().flying = true;
			c.player.onUpdateAbilities();
		});
		server.runCommand(tp);
		context.waitTicks(3);
	}

	/** Warten, bis die Chunks um den Spieler da und gezeichnet sind. */
	private static void waitForWorld(ClientGameTestContext context, TestSingleplayerContext world) {
		context.waitTicks(60);
		world.getClientWorld().waitForChunksRender(false, 1200);
	}

	private static void command(ClientGameTestContext context, String command) {
		context.runOnClient(c -> c.player.connection.sendCommand(command));
	}

	private static boolean isPinned(ClientGameTestContext context, BlockPos pos) {
		return context.computeOnClient(c -> BeaconRangeClient.getPins().isPinned(c, pos));
	}

	private static void expectLookedAt(ClientGameTestContext context, BlockPos pos, int level) {
		BlockPos lookedAt = context.computeOnClient(c -> BeaconRangeClient.getLookedAt());
		int actual = context.computeOnClient(c -> BeaconRangeClient.getLookedAtLevel());
		if (!pos.equals(lookedAt) || actual != level) {
			throw new AssertionError("Angeschaut: erwartet " + pos + " Stufe " + level + ", ist " + lookedAt + " Stufe " + actual);
		}
	}

	private static BeaconArea expectVisible(ClientGameTestContext context, BlockPos pos, int level, boolean pinned) {
		List<BeaconArea> visible = context.computeOnClient(c -> BeaconRangeClient.getVisible());
		Optional<BeaconArea> area = visible.stream().filter(a -> a.pos().equals(pos)).findFirst();
		if (area.isEmpty() || area.get().level() != level || area.get().pinned() != pinned) {
			String state = context.computeOnClient(c -> "angepinnt=" + BeaconRangeClient.getPins().isPinned(c, pos)
					+ " geladen=" + c.level.isLoaded(pos) + " block=" + c.level.getBlockState(pos)
					+ " stufe=" + Beacons.level(c.level, pos) + " pins=" + BeaconRangeClient.getPins().current(c)
					+ " spieler=" + c.player.position());
			throw new AssertionError("Erwartet sichtbar: " + pos + " Stufe " + level + (pinned ? " angepinnt" : "")
					+ ", sichtbar sind: " + visible + " | " + state);
		}
		return area.get();
	}

	private static void expectStep(ClientGameTestContext context, int expected) {
		int actual = context.computeOnClient(c ->
				c.screen instanceof OnboardingScreen screen ? screen.getStepIndex() : Integer.MIN_VALUE);
		if (actual != expected) {
			throw new AssertionError("Tutorial: erwartet Seite " + expected + ", ist aber " + actual);
		}
	}

	/** Wie mit F1: ohne Hotbar, Arm und Fadenkreuz. */
	private static void screenshotWithoutHud(ClientGameTestContext context, String name) {
		context.runOnClient(c -> c.options.hideGui = true);
		context.waitTicks(2);
		context.takeScreenshot(name);
		context.runOnClient(c -> c.options.hideGui = false);
	}

	/** Statusmeldungen ausblenden lassen, Chat und Toasts leeren - für aufgeräumte Screenshots. */
	private static void calm(ClientGameTestContext context) {
		context.waitTicks(60);
		context.runOnClient(c -> {
			c.gui.getChat().clearMessages(false);
			c.getToastManager().clear();
		});
		context.waitTicks(5);
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		} catch (IOException e) {
			throw new AssertionError(path + " fehlt", e);
		}
	}

	private static void write(Path path, String text) {
		try {
			Files.writeString(path, text);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
}
