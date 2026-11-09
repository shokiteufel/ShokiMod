package com.shokiteufel.shokimod.scanner;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gebiet, Server-ID und Spielart aus den Zeilen, die Hypixel liefert. Die Formen stammen aus dem Mitschnitt vom 08.11. */
class StandortTest {

    @Test
    void readsTheAreaFromTheLineHypixelSends() {
        assertEquals("Private Island", Standort.gebiet("Area: Private Island"));
        assertEquals("Jerry's Workshop", Standort.gebiet("Area: Jerry's Workshop"));
    }

    @Test
    void readsTheServerFromTheLineWithItsLeadingSpace() {
        // Hypixel schreibt die Zeile als " Server: " plus Kennung in einem eigenen Textteil; als Klartext bleibt der Leerraum davor stehen
        assertEquals("mini22CD", Standort.serverId("  Server: mini22CD"));
        assertEquals("mini22CD", Standort.serverId("Server: mini22CD"));
    }

    @Test
    void ignoresCaseAndSurroundingBlanks() {
        assertEquals("Crimson Isle", Standort.gebiet("   area:   Crimson Isle  "));
        assertEquals("m1A", Standort.serverId(" SERVER:m1A"));
    }

    @Test
    void aLineWithoutContentSaysNothing() {
        assertNull(Standort.gebiet("Area:"));
        assertNull(Standort.gebiet("Area:   "));
        assertNull(Standort.serverId("Server: "));
    }

    @Test
    void otherLinesAndNullSayNothing() {
        assertNull(Standort.gebiet("Players (12)"));
        assertNull(Standort.serverId("Area: Garden"));
        assertNull(Standort.gebiet("Server: m1A"));
        assertNull(Standort.gebiet(null));
        assertNull(Standort.serverId(null));
        assertFalse(Standort.istStandortZeile("Players (12)"));
        assertFalse(Standort.istStandortZeile(null));
        assertTrue(Standort.istStandortZeile("Area: Garden"));
        assertTrue(Standort.istStandortZeile(" Server: m1A"));
    }

    @Test
    void theUpperLineWinsWhenTheListHasTwo() {
        Standort.Treffer t = Standort.ausZeilen(List.of("Players (3)", "Area: Garden", "Server: m1A", "Area: Hub", "Server: m2B"));
        assertEquals("Garden", t.gebiet());
        assertEquals("m1A", t.serverId());
    }

    @Test
    void missingLinesStayNull() {
        Standort.Treffer nurGebiet = Standort.ausZeilen(List.of("Area: Garden"));
        assertEquals("Garden", nurGebiet.gebiet());
        assertNull(nurGebiet.serverId());
        Standort.Treffer nichts = Standort.ausZeilen(List.of());
        assertNull(nichts.gebiet());
        assertNull(nichts.serverId());
    }

    @Test
    void gameTypeComesFromTheSidebarTitle() {
        assertEquals("SKYBLOCK", Standort.spielart("SKYBLOCK"));
        assertEquals("SKYBLOCK", Standort.spielart("§e§lSKYBLOCK"));
        assertEquals("SKYBLOCK", Standort.spielart("  Skyblock  "));
        assertEquals("Unknown", Standort.spielart("BED WARS"));
    }

    @Test
    void anEmptyTitleKeepsTheLastValue() {
        // Kurz nach einem Weltwechsel gibt es noch keine Seitenleiste: null heisst "nichts aendern"
        assertNull(Standort.spielart(""));
        assertNull(Standort.spielart("   "));
        assertNull(Standort.spielart(null));
        assertNull(Standort.spielart("§a§l"));
    }
}
