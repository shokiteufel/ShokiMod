package com.shokiteufel.shokimod.dungeonkarte;

/**
 * Wo ein Rasterplatz auf der 128 x 128 Punkte grossen Leinwand der Karte liegt. Die Masse sind die der Hypixel-Karte: Raeume zu 16 Punkten,
 * dazwischen Fugen von 4 (ein Raum samt Fuge belegt 20 Punkte, sechs Raeume 116). Reine Rechnung, ohne Minecraft.
 */
public final class KartenGeometrie {

    public static final int LEINWAND = 128;
    public static final int RAUM = 16;
    public static final int FUGE = 4;
    public static final int SCHRITT = RAUM + FUGE;
    /** Breite einer Tuer, die schmaler ist als die Fuge zwischen zwei Raeumen lang ist */
    public static final int TUER = 6;

    private KartenGeometrie() {
    }

    /**
     * Das Rechteck {x, y, breite, hoehe} eines Rasterplatzes, ohne Verschiebung.
     *
     * @param tuer ob der Platz eine Tuer ist (schmaler und in die Mitte der Fuge gesetzt)
     */
    public static int[] rechteck(int zeile, int spalte, boolean tuer) {
        return rechteck(zeile, spalte, tuer, TUER);
    }

    /** Wie {@link #rechteck(int, int, boolean)}, mit einer Tuerbreite nach Wahl ("Door Width" bei Noamm: 6 bis 10) */
    public static int[] rechteck(int zeile, int spalte, boolean tuer, int tuerBreite) {
        boolean zeileGerade = (zeile & 1) == 0;
        boolean spalteGerade = (spalte & 1) == 0;
        if (zeileGerade && spalteGerade) {
            return new int[] {(spalte / 2) * SCHRITT, (zeile / 2) * SCHRITT, RAUM, RAUM};
        }
        if (zeileGerade) {
            // zwischen zwei Raeumen nebeneinander
            int x = ((spalte - 1) / 2) * SCHRITT + RAUM;
            int y = (zeile / 2) * SCHRITT;
            return tuer ? new int[] {x, y + (RAUM - tuerBreite) / 2, FUGE, tuerBreite} : new int[] {x, y, FUGE, RAUM};
        }
        if (spalteGerade) {
            // zwischen zwei Raeumen uebereinander
            int x = (spalte / 2) * SCHRITT;
            int y = ((zeile - 1) / 2) * SCHRITT + RAUM;
            return tuer ? new int[] {x + (RAUM - tuerBreite) / 2, y, tuerBreite, FUGE} : new int[] {x, y, RAUM, FUGE};
        }
        // die Kreuzung von vier Raeumen
        return new int[] {((spalte - 1) / 2) * SCHRITT + RAUM, ((zeile - 1) / 2) * SCHRITT + RAUM, FUGE, FUGE};
    }

    /** Das umschliessende Rechteck {links, oben, rechts, unten} aller belegten Plaetze; ohne Plaetze das ganze 6 x 6 grosse Raster */
    public static int[] begrenzung(Raster raster) {
        int links = Integer.MAX_VALUE;
        int oben = Integer.MAX_VALUE;
        int rechts = Integer.MIN_VALUE;
        int unten = Integer.MIN_VALUE;
        for (int i = 0; i < Raster.PLAETZE; i++) {
            Kachel k = raster.get(i);
            if (k == null) continue;
            int[] r = rechteck(i / Raster.KANTE, i % Raster.KANTE, k.art == Kachel.Art.TUER);
            links = Math.min(links, r[0]);
            oben = Math.min(oben, r[1]);
            rechts = Math.max(rechts, r[0] + r[2]);
            unten = Math.max(unten, r[1] + r[3]);
        }
        if (links == Integer.MAX_VALUE) return new int[] {0, 0, 6 * RAUM + 5 * FUGE, 6 * RAUM + 5 * FUGE};
        return new int[] {links, oben, rechts, unten};
    }

    /** Wie weit die Karte nach rechts und unten geschoben wird, damit das Belegte in der Mitte der Leinwand steht: {x, y} */
    public static int[] verschiebung(int[] begrenzung) {
        int breite = begrenzung[2] - begrenzung[0];
        int hoehe = begrenzung[3] - begrenzung[1];
        return new int[] {(LEINWAND - breite) / 2 - begrenzung[0], (LEINWAND - hoehe) / 2 - begrenzung[1]};
    }

    /**
     * Die Ecke, an der Noamm das Raster beginnen laesst: der Anfang aus der Karte von Hypixel, nur eine 5 wird zur 6 (sein Ausgangswert vor
     * der Eichung ist 5 und gilt als 6: so liegt ein volles Raster von 116 Punkten in den 128 mittig).
     */
    public static int noammEcke(int anfang) {
        return anfang == 5 ? 6 : anfang;
    }

    /** Die Leinwandstelle einer Weltkoordinate (Block) in einer Richtung, ohne Verschiebung; der erste Raum beginnt bei START - 16 */
    public static float weltZuLeinwand(double welt) {
        return (float) ((welt - (Raster.START - RAUM)) * SCHRITT / Raster.RAUM);
    }
}
