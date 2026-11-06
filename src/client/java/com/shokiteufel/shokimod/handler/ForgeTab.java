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
 * Die Zeit steht dort auf die letzte angezeigte Einheit gekuerzt ("2h 15m" heisst zwischen 2h 15m und 2h 16m), wie bei
 * den Effekten: {@link #settle} haelt die Schaetzung des Endes innerhalb dieser Spanne, statt sie jede Minute neu zu setzen.
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

    /** Die kleinste Einheit, die eine angezeigte Zeit noch aufloest: Sekunden, Minuten, Stunden oder Tage */
    static long unitOf(long shownMs) {
        return shownMs % 60_000L != 0L ? 1000L
                : shownMs % 3_600_000L != 0L ? 60_000L
                : shownMs % 86_400_000L != 0L ? 3_600_000L : 86_400_000L;
    }

    /**
     * Wann der Platz fertig ist, wenn die Tab-Liste jetzt {@code shownMs} nennt.
     *
     * Die Anzeige ist auf die letzte Einheit gekuerzt, die wahre Restzeit liegt also in [shown, shown + Einheit). Eine Schaetzung,
     * die darin liegt, bleibt; liegt sie daneben, wird sie an den Rand gezogen. Ohne bisherige Schaetzung ({@code previous} <= 0)
     * gilt die angezeigte Zeit. So wackelt das Ende nicht jede Minute um eine Minute, und eine Aenderung im Spiel (ein neues
     * Stueck, eine Beschleunigung) kommt trotzdem an.
     */
    static long settle(long previous, long shownMs, long now) {
        long upper = shownMs + unitOf(shownMs);
        long estimate = previous <= 0L ? 0L : previous - now;
        long fixed = estimate < shownMs ? shownMs : estimate >= upper ? upper - 1000L : estimate;
        return now + fixed;
    }
}
