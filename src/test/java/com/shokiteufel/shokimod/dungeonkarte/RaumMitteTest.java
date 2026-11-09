package com.shokiteufel.shokimod.dungeonkarte;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Wo in einem Raum das Haekchen steht - die Rechnung von NoammAddons ({@code UniqueRoom.center}, {@code topLeft}). */
class RaumMitteTest {

    /** Platz im Raster: Zeile mal 11 plus Spalte */
    private static int p(int zeile, int spalte) {
        return zeile * Raster.KANTE + spalte;
    }

    @Test
    void aSingleRoomIsItsOwnMiddleAndItsOwnCorner() {
        List<Integer> r = List.of(p(2, 4));
        assertArrayEquals(new int[] {4, 2}, RaumMitte.mitte(r));
        assertArrayEquals(new int[] {4, 2}, RaumMitte.linksOben(r));
    }

    @Test
    void twoRoomsNextToEachOtherHaveTheirMiddleBetweenThem() {
        // Zellen (0,0) und (0,2) mit der Verbindung (0,1) dazwischen: die Verbindung zaehlt nicht, die Mitte ist Spalte 1
        List<Integer> r = List.of(p(0, 0), p(0, 1), p(0, 2));
        assertArrayEquals(new int[] {1, 0}, RaumMitte.mitte(r));
        assertArrayEquals(new int[] {0, 0}, RaumMitte.linksOben(r));
    }

    @Test
    void aTwoByTwoRoomHasItsMiddleInTheMiddle() {
        List<Integer> r = List.of(p(0, 0), p(0, 1), p(0, 2), p(1, 0), p(1, 1), p(1, 2), p(2, 0), p(2, 1), p(2, 2));
        assertArrayEquals(new int[] {1, 1}, RaumMitte.mitte(r));
    }

    @Test
    void anLShapedRoomTakesTheRowOfItsBiggestGroup() {
        // Zellen (0,0), (0,2), (2,0): Zeile 0 hat zwei Zellen, Zeile 2 eine -> Zeile 0; Spalte: Mittelwert der Spalten 0 und 2
        List<Integer> r = List.of(p(0, 0), p(0, 1), p(0, 2), p(1, 0), p(2, 0));
        assertArrayEquals(new int[] {1, 0}, RaumMitte.mitte(r));
    }

    @Test
    void theUpperLeftCornerIsTheSmallestColumnThenTheSmallestRow() {
        List<Integer> r = List.of(p(2, 4), p(4, 4), p(4, 6));
        assertArrayEquals(new int[] {4, 2}, RaumMitte.linksOben(r));
        assertArrayEquals(new int[] {4, 2}, RaumMitte.stelle(r, false));
        assertEquals(RaumMitte.mitte(r)[0], RaumMitte.stelle(r, true)[0]);
    }
}
