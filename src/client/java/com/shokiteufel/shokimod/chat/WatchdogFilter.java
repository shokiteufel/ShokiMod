package com.shokiteufel.shokimod.chat;

/**
 * Chat Filter Types > Watchdog: die Meldung, in der Hypixel sich damit bruestet, wie viele Spieler gesperrt wurden.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code WatchdogHider}). Von dort stammt die Regel: Der Block beginnt mit "[WATCHDOG ANNOUNCEMENT]" und endet
 * mit einer leeren Zeile; hoechstens zehn Zeilen. Die Umsetzung ist eigene Arbeit: Java. Abweichung: SkyHanni erkennt Anfang und Ende an zwei
 * verschieden gefaerbten leeren Zeilen und loescht die leere Zeile VOR dem Block nachtraeglich; im Klartext sehen beide gleich aus, und ein nachtraegliches
 * Loeschen gibt es hier nicht - die leere Zeile davor bleibt (der Schalter "Empty" blendet sie aus), und das Ende ist die erste leere Zeile nach dem
 * ersten Text.
 */
final class WatchdogFilter {

    private static final String ANNOUNCEMENT = "[WATCHDOG ANNOUNCEMENT]";
    private static final int MAX_LINES = 10;
    private static final long MAX_MS = 5000L;

    private boolean inside = false;
    private boolean sawText = false;
    private int lines = 0;
    private long startedAt = 0L;

    void reset() {
        inside = false;
        sawText = false;
        lines = 0;
    }

    /** Eine Zeile; null heisst, der Filter hat dazu nichts zu sagen */
    ChatVerdict line(String line, long now) {
        if (inside && now - startedAt > MAX_MS) reset();
        if (!inside) {
            if (!line.equals(ANNOUNCEMENT)) return null;
            inside = true;
            sawText = false;
            lines = 1;
            startedAt = now;
            return ChatVerdict.HIDE;
        }
        lines++;
        if (line.isBlank()) {
            if (sawText || lines > MAX_LINES) reset();
        } else {
            sawText = true;
            if (lines > MAX_LINES) reset();
        }
        return ChatVerdict.HIDE;
    }
}
