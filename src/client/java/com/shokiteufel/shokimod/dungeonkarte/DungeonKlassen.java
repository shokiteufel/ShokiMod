package com.shokiteufel.shokimod.dungeonkarte;

import com.shokiteufel.shokimod.scanner.TabListScanner;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Welche Klasse ein Spieler im Dungeon spielt - aus der Tab-Liste ("[420] [MVP+] Name (Mage XXV)"), wie NoammAddons sie liest. Die Farben sind
 * seine Vorgaben fuer "ClassColors" (Archer dunkelrot, Berserk gold, Healer dunkellila, Mage dunkelblaugruen, Tank dunkelgruen).
 *
 * Die Tab-Liste wird hoechstens einmal je halbe Sekunde ausgewertet: Gezeichnet wird 60-mal je Sekunde.
 */
public final class DungeonKlassen {

    private static final Pattern ZEILE = Pattern.compile("^\\[\\d+\\] (?:\\[[^\\]]+\\] )*([A-Za-z0-9_]{1,16}) .*\\((\\w+)(?: (\\w+))?\\)$");

    private static Map<String, Integer> farben = Map.of();
    private static long gelesen;

    private DungeonKlassen() {
    }

    /** Die Farbe der Klasse (RGB) von {@code name}, oder null, wenn die Tab-Liste keine nennt */
    public static Integer farbe(String name) {
        long jetzt = System.nanoTime();
        if (jetzt - gelesen > 500_000_000L) {
            farben = lesen(TabListScanner.lastLines());
            gelesen = jetzt;
        }
        return farben.get(name);
    }

    /** Die Klassen aus den Zeilen der Tab-Liste: Name -> Farbe (RGB). Reine Rechnung. */
    static Map<String, Integer> lesen(List<String> zeilen) {
        Map<String, Integer> aus = new HashMap<>();
        if (zeilen == null) return aus;
        for (String z : zeilen) {
            if (z == null) continue;
            Matcher m = ZEILE.matcher(z.strip());
            if (!m.matches()) continue;
            Integer f = farbeDerKlasse(m.group(2));
            if (f != null) aus.put(m.group(1), f);
        }
        return aus;
    }

    static Integer farbeDerKlasse(String klasse) {
        if (klasse == null) return null;
        return switch (klasse.toLowerCase(Locale.ROOT)) {
            case "archer" -> 0xAA0000;
            case "berserk" -> 0xFFAA00;
            case "healer" -> 0xAA00AA;
            case "mage" -> 0x00AAAA;
            case "tank" -> 0x00AA00;
            default -> null;
        };
    }
}
