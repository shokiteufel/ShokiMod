package com.shokiteufel.shokimod.dungeonkarte;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "Only in my room": ein Stern-Mob zaehlt nur im Raum, in dem man steht; ist der Raum nicht bekannt, zeigen alle */
class RaumRegelTest {

    private static final int START = Raster.START;
    private static final int RAUM = Raster.RAUM;

    /** Der Platz (Zeile * 11 + Spalte) der Raummitte in Raumeinheiten */
    private static int platz(int zeile, int spalte) {
        return (2 * zeile) * Raster.KANTE + 2 * spalte;
    }

    @Test
    void aMobInTheSameRoomIsShownAndAMobInAnotherRoomIsNot() {
        List<Integer> mein = List.of(platz(1, 1));
        // Mitte von Raum (Zeile 1, Spalte 1) und Mitte von Raum (Zeile 1, Spalte 2)
        assertTrue(RaumRegel.erlaubt(mein, START + RAUM, START + RAUM));
        assertFalse(RaumRegel.erlaubt(mein, START + 2 * RAUM, START + RAUM));
        assertFalse(RaumRegel.erlaubt(mein, START + RAUM, START + 2 * RAUM));
    }

    @Test
    void aBigRoomOfSeveralPlacesCountsAsOneRoom() {
        // Ein Raum aus zwei Plaetzen nebeneinander (1 x 2): beide Haelften zaehlen, auch der Verbinder dazwischen steht in der Liste
        List<Integer> gross = List.of(platz(0, 0), platz(0, 0) + 1, platz(0, 1));
        assertTrue(RaumRegel.erlaubt(gross, START, START));
        assertTrue(RaumRegel.erlaubt(gross, START + RAUM, START));
        assertFalse(RaumRegel.erlaubt(gross, START + 2 * RAUM, START));
    }

    @Test
    void whenTheRoomIsUnknownEveryMobIsShown() {
        assertTrue(RaumRegel.erlaubt(null, START, START));
        assertTrue(RaumRegel.erlaubt(null, 5000, 5000), "auch weit ausserhalb des Rasters (Boss-Raum)");
    }

    @Test
    void aMobOutsideTheGridIsNotInAnyKnownRoom() {
        List<Integer> mein = List.of(platz(0, 0));
        assertFalse(RaumRegel.erlaubt(mein, START - 200, START));
        assertFalse(RaumRegel.erlaubt(mein, START, START + 500));
    }

    @Test
    void theRoomCellIsThirtyTwoBlocksAroundTheCentre() {
        List<Integer> mein = List.of(platz(2, 3));
        double mx = START + 3 * RAUM;
        double mz = START + 2 * RAUM;
        assertTrue(RaumRegel.erlaubt(mein, mx - 15, mz + 15), "Ecke des Raums");
        assertTrue(RaumRegel.erlaubt(mein, mx + 15, mz - 15), "andere Ecke");
        assertFalse(RaumRegel.erlaubt(mein, mx + 17, mz), "schon im Nachbarraum");
    }
}
