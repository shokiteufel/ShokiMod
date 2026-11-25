package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Das Protokoll des Forge-Fensters (2.4.8): Wie eine Zeile aussieht und wie oft ein Fenster ins Log darf. */
class ForgeWindowTest {

    @BeforeEach
    void leeresGedaechtnis() {
        ForgeWindow.reset();
    }

    @Test
    void aLineHoldsTheSlotTheItemTheNameAndEveryLoreLine() {
        String line = ForgeWindow.describe(12, "minecraft:diamond", 1, "Refined Diamond",
                List.of("Time Remaining: 3h 12m 5s", "", "Click to collect!"));
        assertEquals("12: minecraft:diamond 'Refined Diamond' | Time Remaining: 3h 12m 5s | Click to collect!", line,
                "leere Zeilen fallen weg, der Rest steht in der Reihenfolge des Spiels");
    }

    @Test
    void aStackOfSeveralShowsItsCount() {
        assertEquals("5: minecraft:coal x16 'Coal'", ForgeWindow.describe(5, "minecraft:coal", 16, "Coal", List.of()));
    }

    @Test
    void aWindowIsLoggedAFewTimesAndThenNoMore() {
        for (int i = 0; i < ForgeWindow.MAX_OPENINGS_PER_TITLE; i++) assertTrue(ForgeWindow.mayLog("The Forge"), "Oeffnung " + (i + 1));
        assertFalse(ForgeWindow.mayLog("The Forge"), "danach ist das Log nicht mehr voll von Forge-Fenstern");
    }

    @Test
    void everyTitleHasItsOwnBudget() {
        for (int i = 0; i < ForgeWindow.MAX_OPENINGS_PER_TITLE; i++) ForgeWindow.mayLog("The Forge");
        assertTrue(ForgeWindow.mayLog("Forge Recipes"), "ein anderes Fenster mit Forge im Titel kommt noch dran");
    }

    @Test
    void onlyAFewDifferentTitlesAreKept() {
        for (int i = 0; i < ForgeWindow.MAX_TITLES; i++) assertTrue(ForgeWindow.mayLog("Forge " + i));
        assertFalse(ForgeWindow.mayLog("Forge one more"), "sonst wuerde jedes Fenster mit Forge im Titel ein Eintrag");
    }
}
