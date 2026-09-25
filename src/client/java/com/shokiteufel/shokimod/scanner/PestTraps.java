package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.ShokiMod;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Der Stand der Pest-Fallen im Garden, abgelesen aus der Tab-Liste.
 *
 * Hypixel schreibt ihn dort selbst hin, sobald das Widget eingeschaltet ist:
 *
 * <pre>
 * Pest Traps: 2/3
 * Full Traps: #1, #2
 * No Bait: None
 * </pre>
 *
 * Damit braucht niemand zu rechnen, wie lange eine Falle bis zur naechsten Pest
 * braucht. Eine solche Rechnung waere ohnehin eine Behauptung: Die Zeit haengt am
 * Koeder und an der Falle, und was davon wie viel ausmacht, steht nirgends. Die
 * Tab-Liste sagt stattdessen, was tatsaechlich drin ist - und das ist immer richtig.
 *
 * Fehlt das Widget, bleibt hier alles leer. Einmal je Welt steht dann ein Hinweis im
 * Log; ohne ihn koennte man ewig auf eine Erinnerung warten, die nie kommt, weil das
 * Spiel die Zahl gar nicht schickt.
 */
public final class PestTraps {

    /** "Pest Traps: 2/3" - wie viele Fallen stehen, und wie viele stehen duerften */
    private static final Pattern TRAPS = Pattern.compile("^Pest Traps:\\s*(\\d+)\\s*/\\s*(\\d+)$");
    /** "Full Traps: None" oder "Full Traps: #1, #2" */
    private static final Pattern FULL = Pattern.compile("^Full Traps:\\s*(.+)$");
    /** "No Bait: None" oder "No Bait: #3" */
    private static final Pattern NO_BAIT = Pattern.compile("^No Bait:\\s*(.+)$");
    private static final Pattern NUMBER = Pattern.compile("#(\\d+)");
    /** So lange wird auf das Widget gewartet, bevor der Hinweis ins Log geht */
    private static final long HINT_AFTER_MILLIS = 60_000L;

    private static int placed = 0;
    private static int maximum = 0;
    private static final List<Integer> full = new ArrayList<>();
    private static final List<Integer> withoutBait = new ArrayList<>();
    private static boolean seen = false;
    private static long firstAskedAt = 0L;
    private static boolean hinted = false;

    private PestTraps() {
    }

    /** Wie viele Fallen gerade voll sind */
    public static int fullCount() {
        return full.size();
    }

    /** Welche - fuer die Zeile "#1, #2" in der Erinnerung */
    public static List<Integer> fullTraps() {
        return List.copyOf(full);
    }

    /** Fallen ohne Koeder. Die fangen nichts, egal wie lange man wartet */
    public static List<Integer> trapsWithoutBait() {
        return List.copyOf(withoutBait);
    }

    public static int placed() {
        return placed;
    }

    public static int maximum() {
        return maximum;
    }

    /** Stand die Zeile schon einmal in der Tab-Liste? */
    public static boolean available() {
        return seen;
    }

    /**
     * Sucht die drei Zeilen in der Tab-Liste.
     *
     * Die Liste kommt ohne Farbcodes und in der Reihenfolge des Bildschirms; gesucht
     * wird nach dem Wortlaut, nicht nach der Position - Hypixel schiebt seine Widgets
     * gern um eine Spalte.
     */
    public static void processTabList(List<String> lines) {
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isEmpty()) continue;

            Matcher traps = TRAPS.matcher(line);
            if (traps.matches()) {
                placed = number(traps.group(1));
                maximum = number(traps.group(2));
                found = true;
                continue;
            }

            Matcher fullLine = FULL.matcher(line);
            if (fullLine.matches()) {
                collect(fullLine.group(1), full);
                found = true;
                continue;
            }

            Matcher bait = NO_BAIT.matcher(line);
            if (bait.matches()) {
                collect(bait.group(1), withoutBait);
                found = true;
            }
        }

        if (found) {
            seen = true;
            hinted = false;
            return;
        }
        // Nichts gefunden: entweder ist der Spieler nicht im Garden, oder das Widget
        // ist aus. Nach einer Minute im Garden ist das zweite die wahrscheinlichere
        // Erklaerung, und die soll nicht im Stillen bleiben
        if (firstAskedAt == 0L) firstAskedAt = System.currentTimeMillis();
    }

    /** "None" heisst keine; sonst stehen dort die Nummern */
    private static void collect(String text, List<Integer> out) {
        out.clear();
        if (text.equalsIgnoreCase("None")) return;

        Matcher numbers = NUMBER.matcher(text);
        while (numbers.find()) out.add(number(numbers.group(1)));
    }

    private static int number(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Einmal je Welt: der Hinweis, dass das Widget fehlt.
     *
     * Wird von der Erinnerung aufgerufen, damit er nur dort steht, wo jemand auf die
     * Zahlen wartet.
     */
    public static void hintIfMissing() {
        if (seen || hinted || firstAskedAt == 0L) return;
        if (System.currentTimeMillis() - firstAskedAt < HINT_AFTER_MILLIS) return;

        hinted = true;
        ShokiMod.LOGGER.info("[Pests] No \"Pest Traps:\" line in the tab list - "
                + "switch the widget on in Hypixel's tab settings, otherwise the reminder has nothing to read");
    }

    public static void reset() {
        placed = 0;
        maximum = 0;
        full.clear();
        withoutBait.clear();
        seen = false;
        firstAskedAt = 0L;
        hinted = false;
    }
}
