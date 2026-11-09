package com.shokiteufel.shokimod.dungeonkarte;

import com.shokiteufel.shokimod.dungeonkarte.KartenLeser.Kalibrierung;
import com.shokiteufel.shokimod.dungeonkarte.KartenLeser.Zelle;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Die Hypixel-Karte aus Schiggys Dungeon-Lauf (09.11., Catacombs Floor V): 2552 und 4020 belegte Punkte von 16 384. */
class KartenLeserTest {

    private static byte[] karte(String name) throws IOException {
        try (InputStream in = KartenLeserTest.class.getResourceAsStream("/dungeonkarte/" + name)) {
            return in.readAllBytes();
        }
    }

    private static int belegt(Zelle[] zellen) {
        int n = 0;
        for (Zelle z : zellen) if (z.art() != null) n++;
        return n;
    }

    @Test
    void theEntranceRowFixesTheStartAndTheRoomSize() throws IOException {
        assertEquals(new Kalibrierung(5, 5, 16), KartenLeser.kalibriere(karte("karte_frueh.bin"), null));
        assertEquals(new Kalibrierung(5, 5, 16), KartenLeser.kalibriere(karte("karte_frueh.bin"), 5));
    }

    @Test
    void theSmallFloorsHaveFixedStartCorners() throws IOException {
        byte[] k = karte("karte_frueh.bin");
        assertEquals(new Kalibrierung(22, 22, 16), KartenLeser.kalibriere(k, 0));
        assertEquals(new Kalibrierung(22, 11, 16), KartenLeser.kalibriere(k, 1));
        assertEquals(new Kalibrierung(11, 11, 16), KartenLeser.kalibriere(k, 2));
        assertEquals(new Kalibrierung(11, 11, 16), KartenLeser.kalibriere(k, 3));
    }

    @Test
    void anEmptyMapCannotBeCalibratedYet() {
        assertNull(KartenLeser.kalibriere(new byte[128 * 128], null));
        assertNull(KartenLeser.kalibriere(null, null));
        assertNull(KartenLeser.kalibriere(new byte[10], null));
    }

    @Test
    void theEarlyMapShowsTheEntranceAndItsNeighbours() throws IOException {
        byte[] k = karte("karte_frueh.bin");
        Zelle[] z = KartenLeser.lesen(k, KartenLeser.kalibriere(k, null));
        assertEquals(17, belegt(z));

        Zelle eingang = z[0 * 11 + 2];
        assertEquals(Kachel.Art.RAUM, eingang.art());
        assertEquals(RaumTyp.EINGANG, eingang.typ());
        assertEquals(Zustand.ENTDECKT, eingang.zustand());

        assertEquals(Kachel.Art.RAUM, z[0].art());
        assertEquals(RaumTyp.NORMAL, z[0].typ(), "ein noch nicht betretener Raum erscheint als normaler");
        assertEquals(Kachel.Art.TUER, z[1 * 11 + 0].art(), "darunter eine Tuer");
        assertEquals(Kachel.Art.TRENNER, z[2 * 11 + 1].art(), "zwischen zwei Raeumen in einer Reihe");
        assertEquals(Zelle.LEER, z[5 * 11 + 5], "weiter unten steht noch nichts");
    }

    @Test
    void theMidRunMapAddsRoomsAndAFairy() throws IOException {
        byte[] k = karte("karte_mitte.bin");
        Zelle[] z = KartenLeser.lesen(k, KartenLeser.kalibriere(k, null));
        assertEquals(27, belegt(z));
        Zelle fairy = z[4 * 11 + 8];
        assertEquals(Kachel.Art.RAUM, fairy.art());
        assertEquals(RaumTyp.FAIRY, fairy.typ());
        assertNotNull(z[6 * 11 + 8], "die unterste Reihe der Karte");
        assertEquals(RaumTyp.NORMAL, z[6 * 11 + 8].typ());
    }

    @Test
    void colourNumbersMapToTypes() {
        assertEquals(RaumTyp.BLUT, RaumTyp.vonKartenfarbe(18));
        assertEquals(RaumTyp.FAIRY, RaumTyp.vonKartenfarbe(82));
        assertEquals(RaumTyp.SELTEN, RaumTyp.vonKartenfarbe(34));
        assertEquals(RaumTyp.MINIBOSS, RaumTyp.vonKartenfarbe(74));
        assertEquals(RaumTyp.PUZZLE, RaumTyp.vonKartenfarbe(66));
        assertEquals(RaumTyp.FALLE, RaumTyp.vonKartenfarbe(62));
        assertEquals(RaumTyp.NORMAL, RaumTyp.vonKartenfarbe(63));
        assertEquals(RaumTyp.NORMAL, RaumTyp.vonKartenfarbe(85));
        assertEquals(RaumTyp.EINGANG, RaumTyp.vonKartenfarbe(30));
        assertNull(RaumTyp.vonKartenfarbe(0));
        assertEquals(TuerTyp.WITHER, TuerTyp.vonKartenfarbe(119));
        assertEquals(TuerTyp.BLUT, TuerTyp.vonKartenfarbe(18));
        assertNull(TuerTyp.vonKartenfarbe(5));
    }
}
