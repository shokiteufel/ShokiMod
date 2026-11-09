package com.shokiteufel.shokimod.dungeonkarte;

/**
 * Wann die Funny Map die Karte von Hypixel liest. NoammAddons liest sie nur, wenn ein Kartenpaket kam ({@code MapUpdater}); vorher fragte die
 * Funny Map viermal je Sekunde nach, auch wenn sich nichts geaendert hatte. Jetzt: beim ersten Mal, bei jedem Kartenpaket, und zur Sicherheit
 * alle {@link #SICHERHEIT} Durchgaenge (falls ein Paket verpasst wurde). Laeuft die Paket-Schicht nicht, bleibt es bei jedem Durchgang.
 */
final class KartenTakt {

    /** Alle so viele Durchgaenge (je ein viertel Sekunde) wird auch ohne Paket gelesen: etwa alle zweieinhalb Sekunden */
    static final int SICHERHEIT = 10;

    private boolean neu = true;
    private int seitdem;

    /** Ein Kartenpaket ist angekommen (das Spiel hat es schon verarbeitet): beim naechsten Durchgang lesen */
    void paket() {
        neu = true;
    }

    /** Soll bei diesem Durchgang gelesen werden? {@code ohnePakete}: Die Paket-Schicht laeuft nicht, dann bei jedem */
    boolean lesen(boolean ohnePakete) {
        if (ohnePakete || neu || ++seitdem >= SICHERHEIT) {
            neu = false;
            seitdem = 0;
            return true;
        }
        return false;
    }
}
