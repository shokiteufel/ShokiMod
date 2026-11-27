package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seit 2.5.17: Die Zeit der ganzen Quest laeuft wie bei SkyHanni von der Annahme ("SLAYER QUEST STARTED!") bis zur Zeile "SLAYER QUEST COMPLETE!", nicht bis zum Tod des Bosses;
 * und eine Quest, die laenger als zwei Minuten dauert, ist "nicht gewertet" und geht nicht in den Durchschnitt der Sitzung ein.
 *
 * <p>Anlass war ein Bild des Users: SkyHanni meldete "Slayer quest took 9s to complete", die ShokiMod "Quest Kill time: 8,12Sec" - dieselbe Quest, zwei Marken (Abschlussmeldung
 * gegen Tod, rund eine Sekunde auseinander).
 */
class SlayerQuestCompleteTest {

    private static final long S = 1_000_000_000L;
    private static final String KEY = "Revenant Horror V";

    private SlayerKillClock clock;

    @BeforeEach
    void aufbauen() {
        clock = new SlayerKillClock();
    }

    // ---- die Uhr ----

    @Test
    void theScreenshotCaseQuestToCompleteIsAboutOneSecondLongerThanQuestToDeath() {
        // Quest angenommen bei 0, Boss in der Seitenleiste bei 7,09 s, Tod ("Your Slayer Kill") bei 8,12 s, Abschlussmeldung bei 9,10 s
        clock.questStarted(0L);
        clock.bossGone(S);
        clock.bossSeen(KEY, 7_090_000_000L);
        assertNull(clock.killMark(8_120_000_000L), "die Zeile allein beendet noch nichts");
        SlayerKillClock.Kill kill = clock.finished(9_100_000_000L);
        assertNotNull(kill);
        assertEquals(1_030_000_000L, kill.bossNanos(), "die Bosszeit aendert sich nicht: Seitenleiste bis Tod");
        assertEquals(8_120_000_000L, kill.questNanos(), "bis zum Tod: nur noch fuers Log");
        SlayerKillClock.QuestDone done = clock.questCompleted(9_100_000_000L);
        assertNotNull(done);
        assertEquals(KEY, done.key());
        assertEquals(9_100_000_000L, done.nanos(), "bis zur Abschlussmeldung, wie bei SkyHanni");
    }

    @Test
    void withoutTheKillLineBossAndQuestEndAtTheSameMessage() {
        clock.questStarted(0L);
        clock.bossGone(S);
        clock.bossSeen(KEY, 100 * S);
        SlayerKillClock.Kill kill = clock.finished(115 * S);
        assertEquals(15 * S, kill.bossNanos());
        SlayerKillClock.QuestDone done = clock.questCompleted(115 * S);
        assertEquals(115 * S, done.nanos());
        assertEquals(kill.questNanos(), done.nanos(), "ohne eigene Todeszeile gibt es nur eine Marke");
    }

    @Test
    void aCocoonedBossMakesTheQuestTimeIncludeTheWait() {
        clock.questStarted(0L);
        clock.bossGone(S);
        clock.bossSeen(KEY, 100 * S);
        clock.deathMessage(120 * S, false); // YOU COCOONED YOUR SLAYER BOSS - der Boss lebt noch
        clock.killMark(126 * S); // Your Slayer Kill: der Tod
        SlayerKillClock.QuestDone done = clock.questCompleted(128 * S);
        assertEquals(128 * S, done.nanos(), "bis zur Abschlussmeldung: so lang wie sie braucht (hier 2 s nach dem Tod)");
    }

    @Test
    void theQuestIsTickedOffAfterTheCompleteMessage() {
        clock.questStarted(0L);
        clock.bossGone(S);
        clock.bossSeen(KEY, 10 * S);
        clock.finished(20 * S);
        assertNotNull(clock.questCompleted(20 * S));
        assertNull(clock.questCompleted(21 * S), "ein zweiter Aufruf zaehlt nichts");
    }

    @Test
    void withoutAStartThereIsNoQuestTime() {
        // Die Funktion wurde mitten in der Quest eingeschaltet: kein Beginn bekannt
        clock.bossGone(S);
        clock.bossSeen(KEY, 10 * S);
        clock.finished(20 * S);
        assertNull(clock.questCompleted(20 * S));
    }

    @Test
    void aFailedQuestHasNoQuestTime() {
        clock.questStarted(0L);
        clock.bossGone(S);
        clock.bossSeen(KEY, 10 * S);
        clock.failed();
        assertNull(clock.questCompleted(20 * S));
    }

    @Test
    void aNewQuestStartsItsOwnTime() {
        clock.questStarted(0L);
        clock.bossGone(S);
        clock.bossSeen(KEY, 10 * S);
        clock.finished(20 * S);
        // Die Abschlussmeldung kam nie (zum Beispiel abgebrochen); die naechste Quest beginnt neu
        clock.questStarted(100 * S);
        clock.bossGone(101 * S);
        clock.bossSeen(KEY, 110 * S);
        clock.finished(120 * S);
        SlayerKillClock.QuestDone done = clock.questCompleted(120 * S);
        assertEquals(20 * S, done.nanos(), "von 100 s bis 120 s, nicht von der alten Quest");
    }

    @Test
    void aLobbyChangeKeepsTheQuestRunning() {
        clock.questStarted(0L);
        clock.bossGone(S);
        clock.bossSeen(KEY, 10 * S);
        clock.lobbyChanged();
        SlayerKillClock.QuestDone done = clock.questCompleted(50 * S);
        assertNotNull(done, "die Quest wurde vor dem Wechsel angenommen");
        assertEquals(50 * S, done.nanos());
        assertEquals("Slayer boss", done.key(), "kein Kampf gezaehlt: ein allgemeiner Name");
    }

    @Test
    void theNameIsTheBossOfThatQuest() {
        clock.questStarted(0L);
        clock.bossGone(S);
        clock.bossSeen("Tarantula Broodfather V", 10 * S);
        clock.finished(20 * S);
        assertEquals("Tarantula Broodfather V", clock.questCompleted(21 * S).key());
        // Die naechste Quest kennt den Namen der vorigen nicht
        clock.questStarted(30 * S);
        assertEquals("Slayer boss", clock.questCompleted(40 * S).key());
    }

    @Test
    void aCompleteMessageBeforeTheStartCountsNothing() {
        clock.questStarted(50 * S);
        assertNull(clock.questCompleted(50 * S));
    }

    // ---- zwei Minuten ----

    @Test
    void aQuestOfAtMostTwoMinutesCountsForTheSession() {
        assertTrue(SlayerKillStats.countsForSession(0L));
        assertTrue(SlayerKillStats.countsForSession(9_100L));
        assertTrue(SlayerKillStats.countsForSession(120_000L), "genau zwei Minuten zaehlen noch");
        assertFalse(SlayerKillStats.countsForSession(120_001L), "laenger als zwei Minuten: nicht gewertet");
        assertFalse(SlayerKillStats.countsForSession(10 * 60_000L));
    }

    @Test
    void aLongQuestIsNotCountedAndLeavesTheAverageAlone() {
        SlayerKillStats.Session session = new SlayerKillStats.Session();
        assertTrue(session.addQuest(30_000L));
        assertTrue(session.addQuest(40_000L));
        assertFalse(session.addQuest(130_000L), "nicht gewertet");
        assertFalse(session.addQuest(125_000L), "nicht gewertet");
        assertEquals(2, session.count());
        assertEquals(35_000L, session.average(), "nur die gewerteten");
        assertTrue(session.addQuest(120_000L));
        assertEquals(3, session.count());
    }

    @Test
    void aSessionWithOnlyLongQuestsHasNoAverage() {
        SlayerKillStats.Session session = new SlayerKillStats.Session();
        assertFalse(session.addQuest(200_000L));
        assertEquals(-1L, session.average());
    }

    @Test
    void theLineSaysNotCountedAndKeepsTheAverage() {
        assertEquals("Quest Kill time: 2m 13,40Sec  (not counted - over 2 min)  (Avg. Time this Session: 25,47Sec)",
                SlayerKillStats.questLine(133_400L, 25_470L, false, Locale.GERMANY));
        assertEquals("Quest Kill time: 2m 13,40Sec  (not counted - over 2 min)", SlayerKillStats.questLine(133_400L, -1L, false, Locale.GERMANY),
                "ohne Durchschnitt keiner dahinter");
        assertEquals("Quest Kill time: 9,10Sec  (Avg. Time this Session: 25,47Sec)", SlayerKillStats.questLine(9_100L, 25_470L, true, Locale.GERMANY));
        assertEquals(SlayerKillStats.questLine(9_100L, 25_470L, true, Locale.GERMANY), SlayerKillStats.questLine(9_100L, 25_470L, Locale.GERMANY),
                "die alte Schreibweise bleibt");
    }
}
