package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.ShokiMod;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Der Stand der Besucher im Garden, abgelesen aus der Tab-Liste.
 *
 * Hypixel schreibt ihn dort selbst hin, sobald das Widget eingeschaltet ist:
 *
 * <pre>
 * Visitors: (3)
 * Next Visitor: 3m 27s
 * </pre>
 *
 * Ist die Warteschlange voll, steht hinter "Next Visitor:" stattdessen "Queue Full!" - dann
 * kommt kein weiterer, bis einer bedient ist. Und wer die Besucher noch nicht freigeschaltet hat,
 * liest dort "Not Unlocked!".
 *
 * Wie bei den Fallen gilt: Fehlt das Widget, bleibt hier alles leer. Einmal je Welt steht dann
 * ein Hinweis im Log, sonst wartete man ewig auf eine Warnung, die nie kommt.
 */
public final class GardenVisitors {

    /** So viele Besucher passen in die Warteschlange - mehr kommen nicht */
    public static final int MAXIMUM = 6;

    /** "Visitors: (3)" - wie viele gerade warten */
    private static final Pattern VISITORS = Pattern.compile("Visitors:\\s*\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
    /** "Next Visitor: 3m 27s" oder "Next Visitor: Queue Full!" */
    private static final Pattern NEXT = Pattern.compile("Next Visitor:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    /** So lange wird auf das Widget gewartet, bevor der Hinweis ins Log geht */
    private static final long HINT_AFTER_MILLIS = 60_000L;

    private static int count = 0;
    private static boolean queueFull = false;
    private static boolean notUnlocked = false;
    private static boolean seen = false;
    private static long firstAskedAt = 0L;
    private static boolean hinted = false;
    private static boolean loggedOdd = false;

    private GardenVisitors() {
    }

    /** Wie viele Besucher gerade warten */
    public static int count() {
        return count;
    }

    /** Steht dort "Queue Full!"? Dann ist sie voll, auch wenn die Zahl kleiner aussieht */
    public static boolean queueFull() {
        return queueFull;
    }

    /** Steht dort "Not Unlocked!"? Dann gibt es hier nichts zu warnen */
    public static boolean notUnlocked() {
        return notUnlocked;
    }

    /** Stand die Zeile schon einmal in der Tab-Liste? */
    public static boolean available() {
        return seen;
    }

    /**
     * Sucht die beiden Zeilen in der Tab-Liste.
     *
     * Gesucht wird nach dem Wortlaut, nicht nach der Position - Hypixel schiebt seine Widgets
     * gern um eine Spalte.
     */
    public static void processTabList(List<String> lines) {
        boolean found = false;
        for (String raw : lines) {
            String line = TabText.tidy(raw);
            if (line.isEmpty()) continue;

            Matcher visitors = VISITORS.matcher(line);
            if (visitors.find()) {
                count = number(visitors.group(1));
                found = true;
                continue;
            }

            Matcher next = NEXT.matcher(line);
            if (next.find()) {
                String text = next.group(1).trim().toLowerCase(Locale.ROOT);
                queueFull = text.contains("queue full");
                notUnlocked = text.contains("not unlocked");
                found = true;
            }
        }

        if (found) {
            seen = true;
            hinted = false;
            return;
        }
        logUnreadable(lines);
        // Nichts gefunden: entweder ist der Spieler nicht im Garden, oder das Widget ist aus.
        // Nach einer Minute im Garden ist das zweite die wahrscheinlichere Erklaerung
        if (firstAskedAt == 0L) firstAskedAt = System.currentTimeMillis();
    }

    /**
     * Steht in der Tab-Liste etwas von Besuchern, das nicht erkannt wurde, kommt es einmal je
     * Welt ins Log - samt Zeichencodes.
     */
    private static void logUnreadable(List<String> lines) {
        if (loggedOdd) return;
        for (String raw : lines) {
            if (raw == null || !raw.toLowerCase(Locale.ROOT).contains("visitor")) continue;

            loggedOdd = true;
            ShokiMod.LOGGER.info("[Visitors] A tab line mentions visitors but was not read: \"{}\"", TabText.codes(raw));
            return;
        }
    }

    private static int number(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Wird lange genug im Garden gesucht, ohne dass eine Zeile kam? */
    public static boolean waitingForWidget() {
        return !seen && firstAskedAt > 0L && System.currentTimeMillis() - firstAskedAt >= HINT_AFTER_MILLIS;
    }

    /** Einmal je Welt: der Hinweis, dass das Widget fehlt */
    public static void hintIfMissing() {
        if (seen || hinted || firstAskedAt == 0L) return;
        if (System.currentTimeMillis() - firstAskedAt < HINT_AFTER_MILLIS) return;

        hinted = true;
        ShokiMod.LOGGER.info("[Visitors] No \"Visitors:\" line in the tab list - "
                + "switch the widget on in Hypixel's tab settings, otherwise the reminder has nothing to read");
    }

    public static void reset() {
        count = 0;
        queueFull = false;
        notUnlocked = false;
        seen = false;
        firstAskedAt = 0L;
        hinted = false;
        loggedOdd = false;
    }
}
