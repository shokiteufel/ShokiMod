package com.shokiteufel.shokimod.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Das Forge-Widget der Tab-Liste lesen - ohne Minecraft-Klassen, damit es sich mit Beispielzeilen pruefen laesst.
 *
 * Hypixel schreibt es so: eine Zeile "Forges:", darunter je Platz eine Zeile "1) EMPTY", "3) LOCKED" oder
 * "2) Refined Diamond: 2h 15m", bei einem fertigen Stueck "2) Refined Diamond: Ready!". Es gibt sieben Plaetze;
 * 3 bis 7 sind bis zum Heart of the Mountain 3 bis 7 gesperrt. Das Format ist Skyblockers ForgeWidget abgelesen
 * (siehe CREDITS.md) und auf Hypixel hier noch nicht gesehen - darum meldet {@link ForgeTracker} unverstandene Zeilen
 * ins Log.
 *
 * Die Zeit steht dort auf die Minute AUFgerundet ("1h 14m" heisst mehr als 1h 13m und hoechstens 1h 14m): Ein frisch eingelegtes Stueck
 * steht sofort auf der vollen Dauer ("2h"; sieben von sieben Plaetzen in beiden Tests von Schiggy am 09.11.), und ein Wechsel auf "1h 13m"
 * kommt genau in dem Augenblick, in dem die Restzeit 1h 13m erreicht. Unter einer Minute zeigt der Tab Sekunden ("4s", "8s", "10s"). Und die
 * Zeit laeuft nicht immer in Echtzeit: Beim Ghost-Farmen mit dem Gauntlet sprang die Anzeige alle 21 bis 27 Sekunden um eine Minute
 * (Mitschnitt, 13:17 bis 13:25), in Ruhe alle 60. {@link ForgeTracker} misst dieses Tempo an den Wechseln und rechnet die Zeit dazwischen
 * mit; {@link #erstSchaetzung} und {@link #nachWechsel} geben die Ankerpunkte.
 */
final class ForgeTab {

    static final int MAX_SLOTS = 7;

    private static final String HEADER = "Forges:";
    private static final Pattern SLOT = Pattern.compile("^(\\d)\\)\\s+(.+)$");
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)\\s*([dhms])");

    private ForgeTab() {
    }

    /** Was ein Platz gerade ist. UNKNOWN: die Zeile steht da, aber ihr Format ist uns neu */
    enum Kind { EMPTY, LOCKED, READY, RUNNING, UNKNOWN }

    /**
     * Ein gelesener Platz.
     *
     * @param number  1 bis 7
     * @param item    der Gegenstand, leer bei EMPTY und LOCKED
     * @param shownMs die angezeigte Restzeit, nur bei RUNNING
     * @param raw     die Zeile ohne die Nummer - fuer das Log, wenn sie unverstanden bleibt
     */
    record Slot(int number, Kind kind, String item, long shownMs, String raw) {
    }

    /** Die Plaetze aus den Zeilen der Tab-Liste (ohne Farbcodes). Leer, wenn das Widget nicht da ist */
    static List<Slot> parse(List<String> lines) {
        List<Slot> out = new ArrayList<>();
        if (lines == null) return out;

        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line != null && line.trim().startsWith(HEADER)) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) return out;

        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = SLOT.matcher(line == null ? "" : line.trim());
            // Das Widget endet mit der ersten Zeile, die keine Platzzeile ist
            if (!m.matches()) break;
            int number = Integer.parseInt(m.group(1));
            if (number < 1 || number > MAX_SLOTS) break;
            out.add(slot(number, m.group(2).trim()));
        }
        return out;
    }

    private static Slot slot(int number, String rest) {
        if (rest.equalsIgnoreCase("LOCKED")) return new Slot(number, Kind.LOCKED, "", 0L, rest);
        if (rest.equalsIgnoreCase("EMPTY")) return new Slot(number, Kind.EMPTY, "", 0L, rest);

        // Am letzten ": " teilen: der Gegenstand koennte selbst einen Doppelpunkt enthalten, die Zeit nie
        int cut = rest.lastIndexOf(": ");
        if (cut <= 0) return new Slot(number, Kind.UNKNOWN, "", 0L, rest);
        String item = rest.substring(0, cut).trim();
        String time = rest.substring(cut + 2).trim();
        if (item.isEmpty()) return new Slot(number, Kind.UNKNOWN, "", 0L, rest);
        if (time.equalsIgnoreCase("Ready!") || time.equalsIgnoreCase("Ready")) {
            return new Slot(number, Kind.READY, item, 0L, rest);
        }
        long shown = durationOf(time);
        return shown > 0L ? new Slot(number, Kind.RUNNING, item, shown, rest) : new Slot(number, Kind.UNKNOWN, item, 0L, rest);
    }

    /** "1d 2h 3m 4s" in Millisekunden, 0 wenn nichts erkannt wird */
    static long durationOf(String text) {
        long total = 0L;
        Matcher m = DURATION_PART.matcher(text == null ? "" : text);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "d" -> n * 86_400_000L;
                case "h" -> n * 3_600_000L;
                case "m" -> n * 60_000L;
                default -> n * 1000L;
            };
        }
        return total;
    }

    /**
     * Die Aufloesung einer angezeigten Zeit: Minuten, wenn sie auf volle Minuten lautet, Sekunden, wenn Sekunden dabeistehen. Eine Anzeige
     * von N Minuten heisst: mehr als N - 1 Minuten und hoechstens N. Ab einem Tag zaehlen Stunden.
     */
    static long unitOf(long shownMs) {
        if (shownMs % 60_000L != 0L) return 1000L;
        if (nurStunden(shownMs)) return 3_600_000L;
        if (shownMs < 86_400_000L) return 60_000L;
        return shownMs % 3_600_000L != 0L ? 60_000L : shownMs % 86_400_000L != 0L ? 3_600_000L : 86_400_000L;
    }

    /**
     * Ab zwei Stunden zeigt der Tab nur noch volle Stunden - und rundet dabei AB, nicht auf: Im Mitschnitt vom 09.11. (17:03 bis 17:19) stand
     * dort die ganze Zeit "2h", waehrend das Forge-Fenster "Time Remaining: 2h 20m 24s" nannte. "2h" heisst also: mindestens zwei, weniger als
     * drei Stunden. Unter zwei Stunden steht wieder Stunde und Minute, und dort wird aufgerundet ("1h 59m").
     */
    static boolean nurStunden(long shownMs) {
        return shownMs >= 7_200_000L && shownMs < 86_400_000L && shownMs % 3_600_000L == 0L;
    }

    /**
     * Die erste Schaetzung der Restzeit, wenn die Tab-Liste jetzt {@code shownMs} nennt.
     *
     * Die wahre Restzeit liegt in (shown - Einheit, shown]. Eine bisherige Schaetzung, die darin liegt, bleibt; liegt sie darueber, gilt
     * die obere Grenze; ohne brauchbare Schaetzung (nichts bekannt, oder sie lag darunter) gilt die Mitte. Der erste Wechsel der Anzeige
     * legt die Zeit danach genau fest.
     */
    static long erstSchaetzung(long previousRest, long shownMs) {
        long unit = unitOf(shownMs);
        if (nurStunden(shownMs)) {
            // Abgerundet: die wahre Restzeit liegt in [shown, shown + 1h). Eine bisherige Schaetzung darin bleibt, eine darueber gilt als obere
            // Grenze, sonst die Mitte
            long upper = shownMs + unit;
            if (previousRest >= shownMs && previousRest < upper) return previousRest;
            if (previousRest >= upper) return upper - 1L;
            return shownMs + unit / 2;
        }
        long lower = Math.max(0L, shownMs - unit);
        if (previousRest > lower && previousRest <= shownMs) return previousRest;
        if (previousRest > shownMs) return shownMs;
        return shownMs - unit / 2;
    }

    /**
     * Die Restzeit im Augenblick, in dem die Anzeige auf {@code shownMs} gewechselt ist: genau diese Zeit - abzueglich der Verspaetung, mit
     * der der Tab den Wechsel meldet (er liefert die Zeilen im Abstand von etwa drei Sekunden, im Mittel also anderthalb zu spaet; bei
     * Sekundenanzeige die halbe Sekunde).
     */
    static long nachWechsel(long shownMs) {
        return Math.max(0L, shownMs - Math.min(1500L, unitOf(shownMs) / 2));
    }

    /**
     * Wie {@link #nachWechsel(long)}, mit dem Wert davor: Bei der abgerundeten Stundenanzeige wechselt "3h" auf "2h", wenn die Restzeit unter
     * drei Stunden faellt - der Wechsel geschieht also bei {@code shown + 1h}, nicht bei {@code shown}.
     */
    static long nachWechsel(long vorherMs, long shownMs) {
        if (nurStunden(shownMs) && vorherMs >= shownMs + 3_600_000L) {
            return shownMs + 3_600_000L - 1500L;
        }
        return nachWechsel(shownMs);
    }

    /**
     * Die Grenzen, innerhalb derer die Restzeit liegt, solange der Tab {@code shownMs} nennt: {unten, oben}. Bei Minuten und Sekunden aufgerundet
     * (shown - Einheit, shown], bei der Stundenanzeige ab zwei Stunden abgerundet [shown, shown + 1h).
     */
    static long[] spanne(long shownMs) {
        if (nurStunden(shownMs)) {
            return new long[] {shownMs, shownMs + unitOf(shownMs)};
        }
        return new long[] {Math.max(0L, shownMs - unitOf(shownMs)), shownMs};
    }
}
