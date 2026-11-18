package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Wunsch des Users (18.11.): der Aura-Zeitgeber aus Riccio Fishing Utils, unter Fishing > Frozen Blaze.
 *
 * Die Regel (von dort): Die Aura braucht Bewegung des Blicks UND des Koerpers, je innerhalb von dreissig Sekunden; die Restzeit ist die
 * kuerzere der beiden. Beim Anlegen des Sets beginnen beide Uhren von vorn.
 */
class FrozenBlazeAuraTest {

    private static final long T0 = 1_000_000L;
    private static final long S = 1_000L;

    @BeforeEach
    void frisch() {
        FrozenBlaze.auraReset();
    }

    /** Das Set anlegen und einmal stehen: beide Uhren stehen auf T0 */
    private static void putOn() {
        FrozenBlaze.observe(true, false, 10f, 20f, 1.0, 64.0, 2.0, T0);
    }

    private static long left(long now) {
        return FrozenBlaze.auraLeftMillis(now, FrozenBlaze.lookedAtForTests(), FrozenBlaze.movedAtForTests());
    }

    @Test
    void standingStillCountsDownFromThirtySeconds() {
        putOn();
        assertEquals(30 * S, left(T0));
        assertEquals(20 * S, left(T0 + 10 * S));
        assertEquals(0L, left(T0 + 30 * S));
        assertEquals(0L, left(T0 + 90 * S), "nie unter null");
    }

    @Test
    void movingBodyAndViewTogetherStartsOver() {
        putOn();
        FrozenBlaze.observe(true, true, 11f, 20f, 2.0, 64.0, 2.0, T0 + 20 * S);
        assertEquals(30 * S, left(T0 + 20 * S));
        assertEquals(25 * S, left(T0 + 25 * S));
    }

    @Test
    void movingOnlyTheViewIsNotEnough() {
        putOn();
        // Nach zwanzig Sekunden nur die Maus: die Koerper-Uhr laeuft weiter
        FrozenBlaze.observe(true, true, 11f, 20f, 1.0, 64.0, 2.0, T0 + 20 * S);
        assertEquals(10 * S, left(T0 + 20 * S));
        // Wer die ganze Zeit nur schaut, verliert die Aura nach dreissig Sekunden trotzdem
        for (long t = 21; t <= 40; t++) FrozenBlaze.observe(true, true, 11f + t, 20f, 1.0, 64.0, 2.0, T0 + t * S);
        assertEquals(0L, left(T0 + 40 * S));
    }

    @Test
    void movingOnlyTheBodyIsNotEnough() {
        putOn();
        FrozenBlaze.observe(true, true, 10f, 20f, 5.0, 64.0, 2.0, T0 + 20 * S);
        assertEquals(10 * S, left(T0 + 20 * S));
        for (long t = 21; t <= 40; t++) FrozenBlaze.observe(true, true, 10f, 20f, 5.0 + t, 64.0, 2.0, T0 + t * S);
        assertEquals(0L, left(T0 + 40 * S));
    }

    @Test
    void jumpingChangesTheBodyAndThePitchChangesTheView() {
        putOn();
        // Hoehe zaehlt als Bewegung des Koerpers, die Neigung als Bewegung des Blicks
        FrozenBlaze.observe(true, true, 10f, 25f, 1.0, 65.0, 2.0, T0 + 12 * S);
        assertEquals(30 * S, left(T0 + 12 * S));
    }

    @Test
    void thePositionOfTheFirstTickIsOnlyRemembered() {
        // Ohne vorige Stellung gibt es nichts zu vergleichen: der erste Tick zaehlt nicht als Bewegung
        FrozenBlaze.observe(false, false, 10f, 20f, 1.0, 64.0, 2.0, T0);
        assertEquals(0L, FrozenBlaze.lookedAtForTests());
        assertEquals(0L, FrozenBlaze.movedAtForTests());
    }

    @Test
    void takingTheSetOffAndOnAgainRefreshesBothClocks() {
        putOn();
        // Zwanzig Sekunden stehen, Set aus (die Uhren laufen unbeachtet weiter), nach 40 Sekunden wieder an
        FrozenBlaze.observe(false, true, 10f, 20f, 1.0, 64.0, 2.0, T0 + 20 * S);
        FrozenBlaze.observe(true, false, 10f, 20f, 1.0, 64.0, 2.0, T0 + 40 * S);
        assertEquals(30 * S, left(T0 + 40 * S));
    }

    @Test
    void secondsAreRoundedUpAndZeroMeansOff() {
        assertEquals(30, FrozenBlaze.auraSeconds(30 * S));
        assertEquals(30, FrozenBlaze.auraSeconds(29_001L));
        assertEquals(29, FrozenBlaze.auraSeconds(29_000L));
        assertEquals(1, FrozenBlaze.auraSeconds(1L));
        assertEquals(0, FrozenBlaze.auraSeconds(0L));
        assertEquals(0, FrozenBlaze.auraSeconds(-5L));
    }

    @Test
    void theClockNeverShowsMoreThanThirtySeconds() {
        // Eine Uhr, die einmal rueckwaerts springt, darf keine Restzeit ueber dreissig Sekunden ergeben
        assertEquals(30 * S, FrozenBlaze.auraLeftMillis(T0 - 5 * S, T0, T0));
    }
}
