package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Das Protokoll der Serverkorrekturen: die Zeile, das "vor wie vielen Sekunden" und die Grenze je Minute. */
class RubberbandLogTest {

    private static final long S = 1_000_000_000L;

    @BeforeEach
    void reset() {
        RubberbandLog.resetForTests();
    }

    @Test
    void agoSaysDashForNeverAndForOldEvents() {
        assertEquals("-", RubberbandLog.ago(Long.MIN_VALUE, 100 * S));
        assertEquals("-", RubberbandLog.ago(100 * S - 31 * S, 100 * S));
        assertEquals("0.31 s ago", RubberbandLog.ago(100 * S - 310_000_000L, 100 * S));
    }

    @Test
    void sinceListsWhatHappenedAndHowOften() {
        long now = 1000 * S;
        RubberbandLog.markSwapForTests(now - 4 * S, 2);
        RubberbandLog.markSwapForTests(now - 400_000_000L, 2);
        RubberbandLog.markRestoreForTests(now - 100_000_000L, 2.4, "end_stone");
        String text = RubberbandLog.since(now);
        assertTrue(text.contains("hotbar swap 0.40 s ago (x2 in 5 s) to 3"), text);
        assertTrue(text.contains("block restored 0.10 s ago (x1 in 5 s) (2.40 blocks away, end_stone)"), text);
        assertTrue(text.contains("use item -"), text);
        assertTrue(text.contains("dig start -"), text);
    }

    @Test
    void anEventOlderThanTheBurstWindowIsNotCountedAgain() {
        long now = 1000 * S;
        RubberbandLog.markSwapForTests(now - 20 * S, 0);
        RubberbandLog.markSwapForTests(now - 400_000_000L, 0);
        assertTrue(RubberbandLog.since(now).contains("hotbar swap 0.40 s ago (x1 in 5 s)"));
    }

    @Test
    void theLineTellsDistanceSpeedAndWhatWasHeld() {
        String line = RubberbandLog.line(1.2, 0.0, -1.4, 3.5, 4.3, true, 2, "HYPERION", 1000 * S);
        assertTrue(line.startsWith("server moved us 1.84 blocks (dx 1.20, dy 0.00, dz -1.40, turn 3.50 deg)"), line);
        assertTrue(line.contains("we were doing 4.30 blocks/s, on the ground"), line);
        assertTrue(line.contains("holding slot 3 HYPERION"), line);
        assertTrue(line.contains("previous correction -"), line);
    }

    @Test
    void aLongWayIsCalledATeleportAndAnEmptyHandShowsADash() {
        String line = RubberbandLog.line(10.0, 0.0, 0.0, 0.0, 0.0, false, 0, null, 1000 * S);
        assertTrue(line.startsWith("server teleported us 10.00 blocks"), line);
        assertTrue(line.contains("in the air"), line);
        assertTrue(line.contains("holding slot 1 -"), line);
    }

    @Test
    void atMostThirtyLinesAMinuteAndTheNextMinuteReportsWhatWasLeftOut() {
        long t = 5 * S;
        for (int i = 0; i < RubberbandLog.MAX_LINES; i++) {
            assertEquals(0, RubberbandLog.take(t + i * (S / 100)), "line " + i);
        }
        assertEquals(-1, RubberbandLog.take(t + 20 * S));
        assertEquals(-1, RubberbandLog.take(t + 30 * S));
        // die naechste Minute: erste Zeile meldet die beiden weggelassenen, die danach nichts mehr
        assertEquals(2, RubberbandLog.take(t + 61 * S));
        assertEquals(0, RubberbandLog.take(t + 62 * S));
    }

    @Test
    void headingTellsForwardBackwardSidewaysStandingAndVertical() {
        assertEquals("forward (cos 1.00)", RubberbandLog.heading(0, -3, 0, -0.6));
        assertEquals("backward (cos -1.00)", RubberbandLog.heading(0, 3, 0, -0.6));
        assertEquals("sideways (cos 0.00)", RubberbandLog.heading(3, 0, 0, -0.6));
        assertEquals("we were standing", RubberbandLog.heading(0, -3, 0, 0));
        assertEquals("only up or down", RubberbandLog.heading(0, 0.01, 0, -0.6));
    }

    @Test
    void blockChangesOfTheLastTwoSecondsAreCountedNewestFirst() {
        long now = 1000 * S;
        assertEquals("none", RubberbandLog.changesText(now));
        RubberbandLog.markChangeForTests(now - 10 * S, "air", "stone", 1.0);
        RubberbandLog.markChangeForTests(now - 1_500_000_000L, "bedrock", "end_stone", 2.4);
        RubberbandLog.markChangeForTests(now - 400_000_000L, "end_stone", "bedrock", 3.1);
        assertEquals("2 (end_stone->bedrock 3.10 blocks, 0.40 s ago; bedrock->end_stone 2.40 blocks, 1.50 s ago)",
                RubberbandLog.changesText(now));
    }

    @Test
    void manyBlockChangesShowOnlyTheThreeNewestAndSaturateTheCount() {
        long now = 1000 * S;
        for (int i = 0; i < 20; i++) {
            RubberbandLog.markChangeForTests(now - 1_000_000_000L + i * 1_000_000L, "a" + i, "b" + i, 1.0);
        }
        String text = RubberbandLog.changesText(now);
        assertTrue(text.startsWith("16+ (a19->b19"), text);
        assertTrue(text.contains("a17->b17"), text);
        assertTrue(!text.contains("a16->b16"), text);
    }

    @Test
    void theExtraPartTellsPlaceDirectionSpeedAttributeTargetAndBlockChanges() {
        long now = 1000 * S;
        RubberbandLog.markChangeForTests(now - 400_000_000L, "end_stone", "bedrock", 3.1);
        String extra = RubberbandLog.extra(12.34, 64.0, -5.1, 0.0, 3.0, 0.0, -0.6, 0.284, "end_stone 3.10 blocks", now);
        assertTrue(extra.contains("at 12.3 64.0 -5.1"), extra);
        assertTrue(extra.contains("backward (cos -1.00)"), extra);
        assertTrue(extra.contains("speed attribute 0.284"), extra);
        assertTrue(extra.contains("looking at end_stone 3.10 blocks"), extra);
        assertTrue(extra.contains("last 2 s: 1 (end_stone->bedrock 3.10 blocks, 0.40 s ago)"), extra);
    }
}
