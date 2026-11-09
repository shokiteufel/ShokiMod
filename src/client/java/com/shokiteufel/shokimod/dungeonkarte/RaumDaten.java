package com.shokiteufel.shokimod.dungeonkarte;

/**
 * Ein Eintrag der Raumtabelle: Name, Art, wie viele Kacheln der Raum belegt, die Kern-Hashes (einer je Kachel) und was in ihm steckt.
 *
 * @param kacheln wie viele Rasterplaetze der Raum belegt (1x1 = 1, 1x2 = 2, L = 3, 2x2 = 4 ...); 0 = unbekannt
 */
public record RaumDaten(String name, RaumTyp typ, int kacheln, int[] kerne, int geheimnisse, int crypts) {

    /** Ein Raum, dessen Hash die Tabelle nicht kennt (oder den nur die Hypixel-Karte zeigt): nur die Art ist bekannt */
    public static RaumDaten unbekannt(RaumTyp typ) {
        return new RaumDaten("Unknown", typ, 0, new int[0], 0, 0);
    }

    public boolean istUnbekannt() {
        return "Unknown".equals(name) && kacheln == 0;
    }
}
