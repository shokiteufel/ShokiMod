package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wunsch des Users (18.11.): Die Fischzeit des Sea Creature Trackers haelt nach einem Warp an wie beim Profit Tracker,
 * und "Pause after" stellt ein, nach wie vielen Sekunden ohne Fang (10 bis 60).
 */
class SeaCreatureTrackerZeitTest {

    /** Dreissig Sekunden, die Vorgabe */
    private static final long P = 30_000L;

    @BeforeEach
    void frisch() {
        SeaCreatureTracker.clockReset();
    }

    @Test
    void catchesCloseTogetherCountTheWholeGap() {
        SeaCreatureTracker.markCatch(1_000L, P);
        SeaCreatureTracker.markCatch(11_000L, P);
        SeaCreatureTracker.markCatch(26_000L, P);
        // Die Uhr beginnt mit dem ersten Fang; danach 10 s + 15 s
        assertEquals(25_000L, SeaCreatureTracker.sessionMillisAt(26_000L, P));
    }

    @Test
    void aGapLongerThanThePauseCountsNothing() {
        SeaCreatureTracker.markCatch(1_000L, P);
        SeaCreatureTracker.markCatch(11_000L, P);
        // 60 s Stille: die Uhr stand, die Wartezeit zaehlt nicht
        SeaCreatureTracker.markCatch(71_000L, P);
        assertEquals(10_000L, SeaCreatureTracker.sessionMillisAt(71_000L, P));
        // und ab dem naechsten Fang laeuft sie wieder
        SeaCreatureTracker.markCatch(76_000L, P);
        assertEquals(15_000L, SeaCreatureTracker.sessionMillisAt(76_000L, P));
    }

    @Test
    void aGapExactlyAtThePauseStillCounts() {
        SeaCreatureTracker.markCatch(1_000L, P);
        SeaCreatureTracker.markCatch(1_000L + P, P);
        assertEquals(P, SeaCreatureTracker.sessionMillisAt(1_000L + P, P));
    }

    @Test
    void theRunningPieceCountsUntilThePauseThenDrops() {
        SeaCreatureTracker.markCatch(1_000L, P);
        SeaCreatureTracker.markCatch(11_000L, P);
        assertEquals(10_000L + 20_000L, SeaCreatureTracker.sessionMillisAt(31_000L, P), "20 s nach dem Fang laeuft die Uhr");
        assertEquals(10_000L, SeaCreatureTracker.sessionMillisAt(11_000L + P + 1L, P), "nach der Pause ist die Wartezeit wieder ab");
    }

    @Test
    void aShorterPauseSettingPausesSooner() {
        long zehn = SeaCreatureTracker.pauseAfterMillis(10);
        SeaCreatureTracker.markCatch(1_000L, zehn);
        SeaCreatureTracker.markCatch(9_000L, zehn);
        // 20 s Stille sind bei zehn Sekunden schon eine Pause, bei dreissig noch nicht
        assertEquals(8_000L, SeaCreatureTracker.sessionMillisAt(29_000L, zehn));
        assertEquals(8_000L + 20_000L, SeaCreatureTracker.sessionMillisAt(29_000L, P));
    }

    @Test
    void changingAreaOrServerStopsTheClockAtOnce() {
        SeaCreatureTracker.markCatch(1_000L, P);
        SeaCreatureTracker.markCatch(11_000L, P);
        // 5 s nach dem Fang wird gewarpt: die 5 s Wartezeit zaehlen nicht
        SeaCreatureTracker.pauseNow();
        assertEquals(10_000L, SeaCreatureTracker.sessionMillisAt(16_000L, P));
        assertTrue(SeaCreatureTracker.pausedAt(16_000L, P));
        // Der naechste Fang rechnet nicht ueber die Luecke zurueck
        SeaCreatureTracker.markCatch(60_000L, P);
        assertEquals(10_000L, SeaCreatureTracker.sessionMillisAt(60_000L, P));
        SeaCreatureTracker.markCatch(65_000L, P);
        assertEquals(15_000L, SeaCreatureTracker.sessionMillisAt(65_000L, P));
        assertFalse(SeaCreatureTracker.pausedAt(65_000L, P));
    }

    @Test
    void pausedOnlyAfterTheClockHasStarted() {
        assertFalse(SeaCreatureTracker.pausedAt(5_000L, P), "vor dem ersten Fang gibt es nichts zu pausieren");
        SeaCreatureTracker.markCatch(5_000L, P);
        assertFalse(SeaCreatureTracker.pausedAt(5_000L + P, P), "genau an der Grenze laeuft sie noch");
        assertTrue(SeaCreatureTracker.pausedAt(5_000L + P + 1L, P));
        SeaCreatureTracker.clockReset();
        assertFalse(SeaCreatureTracker.pausedAt(500_000L, P), "nach dem Reset wieder wie am Anfang");
    }

    @Test
    void thePauseSettingStaysInsideTheSlider() throws NoSuchFieldException {
        ModConfig.INSTANCE = new ModConfig();
        int vorgabe = ModConfig.INSTANCE.fishing.tracker.pauseAfterSeconds;
        ConfigEditorSlider regler = ModConfig.SeaTrackerCategory.class.getField("pauseAfterSeconds")
                .getAnnotation(ConfigEditorSlider.class);
        assertEquals(10f, regler.minValue(), "der Regler beginnt bei 10 s");
        assertEquals(60f, regler.maxValue(), "der Regler endet bei 60 s");
        assertTrue(vorgabe >= regler.minValue() && vorgabe <= regler.maxValue(), "Vorgabe im Regler: " + vorgabe);
        // Von Hand geaenderte Werte ausserhalb werden begrenzt
        assertEquals(10_000L, SeaCreatureTracker.pauseAfterMillis(0));
        assertEquals(10_000L, SeaCreatureTracker.pauseAfterMillis(-5));
        assertEquals(60_000L, SeaCreatureTracker.pauseAfterMillis(600));
        assertEquals(45_000L, SeaCreatureTracker.pauseAfterMillis(45));
    }
}
