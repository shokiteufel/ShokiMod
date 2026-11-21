package com.shokiteufel.shokimod.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rang, Bestzeit, Durchschnitt und Schreibweise der Slayer-Kampfzeiten. */
class SlayerKillStatsTest {

    @Test
    void theFirstTimeIsRankOneButNoRecord() {
        List<Long> times = new ArrayList<>();
        SlayerKillStats.Standing st = SlayerKillStats.record(times, 15_050L);
        assertEquals(new SlayerKillStats.Standing(1, 1, false, -1L), st);
        assertEquals(List.of(15_050L), times);
    }

    @Test
    void aSlowerTimeGetsItsPlaceAndTheListStaysSorted() {
        List<Long> times = new ArrayList<>(List.of(10_000L, 20_000L));
        SlayerKillStats.Standing st = SlayerKillStats.record(times, 15_000L);
        assertEquals(2, st.rank());
        assertEquals(3, st.total());
        assertFalse(st.best());
        assertEquals(10_000L, st.previous());
        assertEquals(List.of(10_000L, 15_000L, 20_000L), times);
    }

    @Test
    void aFasterTimeIsANewPersonalBest() {
        List<Long> times = new ArrayList<>(List.of(10_000L, 20_000L));
        SlayerKillStats.Standing st = SlayerKillStats.record(times, 9_000L);
        assertEquals(1, st.rank());
        assertTrue(st.best());
        assertEquals(10_000L, st.previous(), "die bisherige Bestzeit, zum Anzeigen");
        assertEquals(List.of(9_000L, 10_000L, 20_000L), times);
    }

    @Test
    void matchingTheBestTimeIsNotARecord() {
        List<Long> times = new ArrayList<>(List.of(10_000L, 20_000L));
        SlayerKillStats.Standing st = SlayerKillStats.record(times, 10_000L);
        assertEquals(1, st.rank(), "gleich schnell, gleicher Rang");
        assertFalse(st.best());
    }

    @Test
    void equalTimesShareTheirRank() {
        List<Long> times = new ArrayList<>(List.of(10_000L, 20_000L, 20_000L, 30_000L));
        assertEquals(2, SlayerKillStats.record(times, 20_000L).rank());
    }

    @Test
    void onlyTheFastestFiveHundredAreKept() {
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < SlayerKillStats.KEEP; i++) {
            times.add(10_000L + i);
        }
        SlayerKillStats.Standing slow = SlayerKillStats.record(times, 99_000L);
        assertEquals(SlayerKillStats.KEEP, times.size());
        assertEquals(SlayerKillStats.KEEP + 1, slow.rank(), "langsamer als alle gespeicherten");
        SlayerKillStats.record(times, 5_000L);
        assertEquals(SlayerKillStats.KEEP, times.size());
        assertEquals(5_000L, times.get(0));
        assertFalse(times.contains(10_000L + SlayerKillStats.KEEP - 1), "die langsamste fiel weg");
    }

    @Test
    void absurdTimesAreNotAFight() {
        assertFalse(SlayerKillStats.plausible(499L));
        assertTrue(SlayerKillStats.plausible(500L));
        assertTrue(SlayerKillStats.plausible(30L * 60_000L));
        assertFalse(SlayerKillStats.plausible(30L * 60_000L + 1));
    }

    @Test
    void theTimeIsWrittenLikeTheUserSketchedIt() {
        assertEquals("15,05Sec", SlayerKillStats.format(15_050L, Locale.GERMANY));
        assertEquals("15.05Sec", SlayerKillStats.format(15_050L, Locale.US));
        assertEquals("5,00Sec", SlayerKillStats.format(5_000L, Locale.GERMANY));
        assertEquals("0,07Sec", SlayerKillStats.format(70L, Locale.GERMANY));
        assertEquals("1m 05,20Sec", SlayerKillStats.format(65_200L, Locale.GERMANY));
        assertEquals("2m 05,00Sec", SlayerKillStats.format(125_000L, Locale.GERMANY));
        assertEquals("1m 00,00Sec", SlayerKillStats.format(59_996L, Locale.GERMANY), "gerundet wird vor dem Zerlegen: nicht '59,100'");
        assertEquals("0,00Sec", SlayerKillStats.format(-5L, Locale.GERMANY));
    }

    @Test
    void theSessionAverageIsTheMeanOfItsQuestTimes() {
        SlayerKillStats.Session session = new SlayerKillStats.Session();
        assertEquals(-1L, session.average(), "ohne Zeiten kein Durchschnitt");
        session.add(60_000L);
        session.add(90_000L);
        assertEquals(2, session.count());
        assertEquals(75_000L, session.average());
    }

    @Test
    void theBossLineCarriesTheRankAndTheRecordMarker() {
        SlayerKillStats.Standing best = new SlayerKillStats.Standing(1, 42, true, 16_400L);
        assertEquals("Boss Kill time: 15,05Sec  !!NEW PERSONAL BEST!!  (Rank #1 of 42)",
                SlayerKillStats.bossLine(15_050L, best, Locale.GERMANY));
        SlayerKillStats.Standing third = new SlayerKillStats.Standing(3, 41, false, 14_000L);
        assertEquals("Boss Kill time: 16,40Sec  (Rank #3 of 41)", SlayerKillStats.bossLine(16_400L, third, Locale.GERMANY));
    }

    @Test
    void theQuestLineCarriesTheSessionAverageBehindIt() {
        assertEquals("Quest Kill time: 1m 23,45Sec  (Avg. Time this Session: 1m 30,10Sec)",
                SlayerKillStats.questLine(83_450L, 90_100L, Locale.GERMANY));
        assertEquals("Quest Kill time: 1m 23,45Sec", SlayerKillStats.questLine(83_450L, -1L, Locale.GERMANY));
    }
}
