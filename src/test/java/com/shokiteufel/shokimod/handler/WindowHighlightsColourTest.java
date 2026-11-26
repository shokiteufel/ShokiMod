package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Die freien Farben der Markierungen (Inventory > Auction house): Ein Platz kann neben Gruen und Gold auch eine Farbe aus dem Farbwaehler tragen. Die festen Farben
 * und ihr Zaehler bleiben, wie sie waren ({@link WindowHighlightsTest}), und eine Hilfe loescht mit {@code clear()} nie die Farben der anderen.
 */
class WindowHighlightsColourTest {

    @BeforeEach
    @AfterEach
    void leer() {
        WindowHighlights.clear();
        WindowHighlights.clearColours();
    }

    @Test
    void aSlotKeepsItsFreeColour() {
        WindowHighlights.markColour(12, 0xFF112233);
        WindowHighlights.markColour(13, 0x80ABCDEF);
        assertEquals(0xFF112233, WindowHighlights.colourOf(12));
        assertEquals(0x80ABCDEF, WindowHighlights.colourOf(13));
        assertEquals(0, WindowHighlights.colourOf(14));
        assertEquals(2, WindowHighlights.colourCount());
    }

    @Test
    void markingTwiceDoesNotCountTwiceAndTheLastColourWins() {
        WindowHighlights.markColour(5, 0xFF000001);
        WindowHighlights.markColour(5, 0xFF000002);
        assertEquals(1, WindowHighlights.colourCount());
        assertEquals(0xFF000002, WindowHighlights.colourOf(5));
    }

    @Test
    void clearingTheColoursRemovesThemAndOnlyThem() {
        WindowHighlights.mark(1, WindowHighlights.GREEN);
        WindowHighlights.markColour(2, 0xFF00FF00);
        WindowHighlights.clearColours();
        assertEquals(0, WindowHighlights.colourCount());
        assertEquals(0, WindowHighlights.colourOf(2));
        assertEquals(1, WindowHighlights.count(), "die festen Farben bleiben");
        assertEquals(WindowHighlights.GREEN, WindowHighlights.kindOf(1));
    }

    @Test
    void clearingTheFixedKindsKeepsTheFreeColours() {
        WindowHighlights.mark(1, WindowHighlights.GOLD);
        WindowHighlights.markColour(2, 0xFFFFAA00);
        // So loescht der Basar in jedem Tick, in dem er kein Fenster des Basars sieht: Die Farben des Auktionshauses duerfen dabei nicht verschwinden
        WindowHighlights.clear();
        assertEquals(0, WindowHighlights.count());
        assertEquals(0, WindowHighlights.kindOf(1));
        assertEquals(1, WindowHighlights.colourCount());
        assertEquals(0xFFFFAA00, WindowHighlights.colourOf(2));
    }

    @Test
    void theFixedKindsDoNotCountTheFreeColours() {
        WindowHighlights.markColour(7, 0xFF123456);
        assertEquals(0, WindowHighlights.count(), "der Basar fragt count(): Eine freie Farbe zaehlt dort nicht");
        assertEquals(0, WindowHighlights.kindOf(7));
        WindowHighlights.mark(7, WindowHighlights.GREEN);
        assertEquals(1, WindowHighlights.count());
        assertEquals(0xFF123456, WindowHighlights.colourOf(7), "beides auf einem Platz: beide bleiben gespeichert");
    }

    @Test
    void impossibleSlotsAndColoursWithoutOpacityAreIgnored() {
        WindowHighlights.markColour(-1, 0xFFFFFFFF);
        WindowHighlights.markColour(500, 0xFFFFFFFF);
        WindowHighlights.markColour(3, 0x00FF0000);
        WindowHighlights.markColour(3, 0);
        assertEquals(0, WindowHighlights.colourCount());
        assertEquals(0, WindowHighlights.colourOf(-1));
        assertEquals(0, WindowHighlights.colourOf(500));
        assertEquals(0, WindowHighlights.colourOf(3));
    }

    @Test
    void theFillIsTheSameColourWithAThirdOfTheOpacityLikeGreenAndGold() {
        // Gruen und Gold der festen Farben: Rand 0xFF, Fuellung 0x55
        assertEquals(0x5555FF55, WindowHighlights.fillOf(0xFF55FF55));
        assertEquals(0x55FFAA00, WindowHighlights.fillOf(0xFFFFAA00));
        assertEquals(0xFF55FF55, WindowHighlights.edgeOf(0xFF55FF55));
        // Eine halbe Deckkraft gibt eine halbe Fuellung
        assertEquals(0x2A112233, WindowHighlights.fillOf(0x80112233));
        assertEquals(0x80112233, WindowHighlights.edgeOf(0x80112233));
    }

    @Test
    void theOldConstantsAreUnchanged() {
        assertEquals(1, WindowHighlights.GREEN);
        assertEquals(2, WindowHighlights.GOLD);
    }
}
