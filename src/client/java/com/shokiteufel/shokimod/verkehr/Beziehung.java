package com.shokiteufel.shokimod.verkehr;

import java.util.Locale;

/** Die vier Arten, in die jedes empfangene Paket eingeordnet wird. */
public enum Beziehung {
    /** Betrifft den eigenen Spieler (eigene Entity-ID, eigene UUID oder eigener Zustand). */
    EIGEN,
    /** Betrifft nur andere Entities oder Spieler. */
    FREMD,
    /** Betrifft den eigenen Spieler und andere zugleich. */
    GEMISCHT,
    /** Hat kein Subjekt (Zeit, Bloecke, Chunks, Meldungen ...). */
    WELT;

    public String kurz() {
        return name().toLowerCase(Locale.ROOT);
    }
}
