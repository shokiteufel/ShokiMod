package com.shokiteufel.shokimod.data;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Kaelte aus der Seitenleiste - und die Frage, ob jetzt gewarnt wird.
 *
 * In den kalten Gebieten (Glacite Tunnels, Mineshaft) schreibt Hypixel eine Zeile in die Seitenleiste:
 * "Cold: -45" mit einer Schneeflocke dahinter, der Wert mit Minuszeichen. Bei 100 erfriert man. Die Zeile ist
 * nur dort da, wo Kaelte gilt, und verschwindet, sobald sie abgebaut ist (Lagerfeuer, Tod, Gebietswechsel).
 *
 * Hier steht nur die Rechnung, ohne Spiel dahinter - so laesst sie sich ohne Spiel pruefen. Gewarnt wird einmal,
 * wenn der Wert die Schwelle erreicht; erst wenn er wieder darunter gefallen ist oder die Zeile kurz fehlt,
 * kann es noch einmal kommen. Ist der Wert beim ersten Lesen schon zu hoch (Schalter eben angemacht, Welt
 * betreten), wird sofort gewarnt: Anders als beim Boss-Spawn ist das hier keine Ausgangslage, sondern Gefahr.
 */
public final class ColdWatch {

    /**
     * "Cold: -45" mit oder ohne Schneeflocke dahinter. Das Zeichen wird nicht verlangt: SkyHanni vermerkt, dass
     * Hypixel an dieser Stelle noch das alte benutzt - und was Hypixel hier einmal aendert, soll die Warnung
     * nicht stumm machen. Das Minuszeichen darf auch das echte (U+2212) sein.
     */
    private static final Pattern LINE = Pattern.compile("^Cold:\\s*[-\\u2212]?(?<cold>\\d+)");

    /** So viele Lesungen ohne die Zeile, bis sie als abgebaut gilt - eine Sekunde bei vier Lesungen je Sekunde */
    static final int ABSENT_READS = 4;

    private boolean armed = true;
    private int absent = 0;

    /**
     * Die Kaelte aus den Zeilen der Seitenleiste (ohne Farbcodes), oder -1, wenn keine Zeile da ist.
     * Der Wert ist die Staerke, ohne Vorzeichen.
     */
    public static int read(List<String> plainLines) {
        if (plainLines == null) return -1;
        for (String line : plainLines) {
            if (line == null) continue;
            Matcher m = LINE.matcher(line.trim());
            if (!m.find()) continue;
            try {
                return Integer.parseInt(m.group("cold"));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Eine Lesung einspeisen.
     *
     * @param cold      der gelesene Wert, oder -1 wenn die Zeile fehlt
     * @param threshold ab diesem Wert wird gewarnt (der Wert selbst zaehlt schon)
     * @return wahr genau dann, wenn jetzt gewarnt werden soll
     */
    public boolean feed(int cold, int threshold) {
        if (cold < 0) {
            // Die Seitenleiste baut sich Zeile fuer Zeile neu - eine kurze Luecke ist noch kein Ende
            if (++absent >= ABSENT_READS) armed = true;
            return false;
        }
        absent = 0;
        if (cold < threshold) {
            armed = true;
            return false;
        }
        if (!armed) return false;
        armed = false;
        return true;
    }

    /** Eine neue Welt oder ein abgeschalteter Schalter vergisst, dass schon gewarnt wurde */
    public void reset() {
        armed = true;
        absent = 0;
    }
}
