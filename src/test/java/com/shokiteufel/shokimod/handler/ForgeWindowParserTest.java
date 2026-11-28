package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.handler.ForgeWindowParser.Entry;
import com.shokiteufel.shokimod.handler.ForgeWindowParser.Kind;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das Fenster "The Forge" (28.11.2026, Wunsch: den Forge-Timer mit dem verbessern, was SkyOcean macht): Titel, Platzzuordnung und "Time Remaining:" wie in
 * SkyOcean/SkyblockAPI; "Time Remaining: 2h 20m 24s" steht auch im Mitschnitt des Users vom 09.11.
 */
class ForgeWindowParserTest {

    private static final long H = 3_600_000L;
    private static final long M = 60_000L;

    @Test
    void theTitleIsTheForgeWithoutColourCodesAndCase() {
        assertTrue(ForgeWindowParser.isForgeTitle("The Forge"));
        assertTrue(ForgeWindowParser.isForgeTitle("§aThe Forge"));
        assertTrue(ForgeWindowParser.isForgeTitle("  the forge "));
        assertFalse(ForgeWindowParser.isForgeTitle("Forge Recipes"));
        assertFalse(ForgeWindowParser.isForgeTitle("The Forge Menu"));
        assertFalse(ForgeWindowParser.isForgeTitle(""));
        assertFalse(ForgeWindowParser.isForgeTitle(null));
    }

    @Test
    void theSevenSlotsLieOnTenToSixteen() {
        assertEquals(1, ForgeWindowParser.numberOf(10));
        assertEquals(4, ForgeWindowParser.numberOf(13));
        assertEquals(7, ForgeWindowParser.numberOf(16));
        assertEquals(0, ForgeWindowParser.numberOf(9), "der Rand links");
        assertEquals(0, ForgeWindowParser.numberOf(17), "der Rand rechts");
        assertEquals(0, ForgeWindowParser.numberOf(0), "die obere Reihe");
        assertEquals(0, ForgeWindowParser.numberOf(22), "die Reihe darunter: Knoepfe, nicht die Forge");
    }

    @Test
    void aRunningSlotHasTheTimeToTheSecond() {
        Entry e = ForgeWindowParser.read(12, true, "Refined Diamond", List.of("Forging...", "", "Time Remaining: 2h 20m 24s", "", "Click to view"));
        assertNotNull(e);
        assertEquals(3, e.number());
        assertEquals(Kind.RUNNING, e.kind());
        assertEquals("Refined Diamond", e.item());
        assertEquals(2 * H + 20 * M + 24_000L, e.remainingMs(), "der Mitschnitt vom 09.11., wo der Tab nur '2h' zeigte");
    }

    @Test
    void daysAndSecondsAreRead() {
        assertEquals(26 * H, ForgeWindowParser.read(10, true, "X", List.of("Time Remaining: 1d 2h")).remainingMs());
        assertEquals(45_000L, ForgeWindowParser.read(10, true, "X", List.of("Time Remaining: 45s")).remainingMs());
    }

    @Test
    void theTimeLineIsFoundNoMatterWhereItStands() {
        Entry e = ForgeWindowParser.read(11, true, "X", List.of("a", "b", "c", "Time Remaining: 5m"));
        assertEquals(Kind.RUNNING, e.kind());
        assertEquals(5 * M, e.remainingMs());
    }

    @Test
    void aSlotWithAnItemAndAHintToCollectIsReady() {
        assertEquals(Kind.READY, ForgeWindowParser.read(10, true, "Refined Diamond", List.of("Forging complete", "", "Click to collect!")).kind());
        assertEquals(Kind.READY, ForgeWindowParser.read(10, true, "Refined Diamond", List.of("Ready to collect")).kind());
    }

    @Test
    void aTimeOfZeroIsReady() {
        assertEquals(Kind.READY, ForgeWindowParser.read(10, true, "X", List.of("Time Remaining: 0s")).kind());
    }

    @Test
    void anItemWeCannotReadIsLeftAloneAndKeepsItsLines() {
        // Wie ein fertiger Platz aussieht, ist nicht belegt: lieber nichts anfassen als raten
        Entry e = ForgeWindowParser.read(10, true, "Mystery", List.of("Something else", "", "More text"));
        assertEquals(Kind.UNKNOWN, e.kind());
        assertEquals("Something else | More text", e.raw());
    }

    @Test
    void aSlotWithoutAnItemIdIsNone() {
        // Rand, Knoepfe, leere und gesperrte Plaetze haben keine SkyBlock-Kennung
        Entry e = ForgeWindowParser.read(10, false, "Locked Slot", List.of("Time Remaining: 2h"));
        assertEquals(Kind.NONE, e.kind());
        assertEquals("", e.item());
        assertEquals(0L, e.remainingMs());
    }

    @Test
    void aPlaceOutsideTheForgeIsNotRead() {
        assertNull(ForgeWindowParser.read(22, true, "Button", List.of("Time Remaining: 2h")));
        assertNull(ForgeWindowParser.read(9, true, "Border", List.of()));
    }

    @Test
    void aWindowIsFilledOnceEnoughSlotsHaveSomethingInThem() {
        assertFalse(ForgeWindowParser.populated(0), "gerade geoeffnet: noch nichts geliefert");
        assertFalse(ForgeWindowParser.populated(9));
        assertTrue(ForgeWindowParser.populated(10), "Rand und Knoepfe sind da, auch bei leerer Forge");
    }

    @Test
    void theSameItemIsRecognisedAcrossTabAndWindow() {
        assertTrue(ForgeWindowParser.sameItem("Refined Diamond", "Refined Diamond"));
        assertTrue(ForgeWindowParser.sameItem("Refined Diamond", "§aRefined Diamond"), "Farbcodes");
        assertTrue(ForgeWindowParser.sameItem("Refined Diamond", "Refined Diamond x2"), "Anzahl dahinter");
        assertTrue(ForgeWindowParser.sameItem("Refined Diamond", "2x Refined Diamond"), "Anzahl davor");
        assertTrue(ForgeWindowParser.sameItem("Glacite Amalgamation", "glacite  amalgamation!"), "Schreibweise");
    }

    @Test
    void anotherItemIsNotTheSameEvenIfItContainsTheName() {
        assertFalse(ForgeWindowParser.sameItem("Refined Diamond", "Refined Diamond Block"));
        assertFalse(ForgeWindowParser.sameItem("Refined Diamond", "Refined Mithril"));
        assertFalse(ForgeWindowParser.sameItem("", ""), "ein leerer Name ist nie derselbe");
        assertFalse(ForgeWindowParser.sameItem(null, "X"));
    }
}
