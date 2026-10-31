package com.kclucas.beaconrange.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.kclucas.beaconrange.client.onboarding.KcFiles;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class BeaconRangeConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger(BeaconRangeClient.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = KcFiles.file(BeaconRangeClient.MOD_ID, "config.json");

	/** Den Bereich zeigen, solange man auf einen Beacon schaut. */
	public boolean showOnLook = true;
	/** Unter dem Fadenkreuz Stufe und Reichweite des angeschauten Beacons zeigen. */
	public boolean lookInfo = true;
	public WallStyle walls = WallStyle.FADE;
	public Opacity opacity = Opacity.NORMAL;
	public boolean edges = true;
	/** Eine Linie rundherum auf Höhe des Spielers. */
	public boolean heightLine = true;
	public ColorMode colorMode = ColorMode.LEVEL;
	public BoxColor level1 = BoxColor.AQUA;
	public BoxColor level2 = BoxColor.GREEN;
	public BoxColor level3 = BoxColor.GOLD;
	public BoxColor level4 = BoxColor.RED;

	public BoxColor levelColor(int level) {
		return switch (level) {
			case 1 -> this.level1;
			case 2 -> this.level2;
			case 3 -> this.level3;
			default -> this.level4;
		};
	}

	public static BeaconRangeConfig load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				BeaconRangeConfig config = GSON.fromJson(reader, BeaconRangeConfig.class);
				if (config != null) {
					return config.repaired();
				}
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("Konnte {} nicht lesen, nutze Standardwerte", PATH, e);
			}
		}
		return new BeaconRangeConfig();
	}

	/** Unbekannte oder von Hand gelöschte Werte in der Datei wieder auf Standard setzen. */
	private BeaconRangeConfig repaired() {
		BeaconRangeConfig defaults = new BeaconRangeConfig();
		if (this.walls == null) this.walls = defaults.walls;
		if (this.opacity == null) this.opacity = defaults.opacity;
		if (this.colorMode == null) this.colorMode = defaults.colorMode;
		if (this.level1 == null) this.level1 = defaults.level1;
		if (this.level2 == null) this.level2 = defaults.level2;
		if (this.level3 == null) this.level3 = defaults.level3;
		if (this.level4 == null) this.level4 = defaults.level4;
		return this;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			LOGGER.warn("Konnte {} nicht speichern", PATH, e);
		}
	}

	private static String key(Enum<?> value, String group) {
		return "beaconrange." + group + "." + value.name().toLowerCase(Locale.ROOT);
	}

	/** Wie die Wände gezeichnet werden. */
	public enum WallStyle {
		/** Unten kräftig, über dem Beacon nach oben ausblendend. */
		FADE,
		/** Gleichmäßig bis ganz nach oben. */
		FULL,
		/** Nur Kanten. */
		OFF;

		public Component label() {
			return Component.translatable(key(this, "walls"));
		}

		public Component description() {
			return Component.translatable(key(this, "walls") + ".desc");
		}
	}

	public enum Opacity {
		FAINT(0.12F),
		NORMAL(0.22F),
		STRONG(0.36F);

		public final float alpha;

		Opacity(float alpha) {
			this.alpha = alpha;
		}

		public Component label() {
			return Component.translatable(key(this, "opacity"));
		}
	}

	public enum ColorMode {
		LEVEL,
		BEAM;

		public Component label() {
			return Component.translatable(key(this, "color_mode"));
		}

		public Component description() {
			return Component.translatable(key(this, "color_mode") + ".desc");
		}
	}

	/** Farben zur Auswahl, kräftig genug für Wände und Linien. */
	public enum BoxColor {
		AQUA(0x66CCFF),
		BLUE(0x4D7CFF),
		GREEN(0x66FF80),
		LIME(0xB8FF3C),
		YELLOW(0xFFF04D),
		GOLD(0xFFD24D),
		ORANGE(0xFF9433),
		RED(0xFF5C5C),
		PINK(0xFF77D9),
		PURPLE(0xB27CFF),
		WHITE(0xF2F2F2);

		public final int rgb;

		BoxColor(int rgb) {
			this.rgb = rgb;
		}

		/** Der Name in seiner eigenen Farbe. */
		public Component label() {
			return Component.translatable(key(this, "color")).withColor(this.rgb);
		}
	}
}
