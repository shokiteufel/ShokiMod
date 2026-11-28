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
    void theSlayersComeInTheOrderOfTheGame() {
        Map<String, List<Long>> records = new HashMap<>();
        records.put("Voidgloom Seraph IV", times(5L));
        records.put("Revenant Horror V", times(5L));
        records.put("Tarantula Broodfather V", times(5L));
        records.put("Sven Packmaster III", times(5L));
        records.put("Inferno Demonlord II", times(5L));
        records.put("Riftstalker Bloodfiend IV", times(5L));
        List<String> keys = SlayerKillStats.top(records, 3).stream().map(SlayerKillStats.Top::key).toList();
        assertEquals(List.of("Revenant Horror V", "Tarantula Broodfather V", "Sven Packmaster III",
                "Voidgloom Seraph IV", "Inferno Demonlord II", "Riftstalker Bloodfiend IV"), keys);
    }

    private static List<String> keys(Map<String, List<Long>> records) {
        return SlayerKillStats.top(records, 3).stream().map(SlayerKillStats.Top::key).toList();
    }

    // ---- seit 2.6.9: je Slayer nur die hoechste Stufe (Meldung des Users 28.11.)

    @Test
    void onlyTheHighestTierOfASlayerIsShownOnceItIsDone() {
        // Der Fall aus dem Bild: Sven Packmaster IV und V standen beide da; mit V soll nur V stehen
        Map<String, List<Long>> records = new LinkedHashMap<>();
        records.put("Revenant Horror V", times(600L, 610L, 720L));
        records.put("Sven Packmaster IV", times(910L, 1_199L, 1_200L));
        records.put("Sven Packmaster V", times(11_160L, 11_660L, 11_905L));
        List<SlayerKillStats.Top> top = SlayerKillStats.top(records, 3);
        assertEquals(List.of("Revenant Horror V", "Sven Packmaster V"), top.stream().map(SlayerKillStats.Top::key).toList());
        assertEquals(List.of(11_160L, 11_660L, 11_905L), top.get(1).best(), "die Zeiten der Stufe V, nicht vermischt");
    }

    @Test
    void aSlayerWithoutTheHighestTierYetShowsItsHighestOne() {
        Map<String, List<Long>> records = new LinkedHashMap<>();
        records.put("Sven Packmaster III", times(5_000L));
        records.put("Sven Packmaster IV", times(9_000L));
        assertEquals(List.of("Sven Packmaster IV"), keys(records), "noch kein V: dann steht IV da, nicht nichts");
    }

    @Test
    void everySlayerKeepsItsOwnHighestTier() {
        Map<String, List<Long>> records = new HashMap<>();
        records.put("Revenant Horror III", times(5L));
        records.put("Revenant Horror V", times(5L));
        records.put("Tarantula Broodfather IV", times(5L));
        records.put("Tarantula Broodfather II", times(5L));
        records.put("Voidgloom Seraph I", times(5L));
        assertEquals(List.of("Revenant Horror V", "Tarantula Broodfather IV", "Voidgloom Seraph I"), keys(records));
    }

    @Test
    void theVampireSlayerShowsTierFourAndFive() {
        Map<String, List<Long>> records = new LinkedHashMap<>();
        records.put("Riftstalker Bloodfiend V", times(30_000L));
        records.put("Riftstalker Bloodfiend IV", times(20_000L));
        records.put("Riftstalker Bloodfiend III", times(10_000L));
        records.put("Sven Packmaster IV", times(5L));
        records.put("Sven Packmaster V", times(5L));
        assertEquals(List.of("Sven Packmaster V", "Riftstalker Bloodfiend IV", "Riftstalker Bloodfiend V"), keys(records),
                "der Vampire mit IV und V (aufsteigend), Stufe III nicht; Sven nur V");
    }

    @Test
    void theVampireSlayerWithOnlyTierFourShowsFour() {
        Map<String, List<Long>> records = new LinkedHashMap<>();
        records.put("Riftstalker Bloodfiend IV", times(20_000L));
        records.put("Riftstalker Bloodfiend II", times(10_000L));
        assertEquals(List.of("Riftstalker Bloodfiend IV"), keys(records));
    }

    @Test
    void theVampireSlayerWithOnlyLowTiersShowsItsHighestSoNothingDisappears() {
        Map<String, List<Long>> records = new LinkedHashMap<>();
        records.put("Riftstalker Bloodfiend III", times(10_000L));
        records.put("Riftstalker Bloodfiend I", times(8_000L));
        assertEquals(List.of("Riftstalker Bloodfiend III"), keys(records));
    }

    @Test
    void theVampireSlayerWithOnlyTierFiveShowsFive() {
        Map<String, List<Long>> records = new LinkedHashMap<>();
        records.put("Riftstalker Bloodfiend V", times(30_000L));
        assertEquals(List.of("Riftstalker Bloodfiend V"), keys(records));
    }

    @Test
    void aKeyWithoutATierIsItsOwnSlayerAndStays() {
        // "Atoned Horror" steht ohne Stufe in den Daten; die Mod kann nicht wissen, zu welchem Slayer er gehoert - er bleibt, wie er ist
        Map<String, List<Long>> records = new LinkedHashMap<>();
        records.put("Revenant Horror V", times(600L));
        records.put("Atoned Horror", times(500L));
        assertEquals(List.of("Revenant Horror V", "Atoned Horror"), keys(records));
    }

    @Test
    void theSlayerIsTheNameWithoutTheTier() {
        assertEquals("Sven Packmaster", SlayerKillStats.slayerOf("Sven Packmaster V"));
        assertEquals("Riftstalker Bloodfiend", SlayerKillStats.slayerOf("Riftstalker Bloodfiend IV"));
        assertEquals("Atoned Horror", SlayerKillStats.slayerOf("Atoned Horror"));
        assertEquals("V", SlayerKillStats.slayerOf("V"));
        assertEquals("", SlayerKillStats.slayerOf(null));
        assertEquals("Revenant Horror", SlayerKillStats.slayerOf("  Revenant Horror III  "));
    }

    @Test
    void theLowerTiersStayStoredAndComeBackWhenTheHigherOnesAreGone() {
        Map<String, List<Long>> records = new LinkedHashMap<>();
        records.put("Sven Packmaster IV", times(900L));
        records.put("Sven Packmaster V", times(11_000L));
        assertEquals(List.of("Sven Packmaster V"), keys(records));
        // Die Zeiten selbst werden nicht angefasst: nach dem Loeschen von V steht IV wieder da
        records.remove("Sven Packmaster V");
        assertEquals(List.of("Sven Packmaster IV"), keys(records));
        assertEquals(List.of(900L), SlayerKillStats.top(records, 3).get(0).best());
    }

    @Test
    void anUnknownBossComesLastAndTheTiersOfOneBossAreNotMixed() {
        Map<String, List<Long>> records = new LinkedHashMap<>();
        records.put("Zorg The New One V", times(5L));
        records.put("Revenant Horror IV", times(9_000L));
        records.put("Revenant Horror V", times(1_000L));
        List<SlayerKillStats.Top> top = SlayerKillStats.top(records, 3);
        assertEquals(2, top.size(), "Stufe IV faellt weg, sobald es Stufe V gibt");
        assertEquals("Revenant Horror V", top.get(0).key());
        assertEquals(List.of(1_000L), top.get(0).best(), "Stufe 5 bleibt bei ihren eigenen Zeiten, nicht mit denen der Stufe 4 vermischt");
        assertEquals("Zorg The New One V", top.get(1).key(), "ein unbekannter Boss kommt zuletzt");
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
