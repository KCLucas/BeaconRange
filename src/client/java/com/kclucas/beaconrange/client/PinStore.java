package com.kclucas.beaconrange.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.kclucas.beaconrange.client.onboarding.KcFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Angepinnte Beacons und Conduits, gespeichert pro Welt bzw. Server und Dimension. Zu jedem merkt sie sich die zuletzt
 * gesehene Stufe (beim Conduit: wie viele Rahmenblöcke) - so lässt sich der Bereich auch zeichnen, wenn der Chunk
 * gerade nicht geladen ist.
 */
public final class PinStore {

	public enum Kind {
		BEACON, CONDUIT;

		/** Höchste sinnvolle "Stufe": Beacon-Stufe bzw. Zahl der Rahmenblöcke eines Conduits. */
		public int maxLevel() {
			return this == BEACON ? Beacons.MAX_LEVEL : Conduits.FULL_FRAME;
		}
	}

	/** @param level Beacon-Stufe bzw. beim Conduit die Zahl der Rahmenblöcke (0 = unbekannt/inaktiv) */
	public record Pin(BlockPos pos, int level, Kind kind) {
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(BeaconRangeClient.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	/** Welt -> Dimension -> Beacons */
	private final Map<String, Map<String, List<Pin>>> worlds = new LinkedHashMap<>();

	public PinStore() {
		this(KcFiles.file(BeaconRangeClient.MOD_ID, "pins.json"));
	}

	public PinStore(Path path) {
		this.path = path;
	}

	/**
	 * Wo man gerade spielt: Einzelspieler-Welten über ihren Ordnernamen (eindeutig, anders als der Anzeigename),
	 * Server über ihre Adresse. Null, solange man in keiner Welt ist.
	 */
	public static @Nullable String worldKey(Minecraft client) {
		if (client.level == null) {
			return null;
		}
		IntegratedServer server = client.getSingleplayerServer();
		if (server != null) {
			Path folder = server.getWorldPath(LevelResource.ROOT).normalize().getFileName();
			return "local/" + (folder != null ? folder : server.getWorldData().getLevelName());
		}
		ServerData data = client.getCurrentServer();
		if (data == null) {
			return null;
		}
		if (data.type() == ServerData.Type.REALM) {
			return "realms/" + data.name;
		}
		return "server/" + data.ip.trim().toLowerCase(Locale.ROOT);
	}

	public static String dimension(Level level) {
		return level.dimension().identifier().toString();
	}

	private @Nullable List<Pin> list(Minecraft client, boolean create) {
		String world = worldKey(client);
		if (world == null || client.level == null) {
			return null;
		}
		String dimension = dimension(client.level);
		if (!create) {
			Map<String, List<Pin>> dimensions = this.worlds.get(world);
			return dimensions == null ? null : dimensions.get(dimension);
		}
		return this.worlds.computeIfAbsent(world, k -> new LinkedHashMap<>())
				.computeIfAbsent(dimension, k -> new ArrayList<>());
	}

	/** Die Beacons der aktuellen Dimension (Kopie). */
	public List<Pin> current(Minecraft client) {
		List<Pin> pins = list(client, false);
		return pins == null ? List.of() : List.copyOf(pins);
	}

	public boolean isPinned(Minecraft client, BlockPos pos) {
		List<Pin> pins = list(client, false);
		return pins != null && pins.stream().anyMatch(p -> p.pos().equals(pos));
	}

	/** Anpinnen bzw. wieder lösen. @return true, wenn der Beacon jetzt angepinnt ist */
	public boolean toggle(Minecraft client, BlockPos pos, int level, Kind kind) {
		if (remove(client, pos)) {
			return false;
		}
		List<Pin> pins = list(client, true);
		if (pins == null) {
			return false;
		}
		pins.add(new Pin(pos.immutable(), level, kind));
		save();
		return true;
	}

	public boolean remove(Minecraft client, BlockPos pos) {
		List<Pin> pins = list(client, false);
		if (pins == null || !pins.removeIf(p -> p.pos().equals(pos))) {
			return false;
		}
		cleanUp();
		save();
		return true;
	}

	/** Die Stufe hat sich geändert (Pyramide umgebaut) - für später merken. */
	public void updateLevel(Minecraft client, BlockPos pos, int level) {
		List<Pin> pins = list(client, false);
		if (pins == null) {
			return;
		}
		for (int i = 0; i < pins.size(); i++) {
			if (pins.get(i).pos().equals(pos) && pins.get(i).level() != level) {
				pins.set(i, new Pin(pins.get(i).pos(), level, pins.get(i).kind()));
				save();
			}
		}
	}

	/** Löst alle Beacons dieser Welt, in allen Dimensionen. @return wie viele es waren */
	public int clearWorld(Minecraft client) {
		String world = worldKey(client);
		Map<String, List<Pin>> removed = world == null ? null : this.worlds.remove(world);
		if (removed == null) {
			return 0;
		}
		save();
		return removed.values().stream().mapToInt(List::size).sum();
	}

	/** Angepinnt in dieser Welt, alle Dimensionen. */
	public int countWorld(Minecraft client) {
		String world = worldKey(client);
		Map<String, List<Pin>> dimensions = world == null ? null : this.worlds.get(world);
		return dimensions == null ? 0 : dimensions.values().stream().mapToInt(List::size).sum();
	}

	public int countTotal() {
		return this.worlds.values().stream().flatMap(d -> d.values().stream()).mapToInt(List::size).sum();
	}

	private void cleanUp() {
		this.worlds.values().forEach(dimensions -> dimensions.values().removeIf(List::isEmpty));
		this.worlds.values().removeIf(Map::isEmpty);
	}

	public void load() {
		this.worlds.clear();
		if (!Files.exists(this.path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(this.path)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonObject worlds = root.getAsJsonObject("worlds");
			if (worlds == null) {
				return;
			}
			for (Map.Entry<String, JsonElement> world : worlds.entrySet()) {
				Map<String, List<Pin>> dimensions = new LinkedHashMap<>();
				for (Map.Entry<String, JsonElement> dimension : world.getValue().getAsJsonObject().entrySet()) {
					List<Pin> pins = new ArrayList<>();
					for (JsonElement element : dimension.getValue().getAsJsonArray()) {
						// Ein kaputter Eintrag (z. B. von Hand bearbeitet) kostet nur diesen einen Pin, nicht alle.
						if (!element.isJsonObject() || !element.getAsJsonObject().has("x") || !element.getAsJsonObject().has("y")
								|| !element.getAsJsonObject().has("z")) {
							LOGGER.warn("Überspringe kaputten Pin in {}: {}", this.path, element);
							continue;
						}
						JsonObject pin = element.getAsJsonObject();
						BlockPos pos = new BlockPos(pin.get("x").getAsInt(), pin.get("y").getAsInt(), pin.get("z").getAsInt());
						// ohne "type" (Version 2.0) ist es ein Beacon
						Kind kind = pin.has("type") && "conduit".equals(pin.get("type").getAsString()) ? Kind.CONDUIT : Kind.BEACON;
						int level = pin.has("level") ? Math.clamp(pin.get("level").getAsInt(), 0, kind.maxLevel()) : 0;
						if (pins.stream().noneMatch(p -> p.pos().equals(pos))) {
							pins.add(new Pin(pos, level, kind));
						}
					}
					dimensions.put(dimension.getKey(), pins);
				}
				this.worlds.put(world.getKey(), dimensions);
			}
			cleanUp();
		} catch (IOException | RuntimeException e) {
			// Nicht beim nächsten Speichern überschreiben: die Datei als .broken beiseitelegen, dann leer weitermachen.
			LOGGER.warn("Konnte {} nicht lesen, lege sie als .broken ab und starte ohne Pins", this.path, e);
			this.worlds.clear();
			try {
				Files.move(this.path, this.path.resolveSibling(this.path.getFileName() + ".broken"),
						StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException moveFailed) {
				LOGGER.warn("Konnte {} nicht umbenennen", this.path, moveFailed);
			}
		}
	}

	public void save() {
		JsonObject worlds = new JsonObject();
		this.worlds.forEach((world, dimensions) -> {
			JsonObject dims = new JsonObject();
			dimensions.forEach((dimension, pins) -> {
				JsonArray array = new JsonArray();
				for (Pin pin : pins) {
					JsonObject entry = new JsonObject();
					entry.addProperty("x", pin.pos().getX());
					entry.addProperty("y", pin.pos().getY());
					entry.addProperty("z", pin.pos().getZ());
					entry.addProperty("level", pin.level());
					entry.addProperty("type", pin.kind() == Kind.CONDUIT ? "conduit" : "beacon");
					array.add(entry);
				}
				dims.add(dimension, array);
			});
			worlds.add(world, dims);
		});
		JsonObject root = new JsonObject();
		root.add("worlds", worlds);
		try {
			Files.createDirectories(this.path.getParent());
			// Erst in eine Nebendatei, dann ersetzen: stürzt das Spiel beim Schreiben ab, bleibt die alte Datei ganz.
			Path temporary = this.path.resolveSibling(this.path.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temporary)) {
				GSON.toJson(root, writer);
			}
			try {
				Files.move(temporary, this.path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException atomicNotSupported) {
				Files.move(temporary, this.path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Konnte {} nicht speichern", this.path, e);
		}
	}
}
