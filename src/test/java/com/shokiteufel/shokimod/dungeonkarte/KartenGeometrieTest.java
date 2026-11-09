package com.shokiteufel.shokimod.dungeonkarte;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KartenGeometrieTest {

    @Test
    void roomsSitEvery20PointsAndAre16Wide() {
        assertArrayEquals(new int[] {0, 0, 16, 16}, KartenGeometrie.rechteck(0, 0, false));
        assertArrayEquals(new int[] {20, 0, 16, 16}, KartenGeometrie.rechteck(0, 2, false));
        assertArrayEquals(new int[] {100, 100, 16, 16}, KartenGeometrie.rechteck(10, 10, false));
    }

    @Test
    void connectionsFillTheGapAndDoorsAreNarrowerAndCentred() {
        // zwischen Raum (0,0) und Raum (0,2)
        assertArrayEquals(new int[] {16, 0, 4, 16}, KartenGeometrie.rechteck(0, 1, false));
        assertArrayEquals(new int[] {16, 5, 4, 6}, KartenGeometrie.rechteck(0, 1, true));
        // zwischen Raum (0,0) und Raum (2,0)
        assertArrayEquals(new int[] {0, 16, 16, 4}, KartenGeometrie.rechteck(1, 0, false));
        assertArrayEquals(new int[] {5, 16, 6, 4}, KartenGeometrie.rechteck(1, 0, true));
        // die Kreuzung von vier Raeumen
        assertArrayEquals(new int[] {16, 16, 4, 4}, KartenGeometrie.rechteck(1, 1, false));
    }

    @Test
    void theFullDungeonIs116PointsWideAndSitsCentredWithAnOffsetOf6() {
        Raster raster = new Raster();
        raster.set(0, Kachel.raum(Raster.weltX(0), Raster.weltZ(0), RaumDaten.unbekannt(RaumTyp.NORMAL)));
        raster.set(10 * 11 + 10, Kachel.raum(Raster.weltX(10), Raster.weltZ(10), RaumDaten.unbekannt(RaumTyp.NORMAL)));
        int[] box = KartenGeometrie.begrenzung(raster);
        assertArrayEquals(new int[] {0, 0, 116, 116}, box);
        assertArrayEquals(new int[] {6, 6}, KartenGeometrie.verschiebung(box));
    }

    @Test
    void aSmallerFloorIsCentredToo() {
        Raster raster = new Raster();
        raster.set(2 * 11 + 2, Kachel.raum(Raster.weltX(2), Raster.weltZ(2), RaumDaten.unbekannt(RaumTyp.NORMAL)));
        int[] box = KartenGeometrie.begrenzung(raster);
        assertArrayEquals(new int[] {20, 20, 36, 36}, box);
        // 16 Punkte breit, in der Mitte der 128: (128-16)/2 = 56, abzueglich der eigenen Lage 20
        assertArrayEquals(new int[] {36, 36}, KartenGeometrie.verschiebung(box));
    }

    @Test
    void anEmptyRasterFallsBackToTheWholeGrid() {
        assertArrayEquals(new int[] {0, 0, 116, 116}, KartenGeometrie.begrenzung(new Raster()));
    }

    @Test
    void worldCoordinatesMapOntoTheCanvas() {
        // der Raum links oben beginnt bei Block -201 (Mitte -185) und liegt bei 0 bis 16
        assertEquals(0f, KartenGeometrie.weltZuLeinwand(-201), 0.001f);
        assertEquals(10f, KartenGeometrie.weltZuLeinwand(-185), 0.001f);
        // der naechste Raum beginnt 32 Bloecke weiter bei 20
        assertEquals(20f, KartenGeometrie.weltZuLeinwand(-169), 0.001f);
    }
}
