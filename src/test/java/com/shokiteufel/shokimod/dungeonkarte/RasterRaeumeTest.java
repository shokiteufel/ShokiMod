package com.shokiteufel.shokimod.dungeonkarte;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Die Karte wird in jedem Bild gezeichnet: die Raeume werden nur neu gerechnet, wenn sich das Raster aendert */
class RasterRaeumeTest {

    private static Kachel raum(int spalte, int zeile) {
        return Kachel.raum(Raster.weltX(spalte), Raster.weltZ(zeile), RaumTabelle.standard().nachName("Default"));
    }

    @Test
    void theSameListComesBackWhileNothingChanges() {
        Raster raster = new Raster();
        raster.set(0, raum(0, 0));

        assertSame(raster.raeume(), raster.raeume());
        assertEquals(1, raster.raeume().size());
    }

    @Test
    void aChangeOfTheGridGivesANewListWithTheChange() {
        Raster raster = new Raster();
        raster.set(0, raum(0, 0));
        var vorher = raster.raeume();

        raster.set(4, raum(4, 0));
        assertNotSame(vorher, raster.raeume());
        assertEquals(2, raster.raeume().size());

        raster.leeren();
        assertEquals(0, raster.raeume().size());
    }

    @Test
    void theStateFromTheMapCountsAsAChange() {
        Raster raster = new Raster();
        raster.set(0, raum(0, 0));
        assertEquals(Zustand.UNENTDECKT, raster.raeume().get(0).zustand());

        KartenLeser.Zelle[] karte = new KartenLeser.Zelle[Raster.PLAETZE];
        for (int i = 0; i < karte.length; i++) karte[i] = new KartenLeser.Zelle(null, null, null, Zustand.UNENTDECKT);
        karte[0] = new KartenLeser.Zelle(Kachel.Art.RAUM, RaumTyp.NORMAL, null, Zustand.GRUEN);
        raster.vonKarte(karte);

        assertEquals(Zustand.GRUEN, raster.raeume().get(0).zustand(), "der Raum zeigt den neuen Zustand, nicht den zwischengespeicherten");
    }
}
