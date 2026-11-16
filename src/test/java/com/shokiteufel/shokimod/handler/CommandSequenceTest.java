package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Befehle nacheinander, mit einer Sekunde Abstand - ohne Spiel gepruefte Schritte. */
class CommandSequenceTest {

    private final List<String> sent = new ArrayList<>();

    @BeforeEach
    void reset() {
        CommandSequence.resetForTests();
        sent.clear();
    }

    @Test
    void theFirstCommandGoesAtOnceAndTheNextOneASecondLater() {
        CommandSequence.enqueue(List.of("gfs OIL_BARREL 5", "call jotraeline"));
        CommandSequence.step(10_000, sent::add);
        assertEquals(List.of("gfs OIL_BARREL 5"), sent);
        CommandSequence.step(10_000 + CommandSequence.GAP_MILLIS - 1, sent::add);
        assertEquals(1, sent.size(), "kurz vor der Sekunde noch nicht");
        CommandSequence.step(10_000 + CommandSequence.GAP_MILLIS, sent::add);
        assertEquals(List.of("gfs OIL_BARREL 5", "call jotraeline"), sent);
        assertEquals(0, CommandSequence.pending());
    }

    @Test
    void aLeadingSlashAndBlanksAreDropped() {
        CommandSequence.enqueue(List.of("/gfs UMBER_KEY 2", "   ", "  /call jotraeline  "));
        assertEquals(2, CommandSequence.pending());
        CommandSequence.step(0, sent::add);
        CommandSequence.step(CommandSequence.GAP_MILLIS, sent::add);
        assertEquals(List.of("gfs UMBER_KEY 2", "call jotraeline"), sent);
    }

    @Test
    void theGapAlsoHoldsBetweenTwoSequencesInARow() {
        CommandSequence.enqueue(List.of("one"));
        CommandSequence.step(5_000, sent::add);
        CommandSequence.enqueue(List.of("two"));
        CommandSequence.step(5_500, sent::add);
        assertEquals(List.of("one"), sent);
        CommandSequence.step(6_000, sent::add);
        assertEquals(List.of("one", "two"), sent);
    }

    @Test
    void anEmptyQueueSendsNothing() {
        CommandSequence.step(1_000_000, sent::add);
        assertEquals(0, sent.size());
    }

    @Test
    void clearDropsWhatIsWaiting() {
        CommandSequence.enqueue(List.of("one", "two"));
        CommandSequence.step(0, sent::add);
        CommandSequence.clear();
        CommandSequence.step(CommandSequence.GAP_MILLIS * 5, sent::add);
        assertEquals(List.of("one"), sent);
    }
}
