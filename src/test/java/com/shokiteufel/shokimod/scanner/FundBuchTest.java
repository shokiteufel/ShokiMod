package com.shokiteufel.shokimod.scanner;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drop-Zeilen und Funde zusammenfuehren (Chatzeile gegen Platz-Update), in beiden Reihenfolgen, und das Stash-Fenster. */
class FundBuchTest {
    private static final List<String> FLASH = List.of("ENCHANTMENT_FLASH_1", "ENCHANTMENT_ULTIMATE_FLASH_1");

    private FundBuch buch;

    @BeforeEach
    void aufbauen() {
        buch = new FundBuch(19, 78);
    }

    @Test
    void theLineFirstAndTheInventoryWithinTheWindowCountsOnceWithItsExactId() {
        assertEquals(0, buch.dropZeile(FLASH, 1, 1_000L));
        assertEquals(1, buch.wartend());
        assertEquals(1, buch.fund("ENCHANTMENT_ULTIMATE_FLASH_1", 1, 1_006L), "der Fund zaehlt, mit der Kennung aus dem Inventar");
        assertEquals(0, buch.wartend(), "die Zeile ist belegt");
        assertTrue(buch.takt(2_000L).isEmpty(), "nichts nachzutragen");
    }

    @Test
    void theInventoryFirstAndTheLineWithinTheLookBackNeedsNoRescue() {
        assertEquals(1, buch.fund("REVENANT_FLESH", 1, 1_000L));
        assertEquals(1, buch.dropZeile(List.of("REVENANT_FLESH"), 1, 1_008L), "der Fund war zuerst");
        assertEquals(0, buch.wartend());
        assertTrue(buch.takt(5_000L).isEmpty());
    }

    @Test
    void aLineWithoutAnyInventoryUpdateIsTakenFromTheChatAfterTheWindow() {
        buch.dropZeile(FLASH, 1, 1_000L);
        assertTrue(buch.takt(1_019L).isEmpty(), "19 Pakete sind noch im Fenster");
        List<FundBuch.Rettung> r = buch.takt(1_020L);
        assertEquals(1, r.size());
        assertEquals(FLASH, r.get(0).ids());
        assertEquals(1, r.get(0).menge());
        assertEquals(1_000L, r.get(0).zeilenNr());
        assertEquals(0, buch.wartend());
    }

    @Test
    void aLateInventoryUpdateAfterTheRescueDoesNotCountAgainAndWidensTheWindow() {
        buch.dropZeile(FLASH, 1, 1_000L);
        buch.takt(1_030L); // nachgetragen
        assertEquals(19, buch.fenster());
        assertEquals(0, buch.fund("ENCHANTMENT_FLASH_1", 1, 1_050L), "schon aus der Zeile gebucht");
        assertEquals(50, buch.fenster(), "das Platz-Update kam 50 Pakete nach der Zeile: das Fenster waechst darauf");

        // die naechste Zeile wartet entsprechend laenger
        buch.dropZeile(FLASH, 1, 2_000L);
        assertTrue(buch.takt(2_050L).isEmpty());
        assertEquals(1, buch.takt(2_051L).size());
    }

    /** Mitschnitt Schiggy 08.11., "Clipped Wings": Platz-Update 84 Pakete nach der Zeile, mit festem Fenster 19/78 zaehlte der Drop doppelt. */
    @Test
    void theMeasuredLongestDelayOf84PacketsIsPairedNotCountedTwice() {
        FundBuch echt = new FundBuch(com.shokiteufel.shokimod.paket.kern.Kern.DROP_FENSTER_STANDARD,
                com.shokiteufel.shokimod.paket.kern.Kern.KLICK_FENSTER_STANDARD);
        echt.dropZeile(List.of("CLIPPED_WINGS"), 1, 1_000L);
        assertTrue(echt.takt(1_084L).isEmpty(), "84 Pakete sind noch im Fenster");
        assertEquals(1, echt.fund("CLIPPED_WINGS", 1, 1_084L), "der Fund belegt die Zeile und zaehlt einmal");
        assertEquals(0, echt.wartend());

        // und kam das Update noch spaeter, nach dem Nachtrag: gebucht ist gebucht
        echt.dropZeile(List.of("CLIPPED_WINGS"), 1, 5_000L);
        assertEquals(1, echt.takt(5_085L).size(), "nachgetragen");
        assertEquals(0, echt.fund("CLIPPED_WINGS", 1, 5_150L), "65 Pakete nach dem Nachtrag: schon gebucht");
    }

    /** Das Fenster lernt, aber die Aufbewahrung der Nachtraege muss mitwachsen, sonst stoppt das Lernen bei {@code spaet}. */
    @Test
    void theKeepTimeOfARescueGrowsWithTheLearnedWindow() {
        buch.dropZeile(FLASH, 1, 1_000L);
        buch.takt(1_030L); // nachgetragen
        assertEquals(0, buch.fund("ENCHANTMENT_FLASH_1", 1, 1_070L), "70 Pakete: noch in der Aufbewahrung von 78");
        assertEquals(70, buch.fenster());

        // jetzt gilt doppelt so lange wie das Fenster: ein Update 130 Pakete nach der Zeile wird noch erkannt (mit festem 78 ging es verloren)
        buch.dropZeile(FLASH, 1, 2_000L);
        buch.takt(2_071L); // nachgetragen
        assertEquals(0, buch.fund("ENCHANTMENT_FLASH_1", 1, 2_130L), "130 Pakete nach der Zeile: schon gebucht, nicht doppelt");
        assertEquals(130, buch.fenster(), "das Fenster lernt weiter");
    }

    @Test
    void anUnrelatedFindDoesNotCloseALine() {
        buch.dropZeile(FLASH, 1, 1_000L);
        assertEquals(3, buch.fund("ROTTEN_FLESH", 3, 1_004L));
        assertEquals(1, buch.wartend(), "andere Ware belegt die Zeile nicht");
    }

    @Test
    void anOldUnmatchedFindIsForgottenAndCannotCoverALaterLine() {
        buch.fund("REVENANT_FLESH", 1, 1_000L);
        assertEquals(0, buch.dropZeile(List.of("REVENANT_FLESH"), 1, 1_500L), "der Fund liegt weit ausserhalb des Rueckblicks");
        assertEquals(1, buch.wartend());
    }

    @Test
    void theAmountIsDrawnDownNotJustTicked() {
        buch.dropZeile(List.of("ENCHANTED_DIAMOND"), 3, 1_000L);
        assertEquals(2, buch.fund("ENCHANTED_DIAMOND", 2, 1_003L));
        assertEquals(1, buch.wartend(), "ein Stueck der Zeile fehlt noch");
        List<FundBuch.Rettung> r = buch.takt(1_100L);
        assertEquals(1, r.get(0).menge(), "nachgetragen wird nur, was fehlt");
    }

    @Test
    void closingTheWorldRescuesWhatWasStillWaiting() {
        buch.dropZeile(FLASH, 1, 1_000L);
        List<FundBuch.Rettung> r = buch.alleSchliessen(1_002L);
        assertEquals(1, r.size());
        assertEquals(0, buch.wartend());
    }

    @Test
    void theSackMessageBelongsToTheStashOnlyIfTheStashLineLiesInItsOwnWindow() {
        buch.stashZeile(100_000_000_000L);
        assertTrue(buch.stashGehoert(103_000_000_000L, 5_000_000_000L), "die Meldung fasst die letzten 5 s zusammen, die Zeile liegt 3 s davor");
        assertFalse(buch.stashGehoert(110_000_000_000L, 5_000_000_000L), "10 s danach, ausserhalb des Zeitraums der Meldung");
        assertFalse(buch.stashGehoert(99_000_000_000L, 5_000_000_000L), "eine Meldung vor der Stash-Zeile kann den Stash nicht enthalten");
    }

    @Test
    void withoutAStashLineNoSackMessageBelongsToTheStash() {
        assertFalse(buch.stashGehoert(1L, 30_000_000_000L));
        buch.stashZeile(10L);
        buch.leeren();
        assertFalse(buch.stashGehoert(20L, 30_000_000_000L));
    }
}
