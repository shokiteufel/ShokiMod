package com.shokiteufel.shokimod.dungeonkarte;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class KartenFarbenTest {

    @Test
    void theRoomColoursAreTheHypixelOnes() {
        assertEquals(0xFFFF0000, KartenFarben.raum(RaumTyp.BLUT));
        assertEquals(0xFF007C00, KartenFarben.raum(RaumTyp.EINGANG));
        assertEquals(0xFFB24CD8, KartenFarben.raum(RaumTyp.PUZZLE));
        assertEquals(0xFF72431B, KartenFarben.raum(RaumTyp.NORMAL));
    }

    @Test
    void mixingGoesFromOneColourToTheOther() {
        assertEquals(0xFF000000, KartenFarben.mischen(0xFFFFFFFF, 0xFF000000, 1f));
        assertEquals(0xFFFFFFFF, KartenFarben.mischen(0xFFFFFFFF, 0xFF000000, 0f));
        assertEquals(0xFF808080, KartenFarben.mischen(0xFFFFFFFF, 0xFF000000, 0.5f));
        assertEquals(0xFF000000, KartenFarben.mischen(0xFFFFFFFF, 0xFF000000, 7f), "Anteile ausserhalb von 0 bis 1 werden begrenzt");
    }

    @Test
    void anUnvisitedRoomIsDarkerThanADiscoveredOne() {
        Kachel neu = Kachel.raum(0, 0, RaumDaten.unbekannt(RaumTyp.PUZZLE));
        int unbesucht = KartenFarben.kachel(neu, 50);
        neu.zustand = Zustand.ENTDECKT;
        int besucht = KartenFarben.kachel(neu, 50);
        assertEquals(KartenFarben.raum(RaumTyp.PUZZLE), besucht);
        assertNotEquals(besucht, unbesucht);
        assertEquals(KartenFarben.mischen(besucht, 0xFF000000, 0.5f), unbesucht);
    }

    private static Kachel raumAls(RaumTyp typ) {
        Kachel k = Kachel.raum(0, 0, new RaumDaten("X", typ, 1, new int[0], 0, 0));
        k.zustand = Zustand.ENTDECKT;
        return k;
    }

    @Test
    void aNormalDoorTakesTheColourOfTheSpecialRoomNextToIt() {
        Raster r = new Raster();
        r.set(0, raumAls(RaumTyp.NORMAL));
        Kachel tuer = Kachel.tuer(0, 0, TuerTyp.NORMAL);
        tuer.zustand = Zustand.ENTDECKT;
        r.set(1, tuer);
        r.set(2, raumAls(RaumTyp.PUZZLE));
        assertEquals(KartenFarben.raum(RaumTyp.PUZZLE), KartenFarben.tuer(r, 1, tuer, 50));

        // zwei besondere Raeume, einer davon Fairy: der andere gilt
        r.set(0, raumAls(RaumTyp.FAIRY));
        assertEquals(KartenFarben.raum(RaumTyp.PUZZLE), KartenFarben.tuer(r, 1, tuer, 50));
    }

    @Test
    void aDoorBetweenTwoNormalRoomsIsNormalAndAVerticalOneLooksUpAndDown() {
        Raster r = new Raster();
        r.set(0, raumAls(RaumTyp.NORMAL));
        Kachel waagerecht = Kachel.tuer(0, 0, TuerTyp.NORMAL);
        waagerecht.zustand = Zustand.ENTDECKT;
        r.set(1, waagerecht);
        r.set(2, raumAls(RaumTyp.NORMAL));
        assertEquals(KartenFarben.raum(RaumTyp.NORMAL), KartenFarben.tuer(r, 1, waagerecht, 50));

        // senkrecht: Platz 11 liegt unter Platz 0 und ueber Platz 22
        Kachel senkrecht = Kachel.tuer(0, 0, TuerTyp.NORMAL);
        senkrecht.zustand = Zustand.ENTDECKT;
        r.set(11, senkrecht);
        r.set(22, raumAls(RaumTyp.MINIBOSS));
        assertEquals(KartenFarben.raum(RaumTyp.MINIBOSS), KartenFarben.tuer(r, 11, senkrecht, 50));
    }

    @Test
    void anOpenedWitherDoorLooksNormalAndAnUnvisitedDoorIsDarkened() {
        Raster r = new Raster();
        Kachel wither = Kachel.tuer(0, 0, TuerTyp.WITHER);
        wither.zustand = Zustand.ENTDECKT;
        r.set(1, wither);
        assertEquals(KartenFarben.WITHER_TUER, KartenFarben.tuer(r, 1, wither, 50));
        wither.offen = true;
        assertEquals(KartenFarben.raum(RaumTyp.NORMAL), KartenFarben.tuer(r, 1, wither, 50));

        Kachel blut = Kachel.tuer(0, 0, TuerTyp.BLUT);
        r.set(3, blut);   // noch nicht entdeckt
        assertEquals(KartenFarben.mischen(KartenFarben.raum(RaumTyp.BLUT), 0xFF000000, 0.5f), KartenFarben.tuer(r, 3, blut, 50));
    }

    @Test
    void aClosedUnknownRoomIsGreyAndDoorsHaveTheirOwnColours() {
        Kachel zu = Kachel.raum(0, 0, RaumDaten.unbekannt(RaumTyp.NORMAL));
        zu.zustand = Zustand.GESCHLOSSEN;
        assertEquals(KartenFarben.GESCHLOSSEN, KartenFarben.kachel(zu, 50));

        Kachel wither = Kachel.tuer(0, 0, TuerTyp.WITHER);
        wither.zustand = Zustand.ENTDECKT;
        assertEquals(KartenFarben.WITHER_TUER, KartenFarben.kachel(wither, 50));
        Kachel blut = Kachel.tuer(0, 0, TuerTyp.BLUT);
        blut.zustand = Zustand.ENTDECKT;
        assertEquals(KartenFarben.raum(RaumTyp.BLUT), KartenFarben.kachel(blut, 50));
    }
}
