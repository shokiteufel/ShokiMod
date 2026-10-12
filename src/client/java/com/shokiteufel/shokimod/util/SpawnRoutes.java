package com.shokiteufel.shokimod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shokiteufel.shokimod.ShokiMod;

import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Abgelaufene Routen: eine Reihe von Punkten, die man der Reihe nach besucht.
 *
 * Gedacht fuer die Runde in den Glacite Tunnels, mit der man Mineshafts spawnt - der
 * Weg ist immer derselbe, und wer ihn kennt, laeuft ihn ab, statt zu suchen. Die
 * Punkte stammen nicht aus dieser Mod: Sie werden im Spiel abgelaufen und
 * aufgeschrieben, und wer eine bessere Runde hat, legt seine Datei daneben.
 *
 * Gelesen werden zwei Formen, weil die Wegpunkt-Werkzeuge verschiedene schreiben:
 * eine blanke Liste von Punkten, oder ein Objekt mit Namen und einer Liste darin.
 * Ein Punkt braucht x, y und z; Farbe und Name darf er tragen, muss aber nicht.
 */
public final class SpawnRoutes {

    /** Die mitgelieferten Runden im Jar */
    private static final String[][] BUNDLED = {
            {"/assets/shokimod/routes/glacite-spawn.json", "Glacite spawn"},
            {"/assets/shokimod/routes/coal-hollows.json", "Coal Hollows"},
    };
    /** Wo eigene Runden liegen duerfen - eine Datei je Runde */
    private static final String FOLDER = "routes";

    /**
     * Eine Runde: Name, Gebiete, Farbe und ihre Punkte in Reihenfolge.
     *
     * Die Gebiete stehen so da, wie die Seitenleiste sie nennt - "Glacite Tunnels",
     * "Dwarven Base Camp". Mehrere, weil eine Runde ueber eine Zonengrenze laufen
     * kann: Die Spawn-Runde faengt im Basislager an und geht in die Tunnels.
     *
     * Absichtlich die Zone und nicht die Insel: "Dwarven Mines" steht in beiden Zeilen
     * und wuerde die Runde ueber die ganze Insel zeigen.
     *
     * Leer heisst "ueberall, wo ich in der Naehe bin" - die Notloesung fuer Dateien,
     * die ohne Gebiet geschrieben wurden.
     */
    public record Route(String name, List<String> areas, int argb, List<BlockPos> points,
                        String setting, String world) {
    }

    private static List<Route> routes = null;

    private SpawnRoutes() {
    }

    /** Alle bekannten Runden - die mitgelieferte und alles, was im Ordner liegt */
    public static List<Route> all() {
        if (routes == null) routes = load();
        return routes;
    }

    /** Neu einlesen, etwa nachdem jemand eine Datei dazugelegt hat */
    public static void reload() {
        routes = null;
    }

    private static List<Route> load() {
        List<Route> found = new ArrayList<>();

        for (String[] bundled : BUNDLED) {
            try (InputStream in = SpawnRoutes.class.getResourceAsStream(bundled[0])) {
                if (in != null) {
                    Route route = parse(bundled[1], JsonParser.parseReader(
                            new InputStreamReader(in, StandardCharsets.UTF_8)));
                    if (route != null) found.add(route);
                }
            } catch (IOException | RuntimeException e) {
                ShokiMod.LOGGER.warn("[Route] bundled route {} not readable: {}", bundled[1], e.toString());
            }
        }

        Path folder = ModPaths.resolve(FOLDER);
        if (Files.isDirectory(folder)) {
            try (Stream<Path> files = Files.list(folder)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                    try {
                        String name = file.getFileName().toString().replace(".json", "");
                        Route route = parse(name, JsonParser.parseString(Files.readString(file)));
                        if (route != null) found.add(route);
                    } catch (IOException | RuntimeException e) {
                        ShokiMod.LOGGER.warn("[Route] {} not readable: {}", file.getFileName(), e.toString());
                    }
                }
            } catch (IOException e) {
                ShokiMod.LOGGER.warn("[Route] folder {} not readable: {}", folder, e.toString());
            }
        }

        for (Route route : found) {
            ShokiMod.LOGGER.info("[Route] {} with {} points", route.name(), route.points().size());
        }
        return List.copyOf(found);
    }

    /** Beide Formen: eine blanke Liste, oder ein Objekt mit "points" darin */
    static Route parse(String fallbackName, JsonElement json) {
        JsonArray list;
        String name = fallbackName;
        List<String> areas = new ArrayList<>();
        int argb = 0xFF55FF55;
        // Welcher Schalter die Runde einschaltet und in welcher Welt sie gilt (Area-Zeile der Tab-Liste)
        String setting = "spawn";
        String world = "";

        if (json.isJsonArray()) {
            list = json.getAsJsonArray();
        } else if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            JsonElement points = object.has("points") ? object.get("points") : object.get("waypoints");
            if (points == null || !points.isJsonArray()) return null;
            list = points.getAsJsonArray();
            if (object.has("name")) name = object.get("name").getAsString();
            if (object.has("setting")) setting = object.get("setting").getAsString();
            if (object.has("world")) world = object.get("world").getAsString();
            // Beide Schreibweisen: ein Gebiet als Text, oder mehrere als Liste
            if (object.has("area")) areas.add(object.get("area").getAsString());
            if (object.has("areas") && object.get("areas").isJsonArray()) {
                for (JsonElement gebiet : object.getAsJsonArray("areas")) {
                    areas.add(gebiet.getAsString());
                }
            }
        } else {
            return null;
        }

        List<BlockPos> points = new ArrayList<>(list.size());
        boolean farbeGelesen = false;
        for (JsonElement element : list) {
            if (!element.isJsonObject()) continue;
            JsonObject point = element.getAsJsonObject();
            if (!point.has("x") || !point.has("y") || !point.has("z")) continue;
            // Ein Punkt mit null-Koordinaten (so schreibt ihn mancher Exporter am Ende) zaehlt nicht
            if (point.get("x").isJsonNull() || point.get("y").isJsonNull() || point.get("z").isJsonNull()) continue;

            points.add(new BlockPos(point.get("x").getAsInt(), point.get("y").getAsInt(),
                    point.get("z").getAsInt()));
            // Die Farbe des ersten Punktes gilt fuer die Runde: eine Runde, eine Farbe
            if (!farbeGelesen && point.has("r") && point.has("g") && point.has("b")) {
                argb = 0xFF000000
                        | (Math.round(point.get("r").getAsFloat() * 255) << 16)
                        | (Math.round(point.get("g").getAsFloat() * 255) << 8)
                        | Math.round(point.get("b").getAsFloat() * 255);
                farbeGelesen = true;
            }
        }
        return points.isEmpty() ? null : new Route(name, List.copyOf(areas), argb, List.copyOf(points), setting, world);
    }
}
