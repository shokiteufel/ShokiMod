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

    @Test
    void aQuietWindowGivesNoWarning() {
        long[] z = zaehler();
        z[Zaehler.BILD_GEMALT.ordinal()] = 5;          // 0,5 je Sekunde
        z[Zaehler.SCAN_DURCHGAENGE.ordinal()] = 40;    // 4 je Sekunde
        List<String> w = DungeonLog.bewerten(z, zeiten(), 10.0, 140.0, true, new int[Zaehler.values().length], new int[Zeit.values().length]);
        assertTrue(w.isEmpty(), w.toString());
    }

    @Test
    void repaintingTheMapImageEveryFrameIsReportedWithTheRate() {
        long[] z = zaehler();
        z[Zaehler.BILD_GEMALT.ordinal()] = 1400;       // 140 je Sekunde: in jedem Bild
        List<String> w = DungeonLog.bewerten(z, zeiten(), 10.0, 140.0, true, new int[Zaehler.values().length], new int[Zeit.values().length]);
        assertEquals(1, w.size());
        assertTrue(w.get(0).startsWith("TOO OFTEN: map image repainted 140.0/s (limit 4/s)"), w.get(0));
    }

    @Test
    void theSameLimitBrokenAgainIsMarkedAsInARow() {
        long[] z = zaehler();
        z[Zaehler.SCAN_WELTFRAGEN.ordinal()] = 40_000;   // 4000 je Sekunde
        int[] serie = new int[Zaehler.values().length];
        int[] serieZeit = new int[Zeit.values().length];
        String erst = DungeonLog.bewerten(z, zeiten(), 10.0, 100.0, true, serie, serieZeit).get(0);
        String zweit = DungeonLog.bewerten(z, zeiten(), 10.0, 100.0, true, serie, serieZeit).get(0);
        assertFalse(erst.contains("in a row"), erst);
        assertTrue(zweit.contains("for 2 windows in a row"), zweit);

        // ein ruhiges Fenster unterbricht die Reihe
        DungeonLog.bewerten(zaehler(), zeiten(), 10.0, 100.0, true, serie, serieZeit);
        String neu = DungeonLog.bewerten(z, zeiten(), 10.0, 100.0, true, serie, serieZeit).get(0);
        assertFalse(neu.contains("in a row"), neu);
    }

    @Test
    void informationOnlyCountersNeverWarn() {
        long[] z = zaehler();
        z[Zaehler.KARTE_GEZEICHNET.ordinal()] = 1_000_000;
        z[Zaehler.STERN_MOBS.ordinal()] = 1_000_000;
        assertTrue(DungeonLog.bewerten(z, zeiten(), 10.0, 100.0, true, new int[Zaehler.values().length], new int[Zeit.values().length]).isEmpty());
    }

    @Test
    void drawingThatTakesTooLongPerSecondIsReported() {
        long[] t = zeiten();
        t[Zeit.KARTE_ZEICHNEN.ordinal()] = 600_000_000L;   // 600 ms in 10 s = 60 ms je Sekunde, Grenze 25
        List<String> w = DungeonLog.bewerten(zaehler(), t, 10.0, 100.0, true, new int[Zaehler.values().length], new int[Zeit.values().length]);
        assertEquals(1, w.size());
        assertTrue(w.get(0).contains("map drawing takes 60.0 ms every second"), w.get(0));
    }

    @Test
    void lowFramesPerSecondAreReportedOnlyWhenFramesWereCounted() {
        List<String> mit = DungeonLog.bewerten(zaehler(), zeiten(), 10.0, 12.0, true, new int[Zaehler.values().length], new int[Zeit.values().length]);
        assertEquals(1, mit.size());
        assertTrue(mit.get(0).startsWith("LOW FPS: 12 frames per second"), mit.get(0));
        List<String> ohne = DungeonLog.bewerten(zaehler(), zeiten(), 10.0, 0.0, false, new int[Zaehler.values().length], new int[Zeit.values().length]);
        assertTrue(ohne.isEmpty(), "ohne gezaehlte Bilder (zum Beispiel ein Fenster ohne HUD) keine FPS-Meldung");
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
