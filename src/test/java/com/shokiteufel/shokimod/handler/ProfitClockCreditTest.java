package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Die Gutschrift am Anfang einer Reihe der Profit-Uhr. Hintergrund: Schiggys Aufnahme vom 09.11. (13:15 bis 14:46) - Hypixel fasst die Saecke in
 * Schuben von dreissig Sekunden zusammen ("[Sacks] +254 items. (Last 30s.)"), die erste Meldung einer Reihe kam 18 bis 58 Sekunden nach dem
 * Beginn der Arbeit, und die Uhr stand bis dahin.
 */
class ProfitClockCreditTest {

    @Test
    void theFirstSackMessageOfASeriesCreditsItsWindow() {
        assertEquals(30_000L, ProfitTracker.startGutschrift(true, true, 30_000L, 0L));
        assertEquals(12_000L, ProfitTracker.startGutschrift(true, true, 12_000L, 0L), "Last 12s heisst zwoelf Sekunden");
    }

    @Test
    void inTheMiddleOfASeriesThereIsNothingToCredit() {
        assertEquals(0L, ProfitTracker.startGutschrift(false, true, 30_000L, 0L), "die Uhr laeuft ohnehin - sonst zaehlte die Zeit doppelt");
    }

    @Test
    void withoutARunningClockNothingIsCredited() {
        assertEquals(0L, ProfitTracker.startGutschrift(true, false, 30_000L, 0L));
    }

    @Test
    void aFindWithoutAWindowCreditsNothing() {
        assertEquals(0L, ProfitTracker.startGutschrift(true, true, 0L, 0L), "ein Fund aus dem Inventar sagt nichts ueber die Zeit davor");
        assertEquals(0L, ProfitTracker.startGutschrift(true, true, -5L, 0L));
    }

    @Test
    void theCreditNeverExceedsThirtySeconds() {
        assertEquals(30_000L, ProfitTracker.startGutschrift(true, true, 120_000L, 0L));
    }

    @Test
    void theCreditNeverReachesBackBeyondTheLastFind() {
        // Ein Gebietswechsel hat die Reihe beendet; der naechste Fund kommt 8 Sekunden nach dem letzten: nur diese 8 Sekunden sind neu
        assertEquals(8_000L, ProfitTracker.startGutschrift(true, true, 30_000L, 8_000L));
        assertEquals(30_000L, ProfitTracker.startGutschrift(true, true, 30_000L, 90_000L));
    }
}
