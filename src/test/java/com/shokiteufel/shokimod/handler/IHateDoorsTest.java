package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.GlassColour;
import com.shokiteufel.shokimod.dungeonkarte.Raster;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "I hate doors" (von NoammAddons): wo eine Tuer liegt, nach seiner Rechnung - von der Ecke des ersten Raums an in Schritten von 16 Bloecken,
 * ein Platz ist eine Tuer, wenn Zeile und Spalte zusammen ungerade sind, drei Bloecke breit, Hoehe 69 bis 72 - und dass von Haus aus nichts ersetzt wird.
 */
class IHateDoorsTest {

    private static final int START = Raster.START;   // Mitte des ersten Raums: -185

    @Test
    void theDoorBetweenTheFirstTwoRoomsOfTheFirstRowIsFoundOnAllThreeBlocksAcrossAndInAllFourHeights() {
        int x = START + 16;     // zwischen Raum 0 und Raum 1 der ersten Zeile
        int z = START;
        for (int y = 69; y <= 72; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    assertTrue(IHateDoors.tuerPlatz(x + dx, y, z + dz), x + dx + "," + y + "," + (z + dz));
                }
            }
        }
    }

    @Test
    void theDoorIsOnlyThreeBlocksWideAndFourHigh() {
        int x = START + 16;
        int z = START;
        assertFalse(IHateDoors.tuerPlatz(x + 2, 70, z), "ein Block daneben: Wand oder Raum");
        assertFalse(IHateDoors.tuerPlatz(x - 2, 70, z));
        assertFalse(IHateDoors.tuerPlatz(x, 70, z + 2));
        assertFalse(IHateDoors.tuerPlatz(x, 68, z), "unter der Tuer");
        assertFalse(IHateDoors.tuerPlatz(x, 73, z), "ueber der Tuer");
    }

    @Test
    void aRoomPlaceIsNeverADoor() {
        // Raummitten: gerade Zeile und gerade Spalte
        for (int zeile = 0; zeile <= 10; zeile += 2) {
            for (int spalte = 0; spalte <= 10; spalte += 2) {
                assertFalse(IHateDoors.tuerPlatz(START + spalte * 16, 70, START + zeile * 16), "Raum " + zeile + "/" + spalte);
            }
        }
    }

    @Test
    void everyOddPlaceOfTheGridIsADoorAndTheCountIsSixty() {
        int tueren = 0;
        for (int zeile = 0; zeile <= 10; zeile++) {
            for (int spalte = 0; spalte <= 10; spalte++) {
                boolean tuer = IHateDoors.tuerPlatz(START + spalte * 16, 70, START + zeile * 16);
                assertEquals(((zeile + spalte) & 1) != 0, tuer, zeile + "/" + spalte);
                if (tuer) tueren++;
            }
        }
        assertEquals(60, tueren, "ein 6 mal 6 Raster hat 60 Tuer-Plaetze (60 von 121 Plaetzen sind ungerade)");
    }

    @Test
    void outsideTheGridThereAreNoDoors() {
        assertFalse(IHateDoors.tuerPlatz(START - 20, 70, START + 16));
        assertFalse(IHateDoors.tuerPlatz(START + 16, 70, START - 20));
        assertFalse(IHateDoors.tuerPlatz(START + 10 * 16 + 20, 70, START + 16));
        assertFalse(IHateDoors.tuerPlatz(START + 16, 70, START + 10 * 16 + 20));
    }

    @Test
    void theLastDoorOfTheGridIsStillFound() {
        // Zeile 9, Spalte 10: ungerade Summe, ganz am Rand des Rasters
        assertTrue(IHateDoors.tuerPlatz(START + 10 * 16 + 1, 70, START + 9 * 16 - 1));
    }

    @Test
    void nothingIsReplacedByDefault() {
        // Von Haus aus ist nichts eingestellt: der Block geht unveraendert zurueck, ohne dass er auch nur angesehen wird (null waere sonst ein Fehler)
        assertNull(IHateDoors.renderState(START + 16, 70, START, null));
    }

    @Test
    void theSettingsAreOffByDefaultAndTheColoursAreTheOnesOfNoammAddons() {
        ModConfig.IHateDoorsCategory c = new ModConfig.IHateDoorsCategory();
        assertFalse(c.enabled);
        assertFalse(c.glassEntrance);
        assertFalse(c.glassWither);
        assertFalse(c.glassBlood);
        assertEquals(GlassColour.WHITE, c.entranceGlass, "Entrance: weiss");
        assertEquals(GlassColour.BLACK, c.witherGlass, "Wither: schwarz");
        assertEquals(GlassColour.RED, c.bloodGlass, "Blood: rot");
    }

    @Test
    void theGlassListIsTheOneOfNoammAddons() {
        assertEquals(17, GlassColour.values().length, "das gewoehnliche Glas und die 16 Farben");
        assertEquals(GlassColour.DEFAULT, GlassColour.values()[0]);
        assertEquals("glass", GlassColour.DEFAULT.blockId());
        assertEquals("light_blue_stained_glass", GlassColour.LIGHT_BLUE.blockId());
        assertEquals("light_gray_stained_glass", GlassColour.LIGHT_GRAY.blockId());
        for (GlassColour g : GlassColour.values()) {
            if (g == GlassColour.DEFAULT) continue;
            assertTrue(g.blockId().endsWith("_stained_glass"), g.name());
            assertEquals(g.name().toLowerCase(java.util.Locale.ROOT) + "_stained_glass", g.blockId());
        }
        assertEquals("Light Blue", GlassColour.LIGHT_BLUE.toString(), "so steht es im Auswahlfeld");
    }
}
