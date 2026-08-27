package com.shokiteufel.shokimod.scanner;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Das laufende Event aus der Tab-Liste.
 *
 * Hypixel stellt es in zwei Zeilen: "Event: <Name>" und "Ends In: <Zeit>". Beieinander
 * stehen sie nicht - die Tab-Liste kommt ungeordnet an, siehe processTabList.
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
     * @param lines die Tab-Liste ohne Farbcodes
     */
    public static void processTabList(List<String> lines) {
        String foundName = "";
        String foundTime = "";

        // Beide Zeilen werden unabhaengig voneinander gesucht.
        // Die Tab-Liste kommt aus einer ungeordneten Sammlung: gemessen stand
        // "Ends In: 2m 38s" auf Platz 28, "Event: Starlyn Contests" auf Platz 50.
        // Wer die Zeit unter dem Namen erwartet, findet sie nie.
        for (String raw : lines) {
            String line = raw.trim();

            if (foundName.isEmpty() && line.startsWith(EVENT_PREFIX)) {
                String name = line.substring(EVENT_PREFIX.length()).trim();
                if (!name.isEmpty() && !isEndless(name)) foundName = name;
                continue;
            }

            if (foundTime.isEmpty()) {
                Matcher matcher = ENDS_IN.matcher(line);
                if (matcher.matches()) foundTime = matcher.group("time").trim();
            }
        }

        name = foundName;
        // Ohne Event ist auch die Zeit gegenstandslos
        remaining = foundName.isEmpty() ? "" : foundTime;
    }

    private static boolean isEndless(String eventName) {
        for (String endless : ENDLESS) {
            if (eventName.contains(endless)) return true;
        }
        return false;
    }
}
