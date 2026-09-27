package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.ShokiMod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wie viel Essence man hat - abgelesen aus der Tab-Liste.
 *
 * Essence ist kein Gegenstand. Sie liegt im Profil, nicht im Inventar und nicht in
 * einem Sack, und Hypixel schreibt beim Abbauen auch keine Chatzeile dazu. Der
 * Inventar-Vergleich, an dem Profit-Tracker und Schacht-Bilanz haengen, sieht sie
 * deshalb nie - wer Knochenbloecke abbaut, bekommt Fossil Essence und im Kasten
 * steht nichts.
 *
 * Die Tab-Liste kennt den Stand dagegen, sobald das Essence-Widget an ist:
 *
 * <pre>
 * Essence
 *  Fossil: 1,234
 *  Undead: 28,439
 * </pre>
 *
 * Gemeldet wird nicht der Stand, sondern sein Zuwachs: Wer von 1.200 auf 1.234 geht,
 * hat 34 gefunden. Der erste gelesene Stand zaehlt nie mit - sonst stuende beim
 * Einloggen das ganze Profil als Fund im Kasten.
 *
 * Die Zuordnung "Fossil" zu ESSENCE_FOSSIL macht die Mod selbst: Der Basar fuehrt
 * jede Sorte unter ESSENCE_<NAME>, und so wird aus der Zeile eine Ware mit Preis.
 */
public final class EssenceState {

    /** Die Ueberschrift des Widgets */
    private static final Pattern HEADER = Pattern.compile("^\\s*Essence\\s*:?\\s*$", Pattern.CASE_INSENSITIVE);
    /**
     * Eine Zeile darunter: " Fossil: 1,234".
     *
     * Abgekuerzte Zahlen wie "17k" fallen durch - sie haetten keine Genauigkeit, und
     * ein Zuwachs daraus waere geraten.
     */
    private static final Pattern LINE = Pattern.compile("^\\s*(?<type>[A-Za-z ]{3,20}):\\s*(?<amount>[\\d,.]+)\\s*$");
    /** Weiter als so viele Zeilen unter der Ueberschrift steht keine Sorte mehr */
    private static final int MAX_LINES = 20;

    /** Der zuletzt gelesene Stand je Sorte */
    private static final Map<String, Long> totals = new LinkedHashMap<>();

    private EssenceState() {
    }

    /** Beim Wechsel des Profils oder der Welt gilt der naechste Stand wieder als Anfang */
    public static void reset() {
        totals.clear();
    }

    /** Der zuletzt gelesene Stand, fuer die Fehlersuche */
    public static Map<String, Long> totals() {
        return totals;
    }

    /**
     * Die Tab-Liste lesen und den Zuwachs melden.
     *
     * @return was seit dem letzten Lesen dazugekommen ist, als Ware und Stueckzahl
     */
    public static Map<String, Integer> processTabList(List<String> lines) {
        Map<String, Long> gelesen = read(lines);
        if (gelesen.isEmpty()) return Map.of();

        Map<String, Integer> zuwachs = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : gelesen.entrySet()) {
            Long vorher = totals.put(entry.getKey(), entry.getValue());
            if (vorher == null) continue;

            long delta = entry.getValue() - vorher;
            // Nur nach oben: Wer Essence ausgibt, hat nichts gefunden
            if (delta <= 0 || delta > Integer.MAX_VALUE) continue;
            zuwachs.put(itemId(entry.getKey()), (int) delta);
        }
        if (!zuwachs.isEmpty()) ShokiMod.LOGGER.info("[Essence] gained {}", zuwachs);
        return zuwachs;
    }

    /** "Fossil" wird zu ESSENCE_FOSSIL - so fuehrt der Basar sie */
    static String itemId(String type) {
        return "ESSENCE_" + type.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    /** Die Zeilen unter der Ueberschrift, bis etwas kommt, das keine Sorte mehr ist */
    static Map<String, Long> read(List<String> lines) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!HEADER.matcher(lines.get(i)).matches()) continue;

            for (int j = i + 1; j < lines.size() && j <= i + MAX_LINES; j++) {
                Matcher m = LINE.matcher(lines.get(j));
                if (!m.matches()) break;

                try {
                    long amount = Long.parseLong(m.group("amount").replace(",", "").replace(".", ""));
                    out.put(m.group("type").trim(), amount);
                } catch (NumberFormatException e) {
                    break;
                }
            }
            break;
        }
        return out;
    }
}
