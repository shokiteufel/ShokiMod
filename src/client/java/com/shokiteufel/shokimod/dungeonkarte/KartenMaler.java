package com.shokiteufel.shokimod.dungeonkarte;

import static com.shokiteufel.shokimod.dungeonkarte.KartenOption.BACKGROUND;
import static com.shokiteufel.shokimod.dungeonkarte.KartenOption.BORDER_COLOR;
import static com.shokiteufel.shokimod.dungeonkarte.KartenOption.BORDER_WIDTH;
import static com.shokiteufel.shokimod.dungeonkarte.KartenOption.DOOR_WIDTH;

/**
 * Malt den Teil der Funny Map, der sich nur bei einer Aenderung veraendert - Untergrund, Rand, Raeume und Tueren - einmal in einen Pixelpuffer
 * (ARGB, 128 Punkte breit). Das Bild geht danach als Textur auf den Bildschirm: ein Zeichenaufruf statt bis zu 121 Flaechen in jedem Bild.
 * Reine Rechnung ohne Minecraft, deshalb zu pruefen.
 *
 * <p>Gemalt wird mit denselben Regeln wie vorher beim direkten Zeichnen: eine Flaeche liegt ueber den vorigen (Alpha-Mischung), und die
 * Textur liegt danach ueber der Welt - das Ergebnis sieht aus wie die einzelnen Flaechen.
 */
final class KartenMaler {

    static final int BREITE = KartenGeometrie.LEINWAND;
    /** Mit der Zeile unter der Karte ("Show Extra Info Under Map") ist die Leinwand 140 Punkte hoch */
    static final int HOEHE_MAX = 140;

    private KartenMaler() {
    }

    /**
     * @param px     {@code BREITE * HOEHE_MAX} Punkte, wird ganz neu beschrieben
     * @param hoehe  128 oder 140, die Hoehe der Leinwand (der Rest des Puffers bleibt durchsichtig)
     * @param versch Verschiebung des Rasters auf der Leinwand
     */
    static void malen(int[] px, int hoehe, Raster raster, int[] versch, KartenEinstellungen e) {
        java.util.Arrays.fill(px, 0);
        int rand = e.farbe(BORDER_COLOR);
        int dicke = Math.max(1, e.ganz(BORDER_WIDTH));
        flaeche(px, hoehe, 0, 0, BREITE, hoehe, e.farbe(BACKGROUND));
        flaeche(px, hoehe, 0, 0, BREITE, dicke, rand);
        flaeche(px, hoehe, 0, hoehe - dicke, BREITE, hoehe, rand);
        flaeche(px, hoehe, 0, 0, dicke, hoehe, rand);
        flaeche(px, hoehe, BREITE - dicke, 0, BREITE, hoehe, rand);

        int tuerBreite = e.ganz(DOOR_WIDTH);
        for (int i = 0; i < Raster.PLAETZE; i++) {
            Kachel k = raster.get(i);
            if (k == null) continue;
            boolean tuer = k.art == Kachel.Art.TUER;
            int[] r = KartenGeometrie.rechteck(i / Raster.KANTE, i % Raster.KANTE, tuer, tuerBreite);
            int farbe = tuer ? KartenFarben.tuer(raster, i, k, e) : KartenFarben.kachel(k, e);
            flaeche(px, hoehe, versch[0] + r[0], versch[1] + r[1], versch[0] + r[0] + r[2], versch[1] + r[1] + r[3], farbe);
        }
    }

    /** Eine Flaeche von (x0, y0) bis ausschliesslich (x1, y1) in den Puffer, auf die Leinwand beschnitten, gemischt mit dem, was schon dort liegt */
    static void flaeche(int[] px, int hoehe, int x0, int y0, int x1, int y1, int farbe) {
        int von = Math.max(0, x0);
        int bis = Math.min(BREITE, x1);
        int oben = Math.max(0, y0);
        int unten = Math.min(hoehe, y1);
        if (von >= bis || oben >= unten) return;
        int alpha = farbe >>> 24;
        if (alpha == 0) return;
        for (int y = oben; y < unten; y++) {
            int zeile = y * BREITE;
            if (alpha == 255) {
                java.util.Arrays.fill(px, zeile + von, zeile + bis, farbe);
            } else {
                for (int x = von; x < bis; x++) px[zeile + x] = ueber(px[zeile + x], farbe);
            }
        }
    }

    /** {@code oben} ueber {@code unten} (ARGB, nicht vormultipliziert) - dieselbe Mischung wie beim Zeichnen von Flaechen uebereinander */
    static int ueber(int unten, int oben) {
        int ao = oben >>> 24;
        if (ao == 255) return oben;
        if (ao == 0) return unten;
        int au = unten >>> 24;
        if (au == 0) return oben;
        float fo = ao / 255f;
        float fu = au / 255f * (1f - fo);
        float a = fo + fu;
        int r = kanal((oben >> 16) & 0xFF, (unten >> 16) & 0xFF, fo, fu, a);
        int g = kanal((oben >> 8) & 0xFF, (unten >> 8) & 0xFF, fo, fu, a);
        int b = kanal(oben & 0xFF, unten & 0xFF, fo, fu, a);
        return (Math.round(a * 255f) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int kanal(int o, int u, float fo, float fu, float a) {
        return Math.min(255, Math.round((o * fo + u * fu) / a));
    }
}
