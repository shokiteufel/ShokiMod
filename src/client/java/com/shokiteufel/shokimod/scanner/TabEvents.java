package com.shokiteufel.shokimod.scanner;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Das laufende Event aus der Tab-Liste.
 *
 * Hypixel stellt es dort in zwei Zeilen: "Event: <Name>" und darunter "Ends In: <Zeit>".
 * Gelesen wird, was der TabListScanner ohnehin schon einsammelt - ein eigener Durchlauf
 * durch die Spielerliste ist dafuer nicht noetig.
 */
public final class TabEvents {

    private static final Pattern ENDS_IN = Pattern.compile("\\s*Ends In: (?<time>.*)");
    private static final String EVENT_PREFIX = "Event: ";

    /**
     * Dauer-Events, die nicht ablaufen.
     *
     * Fuer sie steht keine sinnvolle Restzeit in der Tab-Liste, deshalb sind sie hier
     * ausgenommen - dieselbe Liste fuehrt CustomScoreboard.
     */
    private static final List<String> ENDLESS = List.of(
            "Carnival", "New Year Celebration", "Spooky Festival", "th SkyBlock Anniversary");

    private static String name = "";
    private static String remaining = "";

    private TabEvents() {
    }

    public static boolean isActive() {
        return !name.isEmpty();
    }

    public static String name() {
        return name;
    }

    /** Restzeit als fertiger Text, so wie Hypixel sie schreibt ("1m 5s") */
    public static String remaining() {
        return remaining;
    }

    public static void reset() {
        name = "";
        remaining = "";
    }

    /**
     * @param lines die Tab-Liste ohne Farbcodes, in der Reihenfolge der Eintraege
     */
    public static void processTabList(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.startsWith(EVENT_PREFIX)) continue;

            String found = line.substring(EVENT_PREFIX.length()).trim();
            if (found.isEmpty() || isEndless(found)) continue;

            // Die Restzeit steht direkt darunter. Weiter unten zu suchen waere riskant:
            // spaeter folgen weitere Abschnitte, die ebenfalls eine Zeit nennen koennen
            String time = "";
            if (i + 1 < lines.size()) {
                Matcher matcher = ENDS_IN.matcher(lines.get(i + 1));
                if (matcher.matches()) time = matcher.group("time").trim();
            }

            name = found;
            remaining = time;
            return;
        }

        // Kein Event in der Liste heisst: gerade laeuft keins
        reset();
    }

    private static boolean isEndless(String eventName) {
        for (String endless : ENDLESS) {
            if (eventName.contains(endless)) return true;
        }
        return false;
    }
}
