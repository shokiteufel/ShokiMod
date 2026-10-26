package com.shokiteufel.shokimod.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Woran die Mod ein Pet erkennt - ohne Spiel dahinter, damit sich die Zuordnung ohne Spiel pruefen laesst.
 *
 * Fruehere Fassungen kannten ein Pet nur an seinem Namen. Wer zwei Golden Dragons hat - einen mit Skin, einen ohne,
 * oder mit verschiedenen Pet-Items -, hatte deshalb nur einen Eintrag fuer beide: dasselbe Bild, und die Erfahrung
 * des einen stand beim anderen, samt einer erfundenen Meldung "leveled up to level 532".
 *
 * Jetzt gehoert zu jedem Pet ein Schluessel aus drei Merkmalen: der Name, ob ein Skin dran ist (ein Zeichen hinter dem
 * Namen oder in der Klammer davor) und das Pet-Item. Name und Skin nennen Tab-Liste, Chatzeile des Autopets und Pet-Menue;
 * das Item nur die Chatzeile (im Mauszeiger-Text) und das Menue - die Tab-Liste zeigt es nicht (Protokoll vom 26.10.2026).
 *
 * <pre>
 *   GOLDEN DRAGON                  ohne Skin, ohne Item
 *   GOLDEN DRAGON|*                mit Skin, ohne Item
 *   GOLDEN DRAGON|Lucky Clover     ohne Skin, mit Item
 *   GOLDEN DRAGON|*|Lucky Clover   mit Skin und Item
 * </pre>
 *
 * Der Name steht gross geschrieben (so waren die Schluessel schon immer: ein Eintrag aus einer alten Fassung gilt
 * damit weiter fuer ein Pet ohne Skin und Item), das Item so, wie es das Spiel schreibt.
 */
public final class PetIdentity {

    /** Mehr Stufen auf einmal sind kein Aufstieg, sondern ein anderes Pet oder eine alte Zahl - die Erfahrung kostet 1.886.700 je Stufe */
    static final int MAX_LEVEL_STEP = 3;

    private PetIdentity() {
    }

    /**
     * Das Ergebnis einer Zuordnung.
     *
     * @param key       der Schluessel, unter dem das Pet gefuehrt wird
     * @param known     stimmt mit einem schon gemerkten Pet ueberein (sonst neu)
     * @param ambiguous mehrere gemerkte Pets passen gleich gut - die Zuordnung ist geraten
     */
    public record Resolution(String key, boolean known, boolean ambiguous) {
    }

    /** Der Schluessel ohne Item: Name und Skin */
    public static String baseKey(String name, boolean skin) {
        String upper = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
        return skin ? upper + "|*" : upper;
    }

    /** Der volle Schluessel. Ein leeres Item heisst: keines */
    public static String key(String name, boolean skin, String item) {
        String base = baseKey(name, skin);
        String clean = item == null ? "" : item.trim();
        return clean.isEmpty() ? base : base + "|" + clean;
    }

    /**
     * Ein Schluessel in der festen Schreibweise: der Name gross, der Rest unveraendert. Ein blosser Name
     * ("Ammonite", wie ihn aeltere Stellen der Mod geben) wird zu "AMMONITE".
     */
    public static String canon(String key) {
        if (key == null) return "";
        String text = key.trim();
        int bar = text.indexOf('|');
        if (bar < 0) return text.toUpperCase(Locale.ROOT);
        return text.substring(0, bar).toUpperCase(Locale.ROOT) + text.substring(bar);
    }

    public static String nameOf(String key) {
        String text = canon(key);
        int bar = text.indexOf('|');
        return bar < 0 ? text : text.substring(0, bar);
    }

    public static boolean skinOf(String key) {
        String text = canon(key);
        return text.contains("|*") && (text.endsWith("|*") || text.contains("|*|"));
    }

    /** Das Item im Schluessel, oder leer */
    public static String itemOf(String key) {
        String text = canon(key);
        int bar = text.indexOf('|');
        if (bar < 0) return "";
        String rest = text.substring(bar + 1);
        if (rest.equals("*")) return "";
        if (rest.startsWith("*|")) return rest.substring(2);
        return rest;
    }

    /** Dasselbe Item? Verglichen ohne Gross/Klein; ein Vorsatz davor ("Poignant Lucky Clover") ist dasselbe Item wie ohne */
    public static boolean sameItem(String a, String b) {
        String x = norm(a);
        String y = norm(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        if (x.equals(y)) return true;
        String longer = x.length() >= y.length() ? x : y;
        String shorter = x.length() >= y.length() ? y : x;
        return longer.endsWith(" " + shorter);
    }

    private static String norm(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * Welches der gemerkten Pets ist gemeint?
     *
     * @param known die gemerkten Schluessel
     * @param item  das Item, wie die Quelle es nennt: null = die Quelle sagt nichts dazu (die Chatzeile ohne
     *              Mauszeiger-Text), leer = die Quelle sagt, dass keines getragen wird
     */
    public static Resolution resolve(Collection<String> known, String name, boolean skin, String item) {
        String base = baseKey(name, skin);
        List<String> same = new ArrayList<>();
        for (String k : known) {
            String c = canon(k);
            if (baseKey(nameOf(c), skinOf(c)).equals(base)) same.add(c);
        }
        same.sort(String::compareTo);

        if (item == null) {
            if (same.isEmpty()) return new Resolution(base, false, false);
            // Ein Eintrag ohne Item ist oft nur ein Platzhalter (das gemerkte Bild des Pets, ein Ueberschuss ohne Pet-Item): Gibt
            // es Pets mit Item, zaehlen nur die - sonst waere ein einziges Pet mit Item schon mehrdeutig
            List<String> mitItem = new ArrayList<>();
            for (String k : same) if (!itemOf(k).isEmpty()) mitItem.add(k);
            List<String> pool = mitItem.isEmpty() ? same : mitItem;
            if (pool.size() == 1) return new Resolution(pool.get(0), true, false);
            // Mehrere Pets dieser Art: ohne das Item laesst sich nicht sagen, welches. Dann lieber keines zeigen als ein falsches
            // Item: der Schluessel ohne Item, bis die Erfahrung aus der Tab-Liste das Pet nennt (siehe PetBook)
            return new Resolution(base, true, true);
        }

        String clean = item.trim();
        if (clean.isEmpty()) return new Resolution(base, same.contains(base), false);
        for (String k : same) {
            String have = itemOf(k);
            if (!have.isEmpty() && sameItem(have, clean)) return new Resolution(k, true, false);
        }
        return new Resolution(base + "|" + clean, false, false);
    }

    /**
     * Ist der Sprung von {@code before} auf {@code after} ein Aufstieg, den man melden darf?
     * Einer bis drei Stufen schon - mehr heisst: eine andere Zahl, nicht ein Aufstieg.
     */
    public static boolean plausibleLevelUp(int before, int after) {
        return before > 0 && after > before && after - before <= MAX_LEVEL_STEP;
    }

    /**
     * Wo nach dem Bild gesucht wird: erst genau dieses Pet, dann Name und Skin ohne Item (das Item aendert das Bild
     * nicht), bei einem Pet ohne Skin zuletzt der blosse Name. Ein Pet MIT Skin faellt nie auf das ohne zurueck -
     * das war der Fehler.
     */
    public static List<String> iconKeys(String key) {
        String c = canon(key);
        List<String> out = new ArrayList<>(3);
        out.add(c);
        String base = baseKey(nameOf(c), skinOf(c));
        if (!out.contains(base)) out.add(base);
        return out;
    }
}
