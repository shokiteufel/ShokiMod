package com.shokiteufel.shokimod.handler;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Uhr eines Slayer-Kampfes: Beginn, Tod, Quest, und die Faelle, in denen sie nichts zaehlen darf. */
class SlayerKillClockTest {

    private static final long S = 1_000_000_000L;
    private static final String KEY = "Tarantula Broodfather V";

    private SlayerKillClock clock;

    @BeforeEach
    void aufbauen() {
        clock = new SlayerKillClock();
    }

    /** Eine Quest wie im Spiel: angenommen, die Seitenleiste zeigt den Fortschritt (die Zeile fehlt), dann erscheint der Boss */
    private void questUndBoss(long questAt, long bossAt) {
        clock.questStarted(questAt);
        clock.bossGone(questAt + S);
        clock.bossSeen(KEY, bossAt);
    }

    @Test
    void bossTimeRunsFromTheSidebarLineToTheCompleteMessageAndQuestTimeFromTheAcceptance() {
        questUndBoss(0L, 100 * S);
        clock.bossSeen(KEY, 105 * S);
        assertTrue(clock.running());
        SlayerKillClock.Kill kill = clock.finished(115 * S);
        assertNotNull(kill);
        assertEquals(KEY, kill.key());
        assertEquals(15 * S, kill.bossNanos());
        assertEquals(115 * S, kill.questNanos());
        assertFalse(clock.running());
    }

    @Test
    void theEarlierMomentTheLineWentMissingCountsAsTheDeath() {
        questUndBoss(0L, 100 * S);
        clock.bossGone(114 * S);
        SlayerKillClock.Kill kill = clock.finished(115_500_000_000L);
        assertEquals(14 * S, kill.bossNanos(), "die Meldung kommt 1,5 s nach dem Tod");
        assertEquals(114 * S, kill.questNanos());
    }

    @Test
    void aLineThatWentMissingLongBeforeTheMessageDoesNotCount() {
        questUndBoss(0L, 100 * S);
        clock.bossGone(103 * S);
        SlayerKillClock.Kill kill = clock.finished(110 * S);
        assertEquals(10 * S, kill.bossNanos(), "7 s Abstand: nicht der Tod, sondern eine andere Aenderung der Seitenleiste");
    }

    @Test
    void aShortGapInTheSidebarEndsNothing() {
        questUndBoss(0L, 100 * S);
        clock.bossGone(110 * S);
        clock.bossSeen(KEY, 111 * S);
        SlayerKillClock.Kill kill = clock.finished(115 * S);
        assertEquals(15 * S, kill.bossNanos(), "die Luecke zaehlt nicht, die Meldung entscheidet");
    }

    @Test
    void theChatMessageCanStartTheFightAndTheSidebarNamesItWithItsTier() {
        clock.questStarted(0L);
        clock.chatSpawn("Packmaster Soul", 100 * S);
        assertTrue(clock.running(), "der Chat beginnt auch ohne Seitenleiste");
        clock.bossSeen("Sven Packmaster V", 101 * S);
        SlayerKillClock.Kill kill = clock.finished(120 * S);
        assertEquals(20 * S, kill.bossNanos(), "der Beginn ist der des Chats");
        assertEquals("Sven Packmaster V", kill.key(), "die Seitenleiste nennt die Stufe mit");
    }

    @Test
    void theChatNameServesWhenTheSidebarNeverNamesTheBoss() {
        clock.chatSpawn("Packmaster Soul", 100 * S);
        assertEquals("Packmaster Soul", clock.finished(110 * S).key());
    }

    @Test
    void theSidebarStartStaysWhenTheChatMessageComesLater() {
        questUndBoss(0L, 100 * S);
        clock.chatSpawn("Tarantula Broodfather", 100_200_000_000L);
        SlayerKillClock.Kill kill = clock.finished(110 * S);
        assertEquals(10 * S, kill.bossNanos());
        assertEquals(KEY, kill.key(), "der Name der Seitenleiste (mit Stufe) bleibt");
    }

    @Test
    void withoutAQuestStartThereIsNoQuestTime() {
        clock.chatSpawn("Atoned Horror", 100 * S);
        SlayerKillClock.Kill kill = clock.finished(110 * S);
        assertEquals(10 * S, kill.bossNanos());
        assertEquals(-1L, kill.questNanos());
    }

    @Test
    void turningTheFeatureOnInTheMiddleOfAFightStartsNothing() {
        // Die Zeile steht schon beim ersten Lesen da: der Beginn waere falsch
        clock.bossSeen(KEY, 100 * S);
        assertFalse(clock.running());
        assertNull(clock.finished(110 * S), "kein bekannter Beginn: keine Zeit");
    }

    @Test
    void theLineThatStaysAfterTheDeathStartsNoPhantomFight() {
        questUndBoss(0L, 100 * S);
        assertNotNull(clock.finished(110 * S));
        // Die Seitenleiste zeigt die Zeile noch einen Augenblick
        clock.bossSeen(KEY, 111 * S);
        assertFalse(clock.running(), "erst muss die Zeile einmal gefehlt haben");
        clock.bossGone(113 * S);
        clock.bossSeen(KEY, 500 * S);
        assertTrue(clock.running(), "der naechste Boss beginnt neu");
        assertEquals(10 * S, clock.finished(510 * S).bossNanos());
    }

    @Test
    void aFailedQuestCountsNothing() {
        questUndBoss(0L, 100 * S);
        clock.failed();
        assertFalse(clock.running());
        assertNull(clock.finished(110 * S));
    }

    @Test
    void aLineThatStaysMissingWithoutAMessageIsGivenUp() {
        questUndBoss(0L, 100 * S);
        clock.bossGone(110 * S);
        clock.timeout(124 * S);
        assertTrue(clock.running(), "14 s: noch nicht");
        clock.timeout(126 * S);
        assertFalse(clock.running(), "ueber 15 s ohne Meldung: kein Tod, verworfen");
    }

    @Test
    void aNewQuestEndsAFightThatNeverGotItsMessage() {
        questUndBoss(0L, 100 * S);
        clock.questStarted(200 * S);
        assertFalse(clock.running());
    }

    @Test
    void aNewLobbyDropsTheFightButTheQuestKeepsRunning() {
        questUndBoss(0L, 100 * S);
        clock.lobbyChanged();
        assertFalse(clock.running());
        clock.bossGone(111 * S);
        clock.bossSeen(KEY, 120 * S);
        SlayerKillClock.Kill kill = clock.finished(130 * S);
        assertEquals(10 * S, kill.bossNanos());
        assertEquals(130 * S, kill.questNanos(), "die Quest wurde vor dem Wechsel angenommen");
    }

    @Test
    void theNameOfTheBossIsTheLineAboveTheSlayTheBossLine() {
        List<String> lines = List.of("SKYBLOCK", "Early Summer 16th", "Slayer Quest", "Tarantula Broodfather V", "Slay the boss!", "www.hypixel.net");
        assertEquals("Tarantula Broodfather V", SlayerKillClock.keyAbove(lines, 4));
        assertNull(SlayerKillClock.keyAbove(List.of("Slayer Quest", "Slay the boss!"), 1), "die Kopfzeile ist kein Name");
        assertNull(SlayerKillClock.keyAbove(List.of("Slay the boss!"), 0));
        assertNull(SlayerKillClock.keyAbove(List.of("", "Slay the boss!"), 1), "eine leere Zeile ist kein Name");
        assertNull(SlayerKillClock.keyAbove(null, 3));
    }
}
