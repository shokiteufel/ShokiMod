package com.shokiteufel.shokimod.dungeonkarte;

/**
 * Ein Platz im 11 x 11 grossen Raster des Dungeons. Gerade Spalten und Zeilen sind Raeume, die ungeraden dazwischen Tueren,
 * Verbindungen grosser Raeume und die Mitte eines 2 x 2 grossen Raums.
 *
 * {@code x} und {@code z} sind Weltkoordinaten des Platzes (Raummitte oder Mitte der Tuer).
 */
public final class Kachel {

    public enum Art {
        /** Die Kachel eines Raums */
        RAUM,
        /** Eine Verbindung zwischen Kacheln desselben grossen Raums (oder die Mitte eines 2 x 2) - gehoert zum Raum, ist aber keine Raummitte */
        TRENNER,
        TUER
    }

    public final Art art;
    public final int x;
    public final int z;
    /** Bei RAUM und TRENNER: der Raum */
    public RaumDaten daten;
    /** Bei TUER: die Art */
    public TuerTyp tuer;
    public Zustand zustand = Zustand.UNENTDECKT;
    /** Bei einer Wither- oder Blut-Tuer: schon aufgemacht? */
    public boolean offen;

    private Kachel(Art art, int x, int z) {
        this.art = art;
        this.x = x;
        this.z = z;
    }

    public static Kachel raum(int x, int z, RaumDaten daten) {
        Kachel k = new Kachel(Art.RAUM, x, z);
        k.daten = daten;
        return k;
    }

    public static Kachel trenner(int x, int z, RaumDaten daten) {
        Kachel k = new Kachel(Art.TRENNER, x, z);
        k.daten = daten;
        return k;
    }

    public static Kachel tuer(int x, int z, TuerTyp typ) {
        Kachel k = new Kachel(Art.TUER, x, z);
        k.tuer = typ;
        return k;
    }

    public boolean istRaumTeil() {
        return art != Art.TUER;
    }
}
