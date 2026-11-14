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
}
