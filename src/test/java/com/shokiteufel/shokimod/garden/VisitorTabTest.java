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
    void aPlayerLineEndsTheListEvenIfTheHeaderSaysMore() {
        // Der Fall aus dem Spiel (2.6.14): Die Kopfzeile sagt sieben, darunter steht ein Besucher, dann kommen Fuellzeilen (leer, der Scanner nimmt ihre rohen
        // Profilnamen heraus) und ein Spieler - und das alles wurde als "neue Besucher" gelesen
        VisitorTab.Result r = VisitorTab.read(List.of("Visitors: (7)", " Sam", "", "", "", "", " [592] SchiggyMobil", " Farmhand"));
        assertTrue(r.found());
        assertEquals(7, r.count());
        assertEquals(List.of("Sam"), r.names());
        assertFalse(r.complete(), "ein Name fehlt: nichts darf als gegangen gelten");
        assertEquals("[592] SchiggyMobil", r.stop());
    }

    @Test
    void theHeaderOfTheNextWidgetEndsTheList() {
        VisitorTab.Result r = VisitorTab.read(List.of("Visitors: (3)", " Jacob", " Players (12)", " Anita"));
        assertEquals(List.of("Jacob"), r.names());
        assertEquals("Players (12)", r.stop());
    }

    @Test
    void aLevelInBracketsWithThousandsSeparatorIsAPlayer() {
        VisitorTab.Result r = VisitorTab.read(List.of("Visitors: (2)", " [1,024] Someone"));
        assertTrue(r.names().isEmpty());
        assertEquals("[1,024] Someone", r.stop());
    }

    @Test
    void aCompleteListHasNoStopLine() {
        assertEquals(null, VisitorTab.read(List.of("Visitors: (2)", " Jacob", " Anita", "Next Visitor: 3m")).stop());
        // Die Warteschlange war nicht voll: hier endet die Lesung an der Zeile mit Doppelpunkt
        assertEquals("Next Visitor: 3m", VisitorTab.read(List.of("Visitors: (3)", " Jacob", " Anita", "Next Visitor: 3m")).stop());
    }

    @Test
    void realVisitorNamesAreStillNames() {
        // Namen mit Punkt, Zahl in roemischer Schreibweise und Bindestrich duerfen nicht herausfallen
        for (String name : List.of("Madame Eleanor Q. Goldsworth III", "Emissary Carlton", "Lazy Miner", "Dr. Paws", "Jotraeline Greatforge", "Mr. Nikiforov")) {
            assertTrue(VisitorTab.looksLikeName(name), name);
        }
        assertFalse(VisitorTab.looksLikeName("Next Visitor: 3m"));
        assertFalse(VisitorTab.looksLikeName("[592] SchiggyMobil"));
        assertFalse(VisitorTab.looksLikeName("Players (12)"));
    }

    @Test
    void theFirstWidgetWins() {
        VisitorTab.Result r = VisitorTab.read(List.of("Visitors: (1)", " Jacob", "Visitors: (1)", " Anita"));
        assertEquals(List.of("Jacob"), r.names());
    }
}
