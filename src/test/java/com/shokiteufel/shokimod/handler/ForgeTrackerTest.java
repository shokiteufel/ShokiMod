package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.ForgeTracker.Meldeart;
import com.shokiteufel.shokimod.handler.ForgeTracker.Meldung;
import com.shokiteufel.shokimod.handler.ForgeTracker.Row;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Der Forge-Merker mit eigener Uhr: Kasten "nur kurz vor Ende" und die Meldungen "gleich fertig" / "fertig". */
class ForgeTrackerTest {

    private static final long MIN = 60_000L;
    private static final long T0 = 1_000_000_000_000L;
    /** Die erste Schaetzung eines Stuecks ist die Mitte seiner Spanne: eine halbe Minute unter der angezeigten Zeit */
    private static final long HALBE = 30_000L;

    private int gespeichert;

    @BeforeEach
    void frischeConfig() {
        ModConfig.INSTANCE = new ModConfig();
        gespeichert = 0;
        ForgeTracker.speichern = () -> gespeichert++;
        ForgeTracker.forget();
        gespeichert = 0;
    }

    private static ModConfig.ForgeCategory cfg() {
        return ModConfig.INSTANCE.mining.forge;
    }

    private static List<String> widget(String... zeilen) {
        List<String> out = new java.util.ArrayList<>();
        out.add("Forges:");
        out.addAll(List.of(zeilen));
        return out;
    }

    private static void lesen(long now, String... zeilen) {
        ForgeTracker.processSlots(ForgeTab.parse(widget(zeilen)), now);
    }

    @Test
    void theRecordedSequenceEndsWithSevenRunningSlots() {
        lesen(T0, "1) Glacite Amalgamation: Ready!", "2) Glacite Amalgamation: Ready!");
        assertEquals(2, ForgeTracker.rows(T0).size());
        assertTrue(ForgeTracker.rows(T0).stream().allMatch(Row::ready));

        lesen(T0 + 6_000L, "1) EMPTY", "2) EMPTY");
        assertTrue(ForgeTracker.rows(T0 + 6_000L).stream().allMatch(Row::empty));

        lesen(T0 + 12_000L, "1) Glacite Amalgamation: 2h", "2) EMPTY");
        List<Row> rows = ForgeTracker.rows(T0 + 12_000L);
        assertEquals(150 * MIN, rows.get(0).remainingMs(), "ein erstes Mal gesehen: die Mitte der Spanne (2h heisst: mindestens 2h, weniger als 3h)");
        assertTrue(rows.get(1).empty());
    }

    @Test
    void thePanelIsAlwaysThereWithoutTheShortlyOption() {
        lesen(T0, "1) Refined Diamond: 2h");
        assertEquals(1, ForgeTracker.panelRows(T0).size());
        assertTrue(ForgeTracker.hasRows(T0));
    }

    @Test
    void onlyShortlyHidesSlotsWithMoreTimeAndEmptyOnes() {
        cfg().onlyShortly = true;
        cfg().shortlyMinutes = 3;
        lesen(T0, "1) Refined Diamond: 2h", "2) EMPTY");
        assertTrue(ForgeTracker.panelRows(T0).isEmpty());
        assertFalse(ForgeTracker.hasRows(T0));

        long finish = T0 + 150 * MIN;
        assertTrue(ForgeTracker.panelRows(finish - 4 * MIN).isEmpty());
        List<Row> soon = ForgeTracker.panelRows(finish - 2 * MIN);
        assertEquals(1, soon.size());
        assertEquals(1, soon.get(0).slot());
    }

    @Test
    void aFinishedSlotStaysForTenMinutesInTheShortlyPanel() {
        cfg().onlyShortly = true;
        lesen(T0, "1) Refined Diamond: 2m");
        long finish = T0 + 2 * MIN - HALBE;
        assertEquals(1, ForgeTracker.panelRows(finish + 9 * MIN).size());
        assertTrue(ForgeTracker.panelRows(finish + 11 * MIN).isEmpty());
    }

    @Test
    void theShortlyWindowIsClampedToOneToFiveMinutes() {
        cfg().onlyShortly = true;
        cfg().shortlyMinutes = 99;
        lesen(T0, "1) Refined Diamond: 2h");
        long finish = T0 + 150 * MIN;
        assertTrue(ForgeTracker.panelRows(finish - 6 * MIN).isEmpty());
        assertEquals(1, ForgeTracker.panelRows(finish - 5 * MIN + 1000L).size());
    }

    // ------------------------------------------------------------ Meldungen

    @Test
    void warnsOnceBeforeAndOnceWhenReady() {
        cfg().alertBeforeMinutes = 2;
        lesen(T0, "1) Refined Diamond: 1h");
        long finish = T0 + 60 * MIN - HALBE;

        assertTrue(ForgeTracker.faellige(finish - 3 * MIN).isEmpty());

        List<Meldung> soon = ForgeTracker.faellige(finish - 2 * MIN);
        assertEquals(1, soon.size());
        assertEquals(Meldeart.GLEICH, soon.get(0).art());
        assertEquals("Refined Diamond", soon.get(0).item());
        assertTrue(ForgeTracker.faellige(finish - MIN).isEmpty(), "die Vorwarnung kommt nur einmal");

        List<Meldung> done = ForgeTracker.faellige(finish + 1000L);
        assertEquals(1, done.size());
        assertEquals(Meldeart.FERTIG, done.get(0).art());
        assertTrue(ForgeTracker.faellige(finish + 2000L).isEmpty(), "und 'fertig' auch");
    }

    @Test
    void zeroMinutesMeansNoEarlyWarning() {
        cfg().alertBeforeMinutes = 0;
        lesen(T0, "1) Refined Diamond: 1h");
        long finish = T0 + 60 * MIN - HALBE;
        assertTrue(ForgeTracker.faellige(finish - MIN).isEmpty());
        assertEquals(Meldeart.FERTIG, ForgeTracker.faellige(finish).get(0).art());
    }

    @Test
    void readyAlertCanBeSwitchedOff() {
        cfg().alertBeforeMinutes = 0;
        cfg().alertWhenReady = false;
        lesen(T0, "1) Refined Diamond: 1h");
        assertTrue(ForgeTracker.faellige(T0 + 61 * MIN).isEmpty());
    }

    @Test
    void aSlotSeenReadyInTheTabListIsNotAnnounced() {
        // Wer in den Minen steht, sieht es dort; ein Stueck, das wir nicht haben laufen sehen, meldet nichts
        lesen(T0, "1) Glacite Amalgamation: Ready!");
        assertTrue(ForgeTracker.faellige(T0 + 1000L).isEmpty());
    }

    @Test
    void aSlotThatFinishedLongAgoIsNotAnnouncedAfterARestart() {
        cfg().alertBeforeMinutes = 0;
        lesen(T0, "1) Refined Diamond: 5m");
        long finish = T0 + 5 * MIN - HALBE;
        // Das Spiel war zwei Stunden zu: nichts von gestern melden, und auch spaeter nicht mehr
        assertTrue(ForgeTracker.faellige(finish + 120 * MIN).isEmpty());
        assertTrue(ForgeTracker.faellige(finish + 121 * MIN).isEmpty());
    }

    @Test
    void aPieceThatTheTabListShowsReadyEarlyStillGetsItsAnnouncement() {
        cfg().alertBeforeMinutes = 0;
        lesen(T0, "1) Refined Diamond: 10m");
        // Nach 9 Minuten steht im Tab schon "Ready!": die Mod hat es laufen sehen, also meldet sie das Ende
        lesen(T0 + 9 * MIN, "1) Refined Diamond: Ready!");
        List<Meldung> done = ForgeTracker.faellige(T0 + 9 * MIN + 1000L);
        assertEquals(1, done.size());
        assertEquals(Meldeart.FERTIG, done.get(0).art());
    }

    @Test
    void aNewPieceInTheSameSlotAnnouncesAgain() {
        cfg().alertBeforeMinutes = 0;
        lesen(T0, "1) Refined Diamond: 10m");
        assertEquals(1, ForgeTracker.faellige(T0 + 10 * MIN).size());

        lesen(T0 + 12 * MIN, "1) EMPTY");
        lesen(T0 + 13 * MIN, "1) Refined Diamond: 10m");
        assertEquals(1, ForgeTracker.faellige(T0 + 23 * MIN).size());
    }

    @Test
    void sameItemStartedAgainWithoutSeeingEmptyCountsAsNewPieceByItsLaterEnd() {
        cfg().alertBeforeMinutes = 0;
        lesen(T0, "1) Refined Diamond: 10m");
        assertEquals(1, ForgeTracker.faellige(T0 + 10 * MIN).size());
        // Dasselbe Stueck noch einmal eingelegt, ohne dass "EMPTY" zu sehen war: das neue Ende liegt weit hinter dem alten
        lesen(T0 + 11 * MIN, "1) Refined Diamond: 30m");
        assertEquals(1, ForgeTracker.faellige(T0 + 41 * MIN).size());
    }

    @Test
    void severalSlotsAtOnceGiveOneBannerText() {
        List<Meldung> sieben = new java.util.ArrayList<>();
        for (int i = 1; i <= 7; i++) sieben.add(new Meldung(i, "Glacite Amalgamation", Meldeart.FERTIG, 0L));
        assertEquals("Forge: 7 slots are ready!", ForgeAlert.text(sieben));
        assertEquals("Forge: Refined Diamond is ready!",
                ForgeAlert.text(List.of(new Meldung(2, "Refined Diamond", Meldeart.FERTIG, 0L))));
        assertEquals("Forge: Refined Diamond ready in 2m",
                ForgeAlert.text(List.of(new Meldung(2, "Refined Diamond", Meldeart.GLEICH, 2 * MIN))));
        assertEquals("Forge: 2 slots ready in 1m", ForgeAlert.text(List.of(
                new Meldung(1, "A", Meldeart.GLEICH, 61_000L), new Meldung(2, "B", Meldeart.GLEICH, 59_000L))));
        assertEquals("Forge: 1 ready, 1 almost", ForgeAlert.text(List.of(
                new Meldung(1, "A", Meldeart.FERTIG, 0L), new Meldung(2, "B", Meldeart.GLEICH, 60_000L))));
        assertEquals("", ForgeAlert.text(List.of()));
    }

    @Test
    void flagsAreSavedSoAMessageSurvivesARestart() {
        cfg().alertBeforeMinutes = 0;
        lesen(T0, "1) Refined Diamond: 10m");
        int vorher = gespeichert;
        ForgeTracker.faellige(T0 + 10 * MIN);
        assertTrue(gespeichert > vorher, "'fertig' wird vermerkt und gespeichert");
        assertTrue(cfg().slotData.get(0).announced);
    }
}
