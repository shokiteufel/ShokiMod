package com.shokiteufel.shokimod.dungeonkarte;

import org.junit.jupiter.api.Test;

import static com.shokiteufel.shokimod.dungeonkarte.KartenOption.BACKGROUND;
import static com.shokiteufel.shokimod.dungeonkarte.KartenOption.BORDER_COLOR;
import static com.shokiteufel.shokimod.dungeonkarte.KartenOption.BORDER_WIDTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das Kartenbild: Untergrund, Rand, Raeume und Tueren werden einmal in einen Pixelpuffer gemalt (statt in jedem Bild Flaeche fuer Flaeche zu
 * zeichnen). Hier wird geprueft, dass dabei dasselbe herauskommt wie beim Zeichnen der Flaechen.
 */
class KartenMalerTest {

    private static final int B = KartenMaler.BREITE;

    private static int[] puffer() {
        return new int[KartenMaler.BREITE * KartenMaler.HOEHE_MAX];
    }

    private static int punkt(int[] px, int x, int y) {
        return px[y * B + x];
    }

    private static Kachel raum(int spalte, int zeile) {
        return Kachel.raum(Raster.weltX(spalte), Raster.weltZ(zeile), RaumTabelle.standard().nachName("Default"));
    }

    private static KartenEinstellungen deckend() {
        // Untergrund und Rand deckend, damit die Farben in den Punkten einfach abzulesen sind
        return KartenEinstellungen.standard().mit(BACKGROUND, 0xFF102030).mit(BORDER_COLOR, 0xFFAA0000).mit(BORDER_WIDTH, 2);
    }

    @Test
    void backgroundAndBorderAreInTheRightPlaces() {
        int[] px = puffer();
        KartenMaler.malen(px, 128, new Raster(), new int[] {0, 0}, deckend());

        assertEquals(0xFF102030, punkt(px, 60, 60), "die Mitte ist Untergrund");
        assertEquals(0xFFAA0000, punkt(px, 0, 0), "die Ecke ist Rand");
        assertEquals(0xFFAA0000, punkt(px, 1, 60), "der Rand ist zwei Punkte dick");
        assertEquals(0xFF102030, punkt(px, 2, 60), "danach beginnt der Untergrund");
        assertEquals(0xFFAA0000, punkt(px, 127, 127), "unten rechts ist Rand");
        assertEquals(0xFFAA0000, punkt(px, 60, 126), "unten auch");
    }

    @Test
    void theRowsBelowTheMapStayEmptyWithoutTheExtraLine() {
        int[] px = puffer();
        KartenMaler.malen(px, 128, new Raster(), new int[] {0, 0}, deckend());
        assertEquals(0, punkt(px, 60, 130), "unter einer 128 hohen Karte ist nichts");

        KartenMaler.malen(px, 140, new Raster(), new int[] {0, 0}, deckend());
        assertEquals(0xFF102030, punkt(px, 60, 130), "mit der Zeile ist die Karte 140 hoch");
        assertEquals(0xFFAA0000, punkt(px, 60, 139), "und der Rand liegt ganz unten");
    }

    @Test
    void aTileIsPaintedWhereItsRectangleIs() {
        Raster raster = new Raster();
        raster.set(0, raum(0, 0));
        KartenEinstellungen e = deckend();
        int[] r = KartenGeometrie.rechteck(0, 0, false, e.ganz(KartenOption.DOOR_WIDTH));
        int farbe = KartenFarben.kachel(raster.get(0), e);

        int[] px = puffer();
        KartenMaler.malen(px, 128, raster, new int[] {10, 10}, e);

        assertEquals(0xFF, farbe >>> 24, "die Kachelfarbe ist deckend (sonst waere der Vergleich unten nicht eindeutig)");
        assertEquals(farbe, punkt(px, 10 + r[0] + 1, 10 + r[1] + 1), "ein Punkt im Rechteck hat die Farbe der Kachel");
        assertEquals(0xFF102030, punkt(px, 10 + r[0] + r[2] + 3, 10 + r[1] + 3), "rechts daneben ist Untergrund");
    }

    @Test
    void aTileOutsideTheCanvasIsCutOffAndDoesNotCrash() {
        Raster raster = new Raster();
        raster.set(0, raum(0, 0));
        int[] px = puffer();
        KartenMaler.malen(px, 128, raster, new int[] {-500, -500}, deckend());
        assertEquals(0xFF102030, punkt(px, 60, 60));
        KartenMaler.malen(px, 128, raster, new int[] {500, 500}, deckend());
        assertEquals(0xFF102030, punkt(px, 60, 60));
    }

    @Test
    void anOpaqueColourCoversWhatLiesBelow() {
        assertEquals(0xFF336699, KartenMaler.ueber(0xFF102030, 0xFF336699));
    }

    @Test
    void aFullyTransparentColourChangesNothing() {
        assertEquals(0xFF102030, KartenMaler.ueber(0xFF102030, 0x00336699));
    }

    @Test
    void aColourOverNothingStaysAsItIs() {
        assertEquals(0x80336699, KartenMaler.ueber(0, 0x80336699));
    }

    @Test
    void halfTransparentWhiteOverOpaqueBlackIsMidGrey() {
        int mix = KartenMaler.ueber(0xFF000000, 0x80FFFFFF);
        assertEquals(0xFF, mix >>> 24, "ueber Deckendem bleibt es deckend");
        int grau = (mix >> 16) & 0xFF;
        assertTrue(grau >= 126 && grau <= 130, "halb weiss ueber schwarz ist mittelgrau: " + grau);
        assertEquals(grau, (mix >> 8) & 0xFF);
        assertEquals(grau, mix & 0xFF);
    }

    @Test
    void twoHalfTransparentLayersGiveTheSameAsTheirSequentialMix() {
        // Das Kartenbild legt die Flaechen uebereinander und kommt danach als Ganzes ueber die Welt: Das muss dasselbe sein wie jede Flaeche einzeln
        // ueber die Welt zu legen. Pruefung an einer deckenden "Welt"
        int welt = 0xFF204060;
        int a = 0x80FF0000;
        int b = 0x4000FF00;
        int einzeln = KartenMaler.ueber(KartenMaler.ueber(welt, a), b);
        int gebuendelt = KartenMaler.ueber(welt, KartenMaler.ueber(a, b));
        for (int shift = 0; shift <= 16; shift += 8) {
            int x = (einzeln >> shift) & 0xFF;
            int y = (gebuendelt >> shift) & 0xFF;
            assertTrue(Math.abs(x - y) <= 2, "Kanal " + shift + ": " + x + " gegen " + y);
        }
    }

    @Test
    void theSamePictureComesOutWhenNothingChanged() {
        Raster raster = new Raster();
        raster.set(0, raum(0, 0));
        raster.set(4, raum(2, 0));
        int[] a = puffer();
        int[] b = puffer();
        KartenMaler.malen(a, 128, raster, new int[] {4, 4}, deckend());
        KartenMaler.malen(b, 128, raster, new int[] {4, 4}, deckend());
        assertTrue(java.util.Arrays.equals(a, b));
    }

    @Test
    void aChangeOfTheGridChangesThePicture() {
        Raster raster = new Raster();
        int[] vorher = puffer();
        KartenMaler.malen(vorher, 128, raster, new int[] {4, 4}, deckend());
        raster.set(0, raum(0, 0));
        int[] nachher = puffer();
        KartenMaler.malen(nachher, 128, raster, new int[] {4, 4}, deckend());
        assertTrue(!java.util.Arrays.equals(vorher, nachher));
    }

    @Test
    void theGridOnlyCountsWhenSomethingChanges() {
        Raster raster = new Raster();
        raster.set(0, raum(0, 0));
        int stand = raster.stand();

        KartenLeser.Zelle[] karte = new KartenLeser.Zelle[Raster.PLAETZE];
        for (int i = 0; i < karte.length; i++) karte[i] = new KartenLeser.Zelle(null, null, null, Zustand.UNENTDECKT);
        // Dieselbe Karte, wie sie schon im Raster steht: nichts aendert sich, der Stand bleibt
        karte[0] = new KartenLeser.Zelle(Kachel.Art.RAUM, RaumTyp.NORMAL, null, Zustand.UNENTDECKT);
        raster.vonKarte(karte);
        raster.vonKarte(karte);
        assertEquals(stand, raster.stand(), "eine Karte ohne Neues darf den Stand nicht erhoehen");

        karte[0] = new KartenLeser.Zelle(Kachel.Art.RAUM, RaumTyp.NORMAL, null, Zustand.GRUEN);
        raster.vonKarte(karte);
        assertNotEquals(stand, raster.stand(), "ein anderer Zustand ist eine Aenderung");

        int nachGruen = raster.stand();
        raster.vonKarte(karte);
        assertEquals(nachGruen, raster.stand(), "und danach ist es wieder ruhig");

        raster.geaendert();
        assertEquals(nachGruen + 1, raster.stand(), "eine Aenderung von Hand (eine Tuer geht auf) zaehlt");
    }

    @Test
    void aNewTileFromTheMapCountsAsAChange() {
        Raster raster = new Raster();
        int stand = raster.stand();
        KartenLeser.Zelle[] karte = new KartenLeser.Zelle[Raster.PLAETZE];
        for (int i = 0; i < karte.length; i++) karte[i] = new KartenLeser.Zelle(null, null, null, Zustand.UNENTDECKT);
        karte[0] = new KartenLeser.Zelle(Kachel.Art.RAUM, RaumTyp.NORMAL, null, Zustand.UNENTDECKT);
        raster.vonKarte(karte);
        assertNotEquals(stand, raster.stand());
        assertSame(raster.raeume(), raster.raeume());
    }
}
