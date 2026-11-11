package com.shokiteufel.shokimod.sprung;

import java.util.Locale;

/**
 * Die Klassen des Dungeons in der Reihenfolge von NoammAddons ({@code DungeonClass}): Archer, Berserk, Healer, Mage, Tank - dahinter "leer" fuer
 * einen, dessen Klasse die Tab-Liste (noch) nicht nennt. Die Farben sind seine Vorgaben (dieselben wie bei {@code DungeonKlassen} fuer die Karte).
 */
public enum SprungKlasse {
    ARCHER("Archer", 0xAA0000),
    BERSERK("Berserk", 0xFFAA00),
    HEALER("Healer", 0xAA00AA),
    MAGE("Mage", 0x00AAAA),
    TANK("Tank", 0x00AA00),
    LEER("Empty", 0xAAAAAA);

    /** Der Name, wie ihn das Spiel schreibt */
    public final String anzeige;
    /** Die Farbe (RGB) */
    public final int farbe;

    SprungKlasse(String anzeige, int farbe) {
        this.anzeige = anzeige;
        this.farbe = farbe;
    }

    /** Der Name in Kleinbuchstaben ("archer") - so steht die Klasse in der eigenen Reihenfolge */
    public String klein() {
        return anzeige.toLowerCase(Locale.ROOT);
    }

    /** Die Klasse zu einem Namen ("Mage", gleich wie gross geschrieben); unbekannt ist {@link #LEER} */
    public static SprungKlasse aus(String name) {
        if (name != null) {
            for (SprungKlasse k : values()) {
                if (k.anzeige.equalsIgnoreCase(name)) return k;
            }
        }
        return LEER;
    }
}
