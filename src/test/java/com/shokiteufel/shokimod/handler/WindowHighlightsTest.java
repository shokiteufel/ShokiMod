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
