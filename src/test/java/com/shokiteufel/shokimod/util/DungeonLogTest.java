package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.util.DungeonLog.Zaehler;
import com.shokiteufel.shokimod.util.DungeonLog.Zeit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Das Dungeon log: wann eine Zeile "zu oft" meldet, was als Chatzeile des Laufs zaehlt, und dass ohne Lauf nichts kostet */
class DungeonLogTest {

    private static long[] zaehler() {
        return new long[Zaehler.values().length];
    }

    private static long[] zeiten() {
        return new long[Zeit.values().length];
    }

    private static int[] reihe() {
        return new int[Zaehler.values().length];
    }

    private static int[] reiheZeit() {
        return new int[Zeit.values().length];
    }

    /** Ein ganz gewoehnliches Fenster: zehn Sekunden, nicht das erste des Laufs, 1400 Bilder gezeichnet */
    private static List<String> fenster(long[] z, long[] t, double fps) {
        return DungeonLog.bewerten(z, t, 10.0, fps, 1400, false, reihe(), reiheZeit());
    }

    @Test
    void aQuietWindowGivesNoWarning() {
        long[] z = zaehler();
        z[Zaehler.BILD_GEMALT.ordinal()] = 5;          // 0,5 je Sekunde
        z[Zaehler.SCAN_DURCHGAENGE.ordinal()] = 40;    // 4 je Sekunde
        List<String> w = fenster(z, zeiten(), 140.0);
        assertTrue(w.isEmpty(), w.toString());
    }

    @Test
    void repaintingTheMapImageEveryFrameIsReportedWithTheRate() {
        long[] z = zaehler();
        z[Zaehler.BILD_GEMALT.ordinal()] = 1400;       // 140 je Sekunde: in jedem Bild
        List<String> w = fenster(z, zeiten(), 140.0);
        assertEquals(1, w.size());
        assertTrue(w.get(0).startsWith("TOO OFTEN: map image repainted 140.0/s (limit 4/s)"), w.get(0));
    }

    @Test
    void theSameLimitBrokenAgainIsMarkedAsInARow() {
        long[] z = zaehler();
        z[Zaehler.SCAN_WELTFRAGEN.ordinal()] = 40_000;   // 4000 je Sekunde
        int[] serie = reihe();
        int[] serieZeit = reiheZeit();
        String erst = DungeonLog.bewerten(z, zeiten(), 10.0, 100.0, 1000, false, serie, serieZeit).get(0);
        String zweit = DungeonLog.bewerten(z, zeiten(), 10.0, 100.0, 1000, false, serie, serieZeit).get(0);
        assertFalse(erst.contains("in a row"), erst);
        assertTrue(zweit.contains("for 2 windows in a row"), zweit);

        // ein ruhiges Fenster unterbricht die Reihe
        DungeonLog.bewerten(zaehler(), zeiten(), 10.0, 100.0, 1000, false, serie, serieZeit);
        String neu = DungeonLog.bewerten(z, zeiten(), 10.0, 100.0, 1000, false, serie, serieZeit).get(0);
        assertFalse(neu.contains("in a row"), neu);
    }

    @Test
    void informationOnlyCountersNeverWarn() {
        long[] z = zaehler();
        z[Zaehler.KARTE_GEZEICHNET.ordinal()] = 1_000_000;
        z[Zaehler.STERN_MOBS.ordinal()] = 1_000_000;
        assertTrue(fenster(z, zeiten(), 100.0).isEmpty());
    }

    @Test
    void drawingThatCostsTooMuchInEveryFrameIsReported() {
        long[] t = zeiten();
        t[Zeit.KARTE_ZEICHNEN.ordinal()] = 700_000_000L;   // 700 ms ueber 1400 Bilder = 0,5 ms je Bild, Grenze 0,4
        List<String> w = fenster(zaehler(), t, 140.0);
        assertEquals(1, w.size());
        assertTrue(w.get(0).contains("map drawing takes 0.50 ms per frame (limit 0.40 ms)"), w.get(0));
    }

    @Test
    void theDrawingCostOfARealRunIsNoWarning() {
        // Gemessen an einem echten Lauf (Floor 5): 0,08 bis 0,18 ms je Bild bei rund 140 Bildern je Sekunde = bis 25 ms je Sekunde.
        // Eine Grenze je Sekunde hatte das einmal gemeldet, obwohl jedes Bild nur 0,18 ms kostete.
        long[] t = zeiten();
        t[Zeit.KARTE_ZEICHNEN.ordinal()] = 254_000_000L;   // 25,4 ms je Sekunde ueber zehn Sekunden
        List<String> w = DungeonLog.bewerten(zaehler(), t, 10.0, 139.0, 1390, false, reihe(), reiheZeit());
        assertTrue(w.isEmpty(), w.toString());
    }

    @Test
    void theStarMobWorkIsJudgedPerSecond() {
        long[] t = zeiten();
        t[Zeit.STERN_ARBEIT.ordinal()] = 700_000_000L;   // 70 ms je Sekunde, Grenze 60
        List<String> w = fenster(zaehler(), t, 140.0);
        assertEquals(1, w.size());
        assertTrue(w.get(0).contains("star mob work takes 70.0 ms every second (limit 60 ms)"), w.get(0));
        t[Zeit.STERN_ARBEIT.ordinal()] = 100_000_000L;   // 10 ms je Sekunde
        assertTrue(fenster(zaehler(), t, 140.0).isEmpty());
    }

    @Test
    void lowFramesPerSecondAreReportedOnlyWhenFramesWereCounted() {
        List<String> mit = DungeonLog.bewerten(zaehler(), zeiten(), 10.0, 12.0, 120, false, reihe(), reiheZeit());
        assertEquals(1, mit.size());
        assertTrue(mit.get(0).startsWith("LOW FPS: 12 frames per second"), mit.get(0));
        List<String> ohne = DungeonLog.bewerten(zaehler(), zeiten(), 10.0, 0.0, 0, false, reihe(), reiheZeit());
        assertTrue(ohne.isEmpty(), "ohne gezaehlte Bilder (zum Beispiel ein Fenster ohne HUD) keine FPS-Meldung");
    }

    @Test
    void theFirstWindowOfARunIsNotJudgedByTheCounters() {
        // Echter Lauf: im ersten Fenster fuellt sich die Karte - 46 bis 71 Rasteraenderungen in zehn Sekunden, danach 0 bis 8
        long[] z = zaehler();
        z[Zaehler.RASTER_AENDERUNGEN.ordinal()] = 66;
        int[] serie = reihe();
        assertTrue(DungeonLog.bewerten(z, zeiten(), 10.0, 137.0, 1370, true, serie, reiheZeit()).isEmpty(), "der Anlauf ist kein Fehler");
        assertEquals(0, serie[Zaehler.RASTER_AENDERUNGEN.ordinal()], "und zaehlt nicht als Anfang einer Reihe");
        // dieselbe Menge in einem spaeteren Fenster ist eine Meldung wert
        List<String> spaeter = DungeonLog.bewerten(z, zeiten(), 10.0, 137.0, 1370, false, reihe(), reiheZeit());
        assertEquals(1, spaeter.size());
        assertTrue(spaeter.get(0).startsWith("TOO OFTEN: grid changes 6.6/s (limit 6/s)"), spaeter.get(0));
    }

    @Test
    void theFirstWindowStillCountsForTheFramesPerSecond() {
        List<String> w = DungeonLog.bewerten(zaehler(), zeiten(), 10.0, 12.0, 120, true, reihe(), reiheZeit());
        assertEquals(1, w.size());
        assertTrue(w.get(0).startsWith("LOW FPS"), w.get(0));
    }

    @Test
    void aWindowShorterThanThreeSecondsIsNotJudged() {
        // Der Rest am Ende eines Laufs: 0,4 s mit 35 Bildern wurden als "78 fps" geschrieben, und ein Ruckler macht aus so einem Fenster alles
        long[] z = zaehler();
        z[Zaehler.RASTER_AENDERUNGEN.ordinal()] = 45;    // 15 je Sekunde bei 3 s - aber das Fenster ist kuerzer
        assertTrue(DungeonLog.bewerten(z, zeiten(), 2.9, 5.0, 15, false, reihe(), reiheZeit()).isEmpty());
        assertEquals(1, DungeonLog.bewerten(z, zeiten(), 3.0, 140.0, 420, false, reihe(), reiheZeit()).size(), "ab drei Sekunden gilt es");
    }

    @Test
    void theWindowLineNamesEveryCounterAndTheFrameFigures() {
        long[] z = zaehler();
        z[Zaehler.KARTE_GELESEN.ordinal()] = 3;
        long[] t = zeiten();
        t[Zeit.KARTE_ZEICHNEN.ordinal()] = 10_000_000L;
        String text = DungeonLog.fensterText(10.0, 142.0, 61_000_000L, z, t, 1420, 900, 2, 40);
        assertTrue(text.contains("fps 142"), text);
        assertTrue(text.contains("longest frame 61 ms"), text);
        assertTrue(text.contains("map read from the hotbar item 3"), text);
        assertTrue(text.contains("map drawing 1.0 ms/s (0.007 ms/frame)"), text);
        assertTrue(text.contains("star mob work 0.0 ms/s"), text);
        assertTrue(text.contains("GC 2 runs 40 ms"), text);
        for (Zaehler c : Zaehler.values()) assertTrue(text.contains(c.name), c.name);
    }

    @Test
    void onlyTheChatLinesOfTheRunAreKept() {
        assertTrue(DungeonLog.wichtig("Anna opened a WITHER door!"));
        assertTrue(DungeonLog.wichtig("The BLOOD DOOR has been opened!"));
        assertTrue(DungeonLog.wichtig("[BOSS] The Watcher: You have proven yourself."));
        assertTrue(DungeonLog.wichtig(" ☠ Bert was killed by a Zombie and became a ghost."));
        assertTrue(DungeonLog.wichtig("[NPC] Mort: Here, I found this map when I first entered the dungeon."));
        assertTrue(DungeonLog.wichtig("            The Catacombs - Floor V"));
        assertTrue(DungeonLog.wichtig("Team Score: 301 (S+)"));
        assertFalse(DungeonLog.wichtig("[Guild] Anna: gg"));
        assertFalse(DungeonLog.wichtig("You found a Wolf Pet!"));
    }

    @Test
    void withoutARunEverythingIsAnEmptyCall() {
        assertFalse(DungeonLog.aktiv());
        assertEquals(0L, DungeonLog.beginn(), "ohne Lauf wird die Uhr nicht gefragt");
        DungeonLog.zaehlen(Zaehler.BILD_GEMALT);
        DungeonLog.zaehlen(Zaehler.SCAN_WELTFRAGEN, 50);
        DungeonLog.ende(Zeit.KARTE_ZEICHNEN, 0L);
        DungeonLog.ende(Zeit.STERN_ARBEIT, 0L);
        DungeonLog.ereignis("nothing");
        DungeonLog.chat("Anna opened a WITHER door!");
        DungeonLog.bild();
        assertFalse(DungeonLog.aktiv());
    }

    @Test
    void timeIsWrittenAsMinutesAndSeconds() {
        assertEquals("0:05", DungeonLog.zeitText(5));
        assertEquals("12:34", DungeonLog.zeitText(12 * 60 + 34));
    }
}
