package com.kclucas.beaconrange.test;

import com.kclucas.beaconrange.client.BeaconArea;
import com.kclucas.beaconrange.client.BeaconRangeClient;
import com.kclucas.beaconrange.client.BeaconRangeConfig;
import com.kclucas.beaconrange.client.BeaconRangeConfigScreen;
import com.kclucas.beaconrange.client.BeaconScreenButton;
import com.kclucas.beaconrange.client.Beacons;
import com.kclucas.beaconrange.client.ConduitArea;
import com.kclucas.beaconrange.client.Conduits;
import com.kclucas.beaconrange.client.PinStore;
import com.kclucas.beaconrange.client.onboarding.Onboarding;
import com.kclucas.beaconrange.client.onboarding.OnboardingScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Baut in einer leeren flachen Welt (keine Tiere, keine Bäume) vier Beacons nebeneinander (Stufe 1 bis 4, ohne
 * Überschneidung), einen Beacon auf einem Turm (der Bereich reicht sichtbar unter ihn) und einen Conduit mit vollem
 * Rahmen in einem Becken. Prüft Anschauen, Anpinnen über Fenster, Taste und Befehle, Speichern, Entladen, Abbauen,
 * versteckte Beacons (Anti-Xray), Conduits und ob Minecraft genau im gezeichneten Bereich wirkt. Screenshots landen im
 * Run-Ordner; die fertige Welt wird als "BeaconRangeTest" in run/saves kopiert, zum Ausprobieren mit runClient.
 */
public class BeaconRangeClientTest implements FabricClientGameTest {

	/** Boden der flachen Welt: Gras bei y=-61, man steht auf y=-60. */
	private static final int GROUND = -60;
	/** Vier Stufen nebeneinander, je 9 Blöcke Luft zwischen den Bereichen (50, 40, 30, 20 Blöcke Reichweite). */
	private static final BlockPos MAIN = new BlockPos(0, GROUND + 4, 0);
	private static final BlockPos LEVEL3 = new BlockPos(-100, GROUND + 3, 0);
	private static final BlockPos LEVEL2 = new BlockPos(-180, GROUND + 2, 0);
	private static final BlockPos LEVEL1 = new BlockPos(-240, GROUND + 1, 0);
	/** Stufe 2 auf einem 45 Blöcke hohen Turm: der Bereich beginnt 30 Blöcke unter dem Beacon, frei in der Luft. */
	private static final BlockPos TOWER = new BlockPos(250, GROUND + 45, -60);
	/** Conduit mit vollem Rahmen (42 Blöcke) in einem oben offenen Glasbecken: 96 Blöcke Reichweite. */
	private static final BlockPos CONDUIT = new BlockPos(0, GROUND + 3, 300);
	/** Von hier sieht man alle vier Bereiche von schräg oben. */
	private static final double[] OVERVIEW = {-104.5, GROUND + 80, -150.5};
	private static final BlockPos OVERVIEW_TARGET = new BlockPos(-104, GROUND, 5);
	/** Glas über jedem Beacon für das Bild mit Strahlfarbe. */
	private static final List<String> GLASS = List.of("magenta", "lime", "red", "light_blue");
	private static final String[] LAYERS = {"iron_block", "gold_block", "emerald_block", "diamond_block"};
	private static final String WORLD_NAME = "BeaconRangeTest";

	@Override
	public void runTest(ClientGameTestContext context) {
		// Test-Client stumm: Lucas streamt oft nebenbei, Töne aus dem Testlauf sollen nicht durchkommen.
		context.runOnClient(c -> c.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0));
		context.getInput().resizeWindow(Integer.parseInt(System.getenv().getOrDefault("KC_TEST_WIDTH", "2560")), Integer.parseInt(System.getenv().getOrDefault("KC_TEST_HEIGHT", "1440")));
		context.runOnClient(c -> {
			c.options.guiScale().set(3);
			c.resizeDisplay();
		});
		testOnboarding(context);
		testRanges();

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			server.runCommand("gamemode creative @a");
			// Ruhige, gleichbleibende Bilder: keine Tiere, immer Vormittag, kein Wetter.
			server.runCommand("gamerule doMobSpawning false");
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("time set 1500");
			server.runCommand("weather clear");

			context.getInput().resizeWindow(Integer.parseInt(System.getenv().getOrDefault("KC_TEST_WIDTH", "2560")), Integer.parseInt(System.getenv().getOrDefault("KC_TEST_HEIGHT", "1440")));
			context.runOnClient(c -> {
				c.options.fov().set(80);
				c.options.guiScale().set(3);
				c.options.renderDistance().set(20); // die Übersicht reicht bis gut 250 Blöcke weit
				c.options.simulationDistance().set(12); // auch weit entfernte Beacons aktualisieren ihren Strahl
				c.options.broadcastOptions(); // sonst schickt der Server weiter nur so viele Chunks wie beim Beitreten
				c.resizeDisplay();
			});
			// Der Server lädt nur Chunks um den Spieler - die Bauplätze vorher laden, sonst gehen die Befehle ins Leere.
			forceload(context, server, -272, -64, 63, 63);
			forceload(context, server, 208, -112, 303, -17);
			forceload(context, server, -16, 288, 15, 319);
			pyramid(server, MAIN, 4);
			pyramid(server, LEVEL3, 3);
			pyramid(server, LEVEL2, 2);
			pyramid(server, LEVEL1, 1);
			tower(server, TOWER, 2);
			aquarium(server, CONDUIT);
			// In der flachen Welt entstehen beim Laden Tiere - weg damit, sie stören auf den Bildern.
			server.runCommand("kill @e[type=!player]");
			saveWorldForManualTest(context, server);

			// 1. Von weit weg auf den Beacon schauen: Bereich und Info unter dem Fadenkreuz erscheinen.
			lookFrom(context, server, 34.5, GROUND + 9, -78.5, MAIN);
			context.waitTicks(100); // der Server rechnet die Stufe nur alle 4 Sekunden aus (fürs Beacon-Fenster)
			waitForWorld(context, world);
			expectLookedAt(context, MAIN, 4);
			BeaconArea area = expectVisible(context, MAIN, 4, false);
			AABB expected = new AABB(-50, MAIN.getY() - 50, -50, 51, MAIN.getY() + 51 + 384, 51);
			if (!area.box().equals(expected)) {
				throw new AssertionError("Bereich falsch: " + area.box() + " statt " + expected);
			}
			calm(context);
			context.takeScreenshot("beaconrange-1-anschauen");

			// 2. Beacon öffnen und über den Knopf anpinnen; der Schloss-Knopf daneben schaltet automatisches Lösen.
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
			clickLock(context);
			if (context.computeOnClient(c -> BeaconRangeClient.getConfig().autoUnpin)) {
				throw new AssertionError("Der Schloss-Knopf hat automatisches Lösen nicht ausgeschaltet");
			}
			context.takeScreenshot("beaconrange-2b-angepinnt");
			clickLock(context);
			if (!context.computeOnClient(c -> BeaconRangeClient.getConfig().autoUnpin)) {
				throw new AssertionError("Der Schloss-Knopf hat automatisches Lösen nicht wieder eingeschaltet");
			}
			// Fenstergröße ändern baut das Fenster neu auf - die Knöpfe müssen bleiben und den Zustand kennen.
			context.runOnClient(c -> c.resizeDisplay());
			context.waitTicks(5);
			if (context.computeOnClient(c -> lockButton(c) == null)) {
				throw new AssertionError("Nach dem Neuaufbau fehlt der Schloss-Knopf");
			}
			String pinnedLabel = context.computeOnClient(c -> Component.translatable("beaconrange.button.pinned").getString());
			if (!context.computeOnClient(c -> Screens.getButtons(c.screen).stream()
					.anyMatch(w -> w.getMessage().getString().equals(pinnedLabel)))) {
				throw new AssertionError("Nach dem Neuaufbau zeigt der Knopf nicht mehr \"Angepinnt\"");
			}
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);

			// 3. Wegschauen: angepinnt bleibt der Bereich sichtbar.
			server.runCommand("tp @a 0.5 " + GROUND + " -20.5 180 0");
			context.waitTicks(10);
			expectVisible(context, MAIN, 4, true);

			// 4. Gespeichert pro Welt und Dimension, mit Stufe und Art.
			Path pinsFile = FabricLoader.getInstance().getConfigDir().resolve("KCMods/beaconrange/pins.json");
			String saved = read(pinsFile);
			if (!saved.contains("local/") || !saved.contains("minecraft:overworld") || !saved.contains("\"level\": 4")
					|| !saved.contains("\"type\": \"beacon\"")) {
				throw new AssertionError("pins.json unvollständig: " + saved);
			}
			PinStore reloaded = new PinStore(pinsFile);
			reloaded.load();
			if (reloaded.countTotal() != 1) {
				throw new AssertionError("Nach dem Neuladen nicht 1 Beacon: " + reloaded.countTotal());
			}

			// 5. Weit weg (Chunk entladen) und zurück: der Beacon bleibt angepinnt - in Version 1 ging er dabei verloren.
			server.runCommand("forceload remove all");
			server.runCommand("tp @a 3000 " + GROUND + " 3000");
			context.waitFor(c -> c.level != null && !c.level.isLoaded(MAIN), 600);
			context.waitTicks(20);
			if (!isPinned(context, MAIN)) {
				throw new AssertionError("Beim Entladen des Chunks ging der Beacon verloren");
			}
			expectVisible(context, MAIN, 4, true); // aus der gemerkten Stufe, ohne geladenen Chunk
			// Wie Anti-Xray: beim Zurückkommen schickt der Server Tiefenschiefer statt des Beacons. Der Pin muss bleiben -
			// gelöst wird nur, was vor unseren Augen verschwindet.
			forceload(context, server, -272, -64, 63, 63);
			server.runCommand("setblock " + MAIN.getX() + " " + MAIN.getY() + " " + MAIN.getZ() + " deepslate");
			server.runCommand("tp @a 0.5 " + GROUND + " -6.5 0 0");
			context.waitFor(c -> c.level != null && c.level.isLoaded(MAIN), 600);
			context.waitTicks(20);
			if (!isPinned(context, MAIN)) {
				throw new AssertionError("Beim Laden versteckter Beacon (Anti-Xray) wurde gelöst");
			}
			expectVisible(context, MAIN, 4, true);
			server.runCommand("setblock " + MAIN.getX() + " " + MAIN.getY() + " " + MAIN.getZ() + " beacon");
			context.waitTicks(20);
			if (!isPinned(context, MAIN)) {
				throw new AssertionError("Nach der Rückkehr ist der Beacon nicht mehr angepinnt");
			}

			// 6. Versteckt der Server den Beacon (Anti-Xray zeigt Tiefenschiefer): mit "nicht automatisch lösen" bleibt er.
			context.runOnClient(c -> BeaconRangeClient.setAutoUnpin(false));
			server.runCommand("setblock " + MAIN.getX() + " " + MAIN.getY() + " " + MAIN.getZ() + " deepslate");
			context.waitTicks(10);
			if (!isPinned(context, MAIN)) {
				throw new AssertionError("Versteckter Beacon wurde trotz \"nicht automatisch lösen\" gelöst");
			}
			expectVisible(context, MAIN, 4, true);
			context.runOnClient(c -> c.gui.getChat().clearMessages(false));
			command(context, "beaconrange list");
			context.waitTicks(5);
			context.takeScreenshot("beaconrange-6b-liste-versteckt"); // "(gerade nicht da)" hinter dem Beacon
			// Wieder an: jetzt wird er gelöst - er ist vor unseren Augen verschwunden und wir stehen nah dran.
			context.runOnClient(c -> BeaconRangeClient.setAutoUnpin(true));
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

			// 8. Alle vier Stufen angepinnt, von schräg oben - keine Überschneidungen.
			context.runOnClient(c -> {
				for (BlockPos pos : List.of(LEVEL1, LEVEL2, LEVEL3)) {
					BeaconRangeClient.togglePin(c, pos);
				}
			});
			lookFrom(context, server, OVERVIEW[0], OVERVIEW[1], OVERVIEW[2], OVERVIEW_TARGET);
			waitForAround(context, world); // sonst fehlen hinten Chunks (weiße Löcher im Boden)
			context.waitTicks(100); // die letzten Chunks fertig zeichnen lassen
			calm(context);
			screenshotWithoutHud(context, "beaconrange-3-stufen");
			List<BeaconArea> four = List.of(expectVisible(context, LEVEL1, 1, true), expectVisible(context, LEVEL2, 2, true),
					expectVisible(context, LEVEL3, 3, true), expectVisible(context, MAIN, 4, true));
			for (int i = 0; i < four.size(); i++) {
				for (int j = i + 1; j < four.size(); j++) {
					if (four.get(i).box().intersects(four.get(j).box())) {
						throw new AssertionError("Bereiche überschneiden sich: " + four.get(i).pos() + " und " + four.get(j).pos());
					}
				}
			}

			// 9. Im Bereich kurz vor der Grenze stehen und schräg auf die Wand schauen: Wand und Linie am Boden davor.
			lookFrom(context, server, 8.5, GROUND, -40.5, new BlockPos(-10, GROUND + 3, -62));
			context.waitTicks(20);
			calm(context);
			screenshotWithoutHud(context, "beaconrange-4-drin");

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

			// 11. Der Bereich reicht auch unter den Beacon: Beacon auf einem Turm, unten endet der Bereich frei in der Luft.
			context.runOnClient(c -> {
				for (BlockPos pos : beacons) {
					BeaconRangeClient.getPins().remove(c, pos);
				}
				BeaconRangeClient.togglePin(c, TOWER);
			});
			// Von schräg unten: Turm, der Bereich endet 15 Blöcke über dem Boden in der Luft, darüber der Himmel.
			lookFrom(context, server, TOWER.getX() + 50.5, GROUND + 6, TOWER.getZ() - 60.5,
					new BlockPos(TOWER.getX(), GROUND + 38, TOWER.getZ()));
			waitForAround(context, world);
			context.waitTicks(40);
			BeaconArea tower = expectVisible(context, TOWER, 2, true);
			if (tower.floor() != TOWER.getY() - 30) {
				throw new AssertionError("Unterkante unter dem Turm-Beacon falsch: " + tower.floor());
			}
			calm(context);
			screenshotWithoutHud(context, "beaconrange-8-unter-dem-beacon");

			// 12. Conduit: anschauen zeigt die Kugel (96 Blöcke bei vollem Rahmen) und den roten Angriffswürfel.
			lookFrom(context, server, CONDUIT.getX() + 4.5, CONDUIT.getY() + 6, CONDUIT.getZ() - 3.5, CONDUIT);
			context.waitFor(c -> c.level.isLoaded(CONDUIT), 1200);
			context.waitTicks(20);
			if (context.computeOnClient(c -> Conduits.frameBlocks(c.level, CONDUIT)) != Conduits.FULL_FRAME) {
				throw new AssertionError("Conduit-Rahmen nicht voll: " + context.computeOnClient(c -> Conduits.frameBlocks(c.level, CONDUIT)));
			}
			expectLookedAt(context, CONDUIT, Conduits.FULL_FRAME);
			calm(context);
			context.takeScreenshot("beaconrange-9b-conduit-nah");
			command(context, "beaconrange pin");
			context.waitTicks(5);
			ConduitArea conduit = expectConduit(context, CONDUIT, 96, true);
			if (!conduit.attacks()) {
				throw new AssertionError("Voller Conduit sollte angreifen");
			}
			if (!read(pinsFile).contains("\"type\": \"conduit\"")) {
				throw new AssertionError("Conduit nicht als Conduit gespeichert: " + read(pinsFile));
			}
			lookFrom(context, server, CONDUIT.getX() + 0.5, GROUND + 60, CONDUIT.getZ() - 180.5,
					new BlockPos(CONDUIT.getX(), GROUND + 30, CONDUIT.getZ()));
			waitForAround(context, world);
			context.waitTicks(40);
			calm(context);
			screenshotWithoutHud(context, "beaconrange-9-conduit");

			// 13. Befehle: Liste, Entfernen (auch im alten Format x,y,z), alle lösen.
			context.runOnClient(c -> {
				BeaconRangeClient.getPins().remove(c, TOWER);
				for (BlockPos pos : beacons) {
					BeaconRangeClient.togglePin(c, pos);
				}
			});
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

			// 14. Einstellungen (mit Beacons in dieser Welt, damit die Zahl dort steht).
			context.runOnClient(c -> {
				BeaconRangeClient.getPins().toggle(c, MAIN, 4, PinStore.Kind.BEACON);
				BeaconRangeClient.getPins().toggle(c, LEVEL3, 3, PinStore.Kind.BEACON);
			});
			context.setScreen(() -> new BeaconRangeConfigScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("beaconrange-7-einstellungen");
			double[] middle = context.computeOnClient(c -> new double[]{c.getWindow().getScreenWidth() / 2.0,
					c.getWindow().getScreenHeight() / 2.0});
			context.getInput().setCursorPos(middle[0], middle[1]);
			context.getInput().scroll(-20);
			context.waitTicks(5);
			context.takeScreenshot("beaconrange-7b-einstellungen-unten");
			context.runOnClient(c -> c.setScreen(null));
			context.waitTicks(5);

			// 15. Wirkt Minecraft genau im gezeichneten Bereich? Echter Beacon mit Eile, Spieler oben, unten, seitlich.
			testVanillaArea(context, server);
			// Und beim Conduit: Kraft bis knapp unter 96 Blöcken, ab 96 nicht mehr (im Regen).
			testVanillaConduit(context, server);
		}

		// Kaputte Einstellungen in der Datei: unbekannte Werte werden zu Standardwerten.
		try {
			Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("KCMods/beaconrange/config.json"),
					"{\"walls\": \"SPIRAL\", \"level2\": null, \"edges\": false, \"conduitColor\": \"RAINBOW\"}");
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		BeaconRangeConfig loaded = BeaconRangeConfig.load();
		if (loaded.walls != BeaconRangeConfig.WallStyle.FADE || loaded.level2 != BeaconRangeConfig.BoxColor.GREEN || loaded.edges
				|| loaded.conduitColor != BeaconRangeConfig.BoxColor.TEAL || !loaded.autoUnpin) {
			throw new AssertionError("Einstellungen nicht repariert: " + loaded.walls + " " + loaded.level2 + " " + loaded.edges
					+ " " + loaded.conduitColor + " " + loaded.autoUnpin);
		}
	}

	/** Erster Start: Tour mit vier Seiten. Danach ein "Update" von 1.0.2: nur "Was ist neu". */
	private static void testOnboarding(ClientGameTestContext context) {
		context.waitForScreen(OnboardingScreen.class);
		context.waitTicks(10);
		expectStep(context, -1);
		context.takeScreenshot("onboarding-1-willkommen");
		for (int step = 0; step < 4; step++) {
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
		context.takeScreenshot("onboarding-6-was-ist-neu");
		context.getInput().pressKey(InputConstants.KEY_RIGHT);
		context.waitTicks(10);
		if (context.computeOnClient(c -> c.screen instanceof OnboardingScreen)) {
			throw new AssertionError("Nach den Neuigkeiten sollte der Screen geschlossen sein");
		}
	}

	private static final String MOD_VERSION = FabricLoader.getInstance().getModContainer(BeaconRangeClient.MOD_ID)
			.orElseThrow().getMetadata().getVersion().getFriendlyString();

	/** Stufe und Reichweite wie in Minecraft; Conduit: 16 Rahmenblöcke 32, 42 Blöcke 96, darunter inaktiv. */
	private static void testRanges() {
		int[] ranges = {Beacons.range(1), Beacons.range(2), Beacons.range(3), Beacons.range(4)};
		if (!Arrays.equals(ranges, new int[]{20, 30, 40, 50})) {
			throw new AssertionError("Reichweiten falsch: " + Arrays.toString(ranges));
		}
		int[] conduit = {Conduits.range(15), Conduits.range(16), Conduits.range(20), Conduits.range(21), Conduits.range(41),
				Conduits.range(42)};
		if (!Arrays.equals(conduit, new int[]{0, 32, 32, 48, 80, 96})) {
			throw new AssertionError("Conduit-Reichweiten falsch: " + Arrays.toString(conduit));
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
		server.runCommand("tp @a " + (beacon.getX() + 0.5) + " 110 " + (beacon.getZ() - 10.5));
		context.waitFor(c -> c.level != null && c.level.isLoaded(beacon), 600);
		context.waitTicks(100); // Strahl aufbauen, Stufe berechnen
		context.runOnClient(c -> BeaconRangeClient.togglePin(c, beacon));
		context.waitTicks(5);
		// Geprüft wird der Bereich, den die Mod wirklich zeichnet (Kasten mit Boden und Decke).
		BeaconArea drawnArea = expectVisible(context, beacon, 1, true);
		AABB area = new AABB(drawnArea.box().minX, drawnArea.floor(), drawnArea.box().minZ,
				drawnArea.box().maxX, drawnArea.ceiling(), drawnArea.box().maxZ);
		if (area.minY != 80 || area.maxY < 320) {
			throw new AssertionError("Bereich falsch berechnet: " + area);
		}
		double x = beacon.getX() + 0.5, z = beacon.getZ() + 0.5;
		expectEffect(context, server, x, 300, z, true, "haste", area.intersects(x - 0.3, 300, z - 0.3, x + 0.3, 301.8, z + 0.3));
		expectEffect(context, server, x, 79, z, true, "haste", area.intersects(x - 0.3, 79, z - 0.3, x + 0.3, 80.8, z + 0.3));
		expectEffect(context, server, x, 78, z, false, "haste", area.intersects(x - 0.3, 78, z - 0.3, x + 0.3, 79.8, z + 0.3));
		expectEffect(context, server, x + 20, 100, z, true, "haste", area.intersects(x + 19.7, 100, z - 0.3, x + 20.3, 101.8, z + 0.3));
		expectEffect(context, server, x + 21, 100, z, false, "haste", area.intersects(x + 20.7, 100, z - 0.3, x + 21.3, 101.8, z + 0.3));

		// Strahl verdeckt (Stein über dem Beacon): Minecraft wirkt nicht mehr, die Mod zeichnet nichts mehr.
		server.runCommand("setblock " + beacon.getX() + " " + (beacon.getY() + 1) + " " + beacon.getZ() + " stone");
		server.runCommand("tp @a " + (beacon.getX() + 0.5) + " 110 " + (beacon.getZ() - 10.5));
		context.waitTicks(100);
		if (context.computeOnClient(c -> Beacons.level(c.level, beacon)) != 0) {
			throw new AssertionError("Beacon mit verdecktem Strahl gilt noch als aktiv");
		}
		if (context.computeOnClient(c -> BeaconRangeClient.getVisible().stream().anyMatch(a -> a.pos().equals(beacon)))) {
			throw new AssertionError("Beacon mit verdecktem Strahl wird noch gezeichnet");
		}
		expectEffect(context, server, x, 100, z + 5, false, "haste", false);
	}

	/** Conduit-Kraft gibt es, wenn der Block des Spielers näher als 96 Blöcke am Conduit ist - genau die Kugel. */
	private static void testVanillaConduit(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("weather rain"); // im Regen wirkt der Conduit auch außerhalb des Wassers
		server.runCommand("forceload add " + CONDUIT.getX() + " " + CONDUIT.getZ());
		context.waitTicks(20);
		double y = CONDUIT.getY();
		double z = CONDUIT.getZ() + 0.5;
		for (int distance : new int[]{95, 96}) {
			double x = CONDUIT.getX() + distance + 0.5;
			boolean drawn = Math.sqrt(Math.pow(x - (CONDUIT.getX() + 0.5), 2)) < Conduits.range(Conduits.FULL_FRAME);
			expectEffect(context, server, x, y, z, distance < 96, "conduit_power", drawn);
		}
		server.runCommand("weather clear");
	}

	/** Spieler (Füße auf y) hinstellen, einen Effekt-Takt abwarten, Effekt prüfen - und ob die Mod dort zeichnet. */
	private static void expectEffect(ClientGameTestContext context, TestServerContext server, double x, double y, double z,
			boolean expected, String effect, boolean drawn) {
		server.runCommand("effect clear @a");
		String tp = "tp @a " + x + " " + y + " " + z;
		server.runCommand(tp);
		context.waitTicks(2);
		context.runOnClient(c -> {
			c.player.getAbilities().flying = true;
			c.player.onUpdateAbilities();
		});
		server.runCommand(tp);
		context.waitTicks(100); // Beacons wirken alle 80 Ticks, Conduits alle 40
		boolean vanilla = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst()
				.hasEffect(effect.equals("haste") ? MobEffects.HASTE : MobEffects.CONDUIT_POWER));
		if (drawn != expected || vanilla != expected) {
			throw new AssertionError(effect + " bei " + x + " " + y + " " + z + ": erwartet " + expected + ", gezeichnet " + drawn
					+ ", Minecraft " + vanilla);
		}
	}

	/** Chunks des Rechtecks dauerhaft laden (höchstens 256 auf einmal) und warten, bis sie da sind. */
	private static void forceload(ClientGameTestContext context, TestServerContext server, int x1, int z1, int x2, int z2) {
		server.runCommand("forceload add " + x1 + " " + z1 + " " + x2 + " " + z2);
		for (int i = 0; i < 400 && !server.computeOnServer(s -> s.overworld().isLoaded(new BlockPos(x1, GROUND, z1))
				&& s.overworld().isLoaded(new BlockPos(x2, GROUND, z2))
				&& s.overworld().isLoaded(new BlockPos(x1, GROUND, z2))
				&& s.overworld().isLoaded(new BlockPos(x2, GROUND, z1))); i++) {
			context.waitTicks(5);
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

	/** Steinsäule vom Boden bis unter die Pyramide, oben die Pyramide mit Beacon. */
	private static void tower(TestServerContext server, BlockPos beacon, int level) {
		int top = beacon.getY() - level - 1;
		server.runCommand("fill " + (beacon.getX() - 1) + " " + GROUND + " " + (beacon.getZ() - 1) + " "
				+ (beacon.getX() + 1) + " " + top + " " + (beacon.getZ() + 1) + " stone_bricks");
		pyramid(server, beacon, level);
	}

	/**
	 * Oben offenes Glasbecken voller Wasser, darin der Conduit mit vollem Rahmen: drei Ringe aus Prismarin (je 5×5,
	 * einer quer zu jeder Achse), zusammen 42 Blöcke. Von oben sieht man durchs Wasser auf den Conduit.
	 */
	private static void aquarium(TestServerContext server, BlockPos conduit) {
		int x = conduit.getX(), y = conduit.getY(), z = conduit.getZ();
		server.runCommand("fill " + (x - 4) + " " + GROUND + " " + (z - 4) + " " + (x + 4) + " " + (y + 3) + " " + (z + 4) + " glass");
		server.runCommand("fill " + (x - 3) + " " + GROUND + " " + (z - 3) + " " + (x + 3) + " " + (y + 3) + " " + (z + 3) + " water");
		server.runCommand("fill " + x + " " + (y - 2) + " " + (z - 2) + " " + x + " " + (y + 2) + " " + (z + 2) + " prismarine_bricks");
		server.runCommand("fill " + (x - 2) + " " + y + " " + (z - 2) + " " + (x + 2) + " " + y + " " + (z + 2) + " dark_prismarine");
		server.runCommand("fill " + (x - 2) + " " + (y - 2) + " " + z + " " + (x + 2) + " " + (y + 2) + " " + z + " prismarine");
		server.runCommand("fill " + (x - 1) + " " + (y - 1) + " " + (z - 1) + " " + (x + 1) + " " + (y + 1) + " " + (z + 1) + " water");
		server.runCommand("setblock " + x + " " + y + " " + z + " conduit[waterlogged=true]");
	}

	/**
	 * Die fertige Szene als Welt "BeaconRangeTest" nach run/saves kopieren - dort öffnet sie ".\gradlew runClient"
	 * zum Selbst-Ausprobieren. Vorher den Spieler an einen guten Startplatz stellen und alles speichern.
	 */
	private static void saveWorldForManualTest(ClientGameTestContext context, TestServerContext server) {
		lookFrom(context, server, 34.5, GROUND + 9, -78.5, MAIN);
		server.runCommand("save-all flush");
		context.waitTicks(20);
		Path source = server.computeOnServer(s -> s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
		// Spiel-Ordner des Tests ist build/run/clientGameTest, der von runClient ist run/ im Projekt.
		Path project = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize().getParent().getParent().getParent();
		Path target = project.resolve("run").resolve("saves").resolve(WORLD_NAME);
		try {
			if (Files.exists(target)) {
				try (Stream<Path> old = Files.walk(target)) {
					for (Path path : old.sorted(Comparator.reverseOrder()).toList()) {
						Files.delete(path);
					}
				}
			}
			try (Stream<Path> files = Files.walk(source)) {
				for (Path path : files.toList()) {
					if (path.getFileName().toString().equals("session.lock")) {
						continue;
					}
					Path copy = target.resolve(source.relativize(path).toString());
					if (Files.isDirectory(path)) {
						Files.createDirectories(copy);
					} else {
						Files.copy(path, copy);
					}
				}
			}
		} catch (IOException e) {
			throw new AssertionError("Testwelt nicht kopiert: " + target, e);
		}
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

	/** Den Schloss-Knopf im Beacon-Fenster mit der Maus anklicken (er hat keine Beschriftung). */
	private static void clickLock(ClientGameTestContext context) {
		double[] target = context.computeOnClient(c -> {
			AbstractWidget lock = lockButton(c);
			if (lock == null) {
				throw new AssertionError("Kein Schloss-Knopf im Beacon-Fenster");
			}
			double scale = c.getWindow().getScreenWidth() / (double) c.getWindow().getGuiScaledWidth();
			return new double[]{(lock.getX() + lock.getWidth() / 2.0) * scale, (lock.getY() + lock.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(target[0], target[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
	}

	private static AbstractWidget lockButton(Minecraft client) {
		if (client.screen == null) {
			return null;
		}
		return Screens.getButtons(client.screen).stream()
				.filter(w -> w.getWidth() == BeaconScreenButton.LOCK_WIDTH && w.getMessage().getString().isEmpty())
				.findFirst().orElse(null);
	}

	/**
	 * Warten, bis jeder Chunk bis 256 Blöcke um den Spieler beim Client angekommen und gezeichnet ist - sonst sieht man
	 * am Horizont weiße Löcher. (Sichtweite im Test: 20 Chunks, also sicher innerhalb.)
	 */
	private static void waitForAround(ClientGameTestContext context, TestSingleplayerContext world) {
		context.waitFor(c -> {
			int px = c.player.getBlockX(), pz = c.player.getBlockZ();
			for (int dx = -256; dx <= 256; dx += 16) {
				for (int dz = -256; dz <= 256; dz += 16) {
					if (dx * dx + dz * dz <= 256 * 256 && !c.level.isLoaded(new BlockPos(px + dx, GROUND, pz + dz))) {
						return false;
					}
				}
			}
			return true;
		}, 2400);
		waitForWorld(context, world);
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

	private static ConduitArea expectConduit(ClientGameTestContext context, BlockPos pos, int range, boolean pinned) {
		List<ConduitArea> visible = context.computeOnClient(c -> BeaconRangeClient.getVisibleConduits());
		Optional<ConduitArea> area = visible.stream().filter(a -> a.pos().equals(pos)).findFirst();
		if (area.isEmpty() || area.get().range() != range || area.get().pinned() != pinned) {
			throw new AssertionError("Erwartet Conduit: " + pos + " Reichweite " + range + (pinned ? " angepinnt" : "")
					+ ", sichtbar sind: " + visible);
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
