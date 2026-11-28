package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Die Markierungen hinter einzelnen Plaetzen (Purchase helper, Order helper). */
class WindowHighlightsTest {

    @BeforeEach
    @AfterEach
    void leer() {
        WindowHighlights.clear();
    }

    @Test
    void aMarkedSlotKeepsItsKind() {
        WindowHighlights.mark(12, WindowHighlights.GREEN);
        WindowHighlights.mark(13, WindowHighlights.GOLD);
        assertEquals(WindowHighlights.GREEN, WindowHighlights.kindOf(12));
        assertEquals(WindowHighlights.GOLD, WindowHighlights.kindOf(13));
        assertEquals(0, WindowHighlights.kindOf(14));
        assertEquals(2, WindowHighlights.count());
    }

    @Test
    void markingTwiceDoesNotCountTwice() {
        WindowHighlights.mark(5, WindowHighlights.GREEN);
        WindowHighlights.mark(5, WindowHighlights.GOLD);
        assertEquals(1, WindowHighlights.count());
        assertEquals(WindowHighlights.GOLD, WindowHighlights.kindOf(5), "die letzte Farbe gilt");
    }

    @Test
    void clearingRemovesEverything() {
        WindowHighlights.mark(1, WindowHighlights.GREEN);
        WindowHighlights.mark(2, WindowHighlights.GOLD);
        WindowHighlights.clear();
        assertEquals(0, WindowHighlights.count());
        assertEquals(0, WindowHighlights.kindOf(1));
        assertEquals(0, WindowHighlights.kindOf(2));
    }

    // ---- seit 2.6.10: gleichmaessig und deckend gefuellt (Meldung des Users 28.11.: der Glow gefaellt nicht)

    @Test
    void theFixedColoursFillTheSlotEvenlyAndOpaque() {
        int green = WindowHighlights.fillOfKind(WindowHighlights.GREEN);
        int gold = WindowHighlights.fillOfKind(WindowHighlights.GOLD);
        assertEquals(0xFF, green >>> 24, "deckend, nicht durchscheinend");
        assertEquals(0xFF, gold >>> 24, "deckend, nicht durchscheinend");
        // Die Farben von SkyHanni: LorenzColor.GREEN (85, 255, 85) und GOLD (255, 170, 0)
        assertEquals(0x55FF55, green & 0xFFFFFF);
        assertEquals(0xFFAA00, gold & 0xFFFFFF);
        assertEquals(0, WindowHighlights.fillOfKind(0));
        assertEquals(0, WindowHighlights.fillOfKind(7));
    }

    @Test
    void theFreeColoursKeepTheirFrameAndTheirThinFill() {
        // Die freien Farben (Auktionen) sind nicht Teil dieser Aenderung
        assertEquals(0x55, WindowHighlights.fillOf(0xFF55FF55) >>> 24);
        assertEquals(0xFF55FF55, WindowHighlights.edgeOf(0xFF55FF55));
    }

    // ---- Symbole (Order icons)

    @Test
    void aMarkedSlotKeepsItsIcon() {
        WindowHighlights.markIcon(12, WindowHighlights.ICON_PART);
        WindowHighlights.markIcon(13, WindowHighlights.ICON_FULL);
        WindowHighlights.markIcon(14, WindowHighlights.ICON_EXPIRING);
        WindowHighlights.markIcon(15, WindowHighlights.ICON_EXPIRED);
        assertEquals(WindowHighlights.ICON_PART, WindowHighlights.iconOf(12));
        assertEquals(WindowHighlights.ICON_FULL, WindowHighlights.iconOf(13));
        assertEquals(WindowHighlights.ICON_EXPIRING, WindowHighlights.iconOf(14));
        assertEquals(WindowHighlights.ICON_EXPIRED, WindowHighlights.iconOf(15));
        assertEquals(0, WindowHighlights.iconOf(16));
        assertEquals(4, WindowHighlights.iconCount());
    }

    @Test
    void anIconDoesNotCountAsAColourAndTheOtherWayRound() {
        WindowHighlights.markIcon(3, WindowHighlights.ICON_FULL);
        assertEquals(0, WindowHighlights.count(), "ein Symbol ist keine Farbe");
        assertEquals(0, WindowHighlights.kindOf(3));
        WindowHighlights.mark(4, WindowHighlights.GREEN);
        assertEquals(0, WindowHighlights.iconOf(4), "eine Farbe ist kein Symbol");
        // Beides auf einem Platz geht: das Feld ist gefuellt, das Zeichen steht oben links
        WindowHighlights.mark(3, WindowHighlights.GOLD);
        assertEquals(WindowHighlights.GOLD, WindowHighlights.kindOf(3));
        assertEquals(WindowHighlights.ICON_FULL, WindowHighlights.iconOf(3));
    }

    @Test
    void markingAnIconTwiceDoesNotCountTwice() {
        WindowHighlights.markIcon(5, WindowHighlights.ICON_PART);
        WindowHighlights.markIcon(5, WindowHighlights.ICON_EXPIRED);
        assertEquals(1, WindowHighlights.iconCount());
        assertEquals(WindowHighlights.ICON_EXPIRED, WindowHighlights.iconOf(5), "das letzte Zeichen gilt");
    }

    @Test
    void clearingRemovesTheIconsToo() {
        WindowHighlights.markIcon(1, WindowHighlights.ICON_FULL);
        WindowHighlights.mark(2, WindowHighlights.GREEN);
        WindowHighlights.clear();
        assertEquals(0, WindowHighlights.iconCount());
        assertEquals(0, WindowHighlights.iconOf(1));
        assertEquals(0, WindowHighlights.count());
        // Nur die Symbole loeschen geht auch, und die Farben bleiben
        WindowHighlights.markIcon(1, WindowHighlights.ICON_FULL);
        WindowHighlights.mark(2, WindowHighlights.GREEN);
        WindowHighlights.clearIcons();
        assertEquals(0, WindowHighlights.iconCount());
        assertEquals(WindowHighlights.GREEN, WindowHighlights.kindOf(2));
    }

    @Test
    void impossibleIconsAndSlotsAreIgnored() {
        WindowHighlights.markIcon(-1, WindowHighlights.ICON_FULL);
        WindowHighlights.markIcon(500, WindowHighlights.ICON_FULL);
        WindowHighlights.markIcon(3, 0);
        WindowHighlights.markIcon(3, 9);
        assertEquals(0, WindowHighlights.iconCount());
        assertEquals(0, WindowHighlights.iconOf(-1));
        assertEquals(0, WindowHighlights.iconOf(500));
    }

    @Test
    void impossibleSlotsAndKindsAreIgnored() {
        WindowHighlights.mark(-1, WindowHighlights.GREEN);
        WindowHighlights.mark(500, WindowHighlights.GREEN);
        WindowHighlights.mark(3, 0);
        WindowHighlights.mark(3, 7);
        assertEquals(0, WindowHighlights.count());
        assertEquals(0, WindowHighlights.kindOf(-1));
        assertEquals(0, WindowHighlights.kindOf(500));
    }
}
