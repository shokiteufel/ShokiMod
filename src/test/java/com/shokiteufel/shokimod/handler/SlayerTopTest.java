package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /shoki topslayer}: die drei schnellsten Boss-Zeiten je Slayer (und Stufe), in der Reihenfolge des Spiels. */
class SlayerTopTest {

    /** Eine veraenderbare Liste; anders als List.of erlaubt sie null (so kaputt kann eine Datei von Hand sein) */
    private static List<Long> times(Long... millis) {
        return new ArrayList<>(java.util.Arrays.asList(millis));
    }

    @Test
    void eachBossShowsItsThreeFastestTimes() {
        Map<String, List<Long>> records = new LinkedHashMap<>();
        records.put("Revenant Horror V", times(1_030L, 1_100L, 1_120L, 1_300L, 9_000L));
        List<SlayerKillStats.Top> top = SlayerKillStats.top(records, 3);
        assertEquals(1, top.size());
        assertEquals("Revenant Horror V", top.get(0).key());
        assertEquals(List.of(1_030L, 1_100L, 1_120L), top.get(0).best());
    }

    @Test
    void fewerThanThreeTimesShowAsManyAsThereAre() {
        Map<String, List<Long>> records = new LinkedHashMap<>();
        records.put("Sven Packmaster IV", times(20_000L));
        records.put("Voidgloom Seraph IV", times(30_000L, 31_000L));
        List<SlayerKillStats.Top> top = SlayerKillStats.top(records, 3);
        assertEquals(List.of(20_000L), top.get(0).best());
        assertEquals(List.of(30_000L, 31_000L), top.get(1).best());
    }

    @Test
    void theSlayersComeInTheOrderOfTheGameThenByTier() {
        Map<String, List<Long>> records = new HashMap<>();
        records.put("Voidgloom Seraph IV", times(5L));
        records.put("Revenant Horror V", times(5L));
        records.put("Tarantula Broodfather V", times(5L));
        records.put("Revenant Horror IV", times(5L));
        records.put("Sven Packmaster III", times(5L));
        records.put("Inferno Demonlord II", times(5L));
        records.put("Riftstalker Bloodfiend IV", times(5L));
        records.put("Revenant Horror III", times(5L));
        List<String> keys = SlayerKillStats.top(records, 3).stream().map(SlayerKillStats.Top::key).toList();
        assertEquals(List.of("Revenant Horror III", "Revenant Horror IV", "Revenant Horror V", "Tarantula Broodfather V", "Sven Packmaster III",
                "Voidgloom Seraph IV", "Inferno Demonlord II", "Riftstalker Bloodfiend IV"), keys);
    }

    @Test
    void anUnknownBossComesLastAndTheTiersOfOneBossAreNotMixed() {
        Map<String, List<Long>> records = new LinkedHashMap<>();
        records.put("Zorg The New One V", times(5L));
        records.put("Revenant Horror IV", times(9_000L));
        records.put("Revenant Horror V", times(1_000L));
        List<SlayerKillStats.Top> top = SlayerKillStats.top(records, 3);
        assertEquals("Revenant Horror IV", top.get(0).key());
        assertEquals(List.of(9_000L), top.get(0).best(), "Stufe 4 bleibt bei ihren eigenen Zeiten");
        assertEquals("Revenant Horror V", top.get(1).key());
        assertEquals("Zorg The New One V", top.get(2).key());
    }

    @Test
    void anUnsortedListIsStillSortedAndBadEntriesAreSkipped() {
        Map<String, List<Long>> records = new LinkedHashMap<>();
        records.put("Revenant Horror V", times(5_000L, 1_000L, null, 3_000L, 0L, -5L, 2_000L));
        List<SlayerKillStats.Top> top = SlayerKillStats.top(records, 3);
        assertEquals(List.of(1_000L, 2_000L, 3_000L), top.get(0).best());
    }

    @Test
    void emptyAndBrokenRecordsGiveNothing() {
        assertTrue(SlayerKillStats.top(null, 3).isEmpty());
        assertTrue(SlayerKillStats.top(new HashMap<>(), 3).isEmpty());
        Map<String, List<Long>> records = new LinkedHashMap<>();
        records.put("Revenant Horror V", new ArrayList<>());
        records.put("Sven Packmaster IV", null);
        records.put("", times(5L));
        records.put(null, times(5L));
        assertTrue(SlayerKillStats.top(records, 3).isEmpty(), "ein Boss ohne Zeiten steht nicht da");
        assertTrue(SlayerKillStats.top(Map.of("Revenant Horror V", times(5L)), 0).isEmpty());
    }

    @Test
    void theTierIsTheRomanNumberAtTheEnd() {
        assertEquals(5, SlayerKillStats.tierOf("Tarantula Broodfather V"));
        assertEquals(4, SlayerKillStats.tierOf("Revenant Horror IV"));
        assertEquals(3, SlayerKillStats.tierOf("Revenant Horror III"));
        assertEquals(1, SlayerKillStats.tierOf("Revenant Horror I"));
        assertEquals(0, SlayerKillStats.tierOf("Slayer boss"));
        assertEquals(0, SlayerKillStats.tierOf(""));
        assertEquals(0, SlayerKillStats.tierOf(null));
    }

    @Test
    void theLineListsTheRanksWithTheTimesOfTheGame() {
        SlayerKillStats.Top top = new SlayerKillStats.Top("Revenant Horror V", List.of(1_030L, 1_100L, 1_120L));
        assertEquals("Revenant Horror V: #1 1,03Sec  #2 1,10Sec  #3 1,12Sec", SlayerKillStats.topLine(top, Locale.GERMANY));
        assertEquals("Revenant Horror V: #1 1.03Sec  #2 1.10Sec  #3 1.12Sec", SlayerKillStats.topLine(top, Locale.US));
        SlayerKillStats.Top one = new SlayerKillStats.Top("Sven Packmaster IV", List.of(83_450L));
        assertEquals("Sven Packmaster IV: #1 1m 23,45Sec", SlayerKillStats.topLine(one, Locale.GERMANY));
    }

    @Test
    void theRecordsOfTheGameAreReadAsTheyAreStored() {
        // So legt record() die Zeiten ab: aufsteigend, die schnellste zuerst
        List<Long> stored = new ArrayList<>();
        SlayerKillStats.record(stored, 9_000L);
        SlayerKillStats.record(stored, 1_030L);
        SlayerKillStats.record(stored, 1_120L);
        SlayerKillStats.record(stored, 1_100L);
        SlayerKillStats.record(stored, 4_000L);
        Map<String, List<Long>> records = new HashMap<>();
        records.put("Revenant Horror V", stored);
        assertEquals(List.of(1_030L, 1_100L, 1_120L), SlayerKillStats.top(records, SlayerKillStats.TOP_COUNT).get(0).best());
    }
}
