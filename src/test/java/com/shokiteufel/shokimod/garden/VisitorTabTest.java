package com.shokiteufel.shokimod.garden;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Namen der Besucher im Widget "Visitors: (n)" der Tab-Liste.
 */
class VisitorTabTest {

    @Test
    void theNamesFollowTheHeader() {
        VisitorTab.Result r = VisitorTab.read(List.of("Area: Garden", "Visitors: (3)", " Emissary Carlton", " Lazy Miner", " Madame Eleanor Q. Goldsworth III",
                "Next Visitor: 3m 27s", "Composter:"));
        assertTrue(r.found());
        assertEquals(3, r.count());
        assertEquals(List.of("Emissary Carlton", "Lazy Miner", "Madame Eleanor Q. Goldsworth III"), r.names());
        assertTrue(r.complete());
    }

    @Test
    void aFullQueueEndsAtTheNextVisitorLine() {
        VisitorTab.Result r = VisitorTab.read(List.of("Visitors: (2)", " Jacob", " Anita", "Next Visitor: Queue Full!"));
        assertEquals(List.of("Jacob", "Anita"), r.names());
        assertTrue(r.complete());
    }

    @Test
    void aListInTheMakingIsNotComplete() {
        // Die Kopfzeile sagt drei, zwei Namen sind da, dann beginnt schon die naechste Zeile mit Doppelpunkt
        VisitorTab.Result r = VisitorTab.read(List.of("Visitors: (3)", " Jacob", " Anita", "Next Visitor: 3m"));
        assertTrue(r.found());
        assertEquals(List.of("Jacob", "Anita"), r.names());
        assertFalse(r.complete());
    }

    @Test
    void noVisitorsIsACompleteEmptyList() {
        VisitorTab.Result r = VisitorTab.read(List.of("Visitors: (0)", "Next Visitor: 1m 2s"));
        assertTrue(r.found());
        assertTrue(r.names().isEmpty());
        assertTrue(r.complete());
    }

    @Test
    void withoutTheWidgetNothingIsKnown() {
        VisitorTab.Result r = VisitorTab.read(List.of("Area: Garden", "Pests: (1)", "Plots: 4"));
        assertFalse(r.found());
        assertFalse(r.complete());
        assertFalse(VisitorTab.read(null).found());
        assertFalse(VisitorTab.read(List.of()).found());
    }

    @Test
    void blankAndOddLinesDoNotCountAsNames() {
        // Eine Leerzeile und ein geschuetztes Leerzeichen am Rand: gelesen wird wie bei den anderen Widgets ueber TabText
        VisitorTab.Result r = VisitorTab.read(List.of(" Visitors: (2) ", "", " Jacob", "   ", " Anita"));
        assertTrue(r.found());
        assertEquals(List.of("Jacob", "Anita"), r.names());
        assertTrue(r.complete());
    }

    @Test
    void theFirstWidgetWins() {
        VisitorTab.Result r = VisitorTab.read(List.of("Visitors: (1)", " Jacob", "Visitors: (1)", " Anita"));
        assertEquals(List.of("Jacob"), r.names());
    }
}
