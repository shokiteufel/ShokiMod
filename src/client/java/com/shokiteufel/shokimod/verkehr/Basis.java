package com.shokiteufel.shokimod.verkehr;

import java.util.Locale;

/** Woraus sich die Einordnung eines Pakets ergibt. Macht sichtbar, wie belastbar sie ist. */
public enum Basis {
    /** Entity-IDs im Paket, verglichen mit der eigenen ID. */
    ID,
    /** Spieler-UUIDs im Paket, verglichen mit der eigenen UUID. */
    UUID,
    /** Das Paket betrifft seiner Art nach den eigenen Spieler (Leben, Inventar, Position ...), ohne ID. */
    IMPLIZIT,
    /** Geprueft: das Paket hat kein Subjekt. */
    KEINE,
    /** Protokoll-Verkehr (Konfiguration, Keep-Alive, Ping ...), kein Spielinhalt. */
    PROTOKOLL,
    /** Nicht in der Tabelle: Luecke, die gesichtet und eingeordnet werden muss. */
    UNBEKANNT,
    /** Vom eigenen Client gesendetes Paket: es gehoert immer zum eigenen Spieler. */
    AUSGEHEND;

    public String kurz() {
        return name().toLowerCase(Locale.ROOT);
    }
}
