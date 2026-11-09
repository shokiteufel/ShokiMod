package com.shokiteufel.shokimod.dungeonkarte;

/**
 * Wie weit ein Raum ist. Die Reihenfolge ist wichtig: Was einem Raum widerfahren kann, steht von "alles erledigt" bis "noch nie gesehen" -
 * {@link Raster#vonKarte} uebernimmt einen Zustand der Hypixel-Karte nur, wenn er weiter vorn steht als der gemerkte.
 */
public enum Zustand {
    GRUEN, GERAEUMT, ENTDECKT, FEHLGESCHLAGEN, GESCHLOSSEN, UNENTDECKT
}
