package com.shokiteufel.shokimod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wo in einem Glacite Mineshaft eine Leiche stehen kann.
 *
 * Die Mineshafts sind keine Zufallshoehlen, sondern sechzehn feste Bauplaene mit je
 * ein bis drei Ausfuehrungen. Innerhalb eines Bauplans liegen die Leichen immer an
 * denselben Stellen - nur welche davon besetzt ist, wechselt. Wer die Stellen kennt,
 * laeuft sie ab, statt den Schacht abzusuchen.
 *
 * Die Liste stammt aus dem Repo von Meowdding (MIT), das auch SkyOcean dafuer
 * benutzt - 110 Punkte, von Spielern zusammengetragen. Geholt wird sie einmal am Tag
 * und liegt zwischen den Starts auf der Platte; so kommen neu gefundene Stellen von
 * selbst dazu, ohne dass diese Mod ein Update braucht.
 */
public final class MineshaftCorpses {

    /**
     * Die Datei im Repo.
     *
     * Aufbau: Bauplan, darin Ausfuehrung, darin die Punkte als "x,y,z".
     * <pre>
     * {"TUNG": {"ONE": ["-114,19,-166", ...]}, ...}
     * </pre>
     */
    public static final RemoteMap<List<BlockPos>> FEED = new RemoteMap<>(
            "mineshaft corpses",
            "https://raw.githubusercontent.com/meowdding/meowdding-repo/master/"
                    + "repo/mining/mineshaft_corpses/mineshaft_corpses.json",
            "mineshaft-corpses.json",
            24 * 60 * 60 * 1000L,
            MineshaftCorpses::parse);

    /**
     * Ein Verweis statt einer Liste.
     *
     * Sechs Schaechte teilen sich dieselben drei Kristall-Stellen; im Repo steht dort
     * deshalb kein Punkt, sondern {"@from": "crystal_positions"}. Aufgeloest wird der
     * Verweis beim Bauen des Repos - wer die Quelldatei liest, muss es selbst tun, und
     * genau das passiert hier.
     */
    private static final String INCLUDE_KEY = "@from";
    private static final String INCLUDE_DEFAULT = "@default";
    private static final String CRYSTAL_FILE = "crystal_positions";

    /** Die geteilten Kristall-Stellen, dieselbe Datei wie im Repo daneben */
    private static final RemoteMap<List<BlockPos>> CRYSTAL = new RemoteMap<>(
            "mineshaft crystal corpses",
            "https://raw.githubusercontent.com/meowdding/meowdding-repo/master/"
                    + "repo/mining/mineshaft_corpses/crystal_positions.json",
            "mineshaft-corpses-crystal.json",
            24 * 60 * 60 * 1000L,
            MineshaftCorpses::parseInclude);

    private MineshaftCorpses() {
    }

    /**
     * Die bekannten Stellen fuer diesen Schacht, oder eine leere Liste.
     *
     * @param type    der Bauplan, vierstellig wie in der Seitenleiste (TUNG, PERI, ...)
     * @param variant die Ausfuehrung, wie sie in der Seitenleiste steht: 1, 2 oder C
     */
    public static List<BlockPos> forShaft(String type, String variant) {
        if (type == null || variant == null) return List.of();
        prefetch();

        List<BlockPos> known = FEED.get(key(type, variant));
        if (known != null && !known.isEmpty()) return known;

        // Die Kristall-Ausfuehrungen teilen sich eine gemeinsame Liste. Beim Einlesen
        // steht die vielleicht noch nicht bereit - die beiden Dateien kommen einzeln -,
        // deshalb wird der Verweis auch hier noch einmal aufgeloest
        if ("CRYSTAL".equals(variantName(variant))) {
            List<BlockPos> shared = CRYSTAL.get(INCLUDE_DEFAULT);
            if (shared != null && !shared.isEmpty()) return shared;
        }
        return List.of();
    }

    public static void prefetch() {
        FEED.prefetch();
        CRYSTAL.prefetch();
    }

    public static boolean ready() {
        return FEED.ready();
    }

    /** "TUNG" + "1" wird zu "TUNG_ONE" - die Datei schreibt die Ausfuehrung aus */
    static String key(String type, String variant) {
        return type.toUpperCase(Locale.ROOT) + "_" + variantName(variant);
    }

    /**
     * Die Seitenleiste kuerzt die Ausfuehrung auf ein Zeichen, die Datei schreibt sie aus.
     *
     * Ein unbekanntes Zeichen bleibt stehen, wie es ist: Kommt eine vierte Ausfuehrung
     * dazu, findet sie sich von selbst, sobald sie in der Datei steht.
     */
    private static String variantName(String variant) {
        return switch (variant.toUpperCase(Locale.ROOT)) {
            case "1" -> "ONE";
            case "2" -> "TWO";
            case "C" -> "CRYSTAL";
            default -> variant.toUpperCase(Locale.ROOT);
        };
    }

    private static Map<String, List<BlockPos>> parse(JsonObject root) {
        Map<String, List<BlockPos>> out = new HashMap<>();
        for (String type : root.keySet()) {
            JsonElement variants = root.get(type);
            if (variants == null || !variants.isJsonObject()) continue;

            JsonObject byVariant = variants.getAsJsonObject();
            for (String variant : byVariant.keySet()) {
                JsonElement value = byVariant.get(variant);
                if (value == null) continue;

                List<BlockPos> points;
                if (value.isJsonArray()) {
                    points = points(value.getAsJsonArray());
                } else if (value.isJsonObject() && value.getAsJsonObject().has(INCLUDE_KEY)) {
                    points = include(value.getAsJsonObject().get(INCLUDE_KEY).getAsString());
                } else {
                    continue;
                }

                if (!points.isEmpty()) {
                    out.put(type.toUpperCase(Locale.ROOT) + "_" + variant.toUpperCase(Locale.ROOT), points);
                }
            }
        }
        return out;
    }

    /**
     * Die Punkte hinter einem Verweis.
     *
     * Bekannt ist bisher nur die Kristall-Datei. Kommt eine zweite dazu, bleibt der
     * Schacht leer und der Grund steht im Log - besser als Punkte zu erfinden.
     */
    private static List<BlockPos> include(String name) {
        if (CRYSTAL_FILE.equals(name)) {
            CRYSTAL.prefetch();
            List<BlockPos> shared = CRYSTAL.get(INCLUDE_DEFAULT);
            return shared == null ? List.of() : shared;
        }
        com.shokiteufel.shokimod.ShokiMod.LOGGER.info(
                "[Mineshaft] unknown corpse include \"{}\" - those spots stay empty", name);
        return List.of();
    }

    /** Die geteilte Datei: ein Schluessel, darunter die Punkte */
    private static Map<String, List<BlockPos>> parseInclude(JsonObject root) {
        Map<String, List<BlockPos>> out = new HashMap<>();
        for (String key : root.keySet()) {
            JsonElement list = root.get(key);
            if (list != null && list.isJsonArray()) out.put(key, points(list.getAsJsonArray()));
        }
        return out;
    }

    /** Jeder Punkt steht als "x,y,z" da; was sich nicht lesen laesst, faellt weg */
    private static List<BlockPos> points(JsonArray array) {
        List<BlockPos> out = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element == null || !element.isJsonPrimitive()) continue;
            String[] parts = element.getAsString().split(",");
            if (parts.length != 3) continue;
            try {
                out.add(new BlockPos(Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim())));
            } catch (NumberFormatException ignored) {
                // Eine krumme Zeile im Repo soll nicht die ganze Datei kosten
            }
        }
        return out;
    }
}
