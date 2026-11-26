package com.shokiteufel.shokimod.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wo die vier Plaetze neben dem Inventarfenster stehen: der Streifen links, bei offenem Rezeptbuch links davon, und welcher Platz unter dem Zeiger liegt. */
class EquipmentLayoutTest {

    private static final int GUI_WIDTH = 480;
    /** Wo das Spiel das Fenster bei geschlossenem Rezeptbuch hinsetzt: in die Mitte */
    private static final int CENTRED = (GUI_WIDTH - EquipmentLayout.IMAGE_WIDTH) / 2;
    /** Und bei offenem Rezeptbuch (Spiel: 177 + (Breite - 176 - 200) / 2) */
    private static final int WITH_BOOK = 177 + (GUI_WIDTH - EquipmentLayout.IMAGE_WIDTH - 200) / 2;

    @Test
    void theSizesAddUp() {
        assertEquals(26, EquipmentLayout.PANEL_WIDTH);
        assertEquals(80, EquipmentLayout.PANEL_HEIGHT);
        // Der letzte Platz endet vor dem unteren Rand des Streifens - mit demselben Rand wie oben
        assertEquals(EquipmentLayout.PANEL_HEIGHT, EquipmentLayout.slotY(0, 3) + EquipmentLayout.SLOT + EquipmentLayout.PAD);
        assertEquals(EquipmentLayout.PANEL_WIDTH, EquipmentLayout.slotX(0) + EquipmentLayout.SLOT + EquipmentLayout.PAD);
    }

    @Test
    void theStripSitsLeftOfTheWindowWithASmallGap() {
        int x = EquipmentLayout.panelX(CENTRED, GUI_WIDTH);
        assertEquals(CENTRED - EquipmentLayout.GAP - EquipmentLayout.PANEL_WIDTH, x);
        assertEquals(CENTRED - EquipmentLayout.GAP, x + EquipmentLayout.PANEL_WIDTH, "rechts vom Streifen kommt nur der Abstand, dann das Fenster");
    }

    @Test
    void theStripAlignsWithTheTopOfTheWindow() {
        assertEquals(37, EquipmentLayout.panelY(37));
    }

    @Test
    void anOpenRecipeBookIsRecognisedByTheShiftOfTheWindow() {
        assertFalse(EquipmentLayout.recipeBookOpen(CENTRED, GUI_WIDTH));
        assertFalse(EquipmentLayout.recipeBookOpen(CENTRED + 1, GUI_WIDTH), "eine Rundung ist kein Buch");
        assertTrue(EquipmentLayout.recipeBookOpen(WITH_BOOK, GUI_WIDTH));
        assertTrue(WITH_BOOK - CENTRED > 60, "das Buch verschiebt das Fenster um fast achtzig");
    }

    @Test
    void withTheRecipeBookOpenTheStripMovesLeftOfTheBook() {
        int x = EquipmentLayout.panelX(WITH_BOOK, GUI_WIDTH);
        int bookLeft = WITH_BOOK - EquipmentLayout.RECIPE_BOOK_WIDTH - 1;
        assertEquals(bookLeft - EquipmentLayout.GAP, x + EquipmentLayout.PANEL_WIDTH);
        assertTrue(x + EquipmentLayout.PANEL_WIDTH + EquipmentLayout.GAP <= bookLeft, "links vom Buch, nicht auf ihm");
        assertTrue(x >= 0);
    }

    @Test
    void withoutRoomOnTheLeftNothingIsDrawn() {
        int smallest = EquipmentLayout.GAP + EquipmentLayout.PANEL_WIDTH;
        assertEquals(0, EquipmentLayout.panelX(smallest, 2 * smallest + EquipmentLayout.IMAGE_WIDTH), "genau bis an den Rand ist noch Platz");
        // Ein schmaler Bildschirm: Das Fenster sitzt fast am linken Rand
        assertEquals(EquipmentLayout.NO_ROOM, EquipmentLayout.panelX(smallest - 1, 2 * smallest + EquipmentLayout.IMAGE_WIDTH - 2));
        assertEquals(EquipmentLayout.NO_ROOM, EquipmentLayout.panelX(0, EquipmentLayout.IMAGE_WIDTH));
        assertEquals(EquipmentLayout.NO_ROOM, EquipmentLayout.panelX(20, EquipmentLayout.IMAGE_WIDTH + 40));
    }

    @Test
    void thePlaceUnderThePointerIsFoundInsideTheFourFrames() {
        int px = 100;
        int py = 10;
        int sx = EquipmentLayout.slotX(px);
        int sy = EquipmentLayout.slotY(py, 0);
        assertEquals(0, EquipmentLayout.slotAt(px, py, sx, sy));
        assertEquals(0, EquipmentLayout.slotAt(px, py, sx + 17.9, sy + 17.9));
        assertEquals(1, EquipmentLayout.slotAt(px, py, sx, sy + 18));
        assertEquals(2, EquipmentLayout.slotAt(px, py, sx + 9, sy + 36 + 5));
        assertEquals(3, EquipmentLayout.slotAt(px, py, sx + 9, sy + 54 + 17.9));
    }

    @Test
    void outsideTheFramesThereIsNoPlace() {
        int px = 100;
        int py = 10;
        int sx = EquipmentLayout.slotX(px);
        int sy = EquipmentLayout.slotY(py, 0);
        assertEquals(-1, EquipmentLayout.slotAt(px, py, sx - 0.1, sy + 5), "links vom Rahmen");
        assertEquals(-1, EquipmentLayout.slotAt(px, py, sx + 18, sy + 5), "rechts vom Rahmen");
        assertEquals(-1, EquipmentLayout.slotAt(px, py, sx + 5, sy - 0.1), "ueber dem ersten Platz (im Rand)");
        assertEquals(-1, EquipmentLayout.slotAt(px, py, sx + 5, sy + 72), "unter dem vierten Platz");
        assertEquals(-1, EquipmentLayout.slotAt(px, py, px + 1, py + 1), "in der Ecke des Streifens");
    }

    @Test
    void theWholeStripCatchesTheClickAlsoBetweenTheFramesAndOnTheBorder() {
        int px = 100;
        int py = 10;
        assertTrue(EquipmentLayout.inPanel(px, py, px, py));
        assertTrue(EquipmentLayout.inPanel(px, py, px + 25.9, py + 79.9));
        assertTrue(EquipmentLayout.inPanel(px, py, px + 2, py + 40), "auf dem Rand links vom Rahmen");
        assertFalse(EquipmentLayout.inPanel(px, py, px - 0.1, py + 5));
        assertFalse(EquipmentLayout.inPanel(px, py, px + 26, py + 5));
        assertFalse(EquipmentLayout.inPanel(px, py, px + 5, py - 0.1));
        assertFalse(EquipmentLayout.inPanel(px, py, px + 5, py + 80));
    }
}
