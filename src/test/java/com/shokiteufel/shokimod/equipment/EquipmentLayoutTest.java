package com.shokiteufel.shokimod.equipment;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wo die vier Plaetze im Inventarfenster stehen: rechts neben der Spielfigur, in der Luecke vor dem Bastelfeld; wohin der Zweithand-Platz und der Knopf des Rezeptbuchs
 * rueken; welcher Platz unter dem Zeiger liegt; und das Sechseck auf einem leeren Platz. (Bis 2.5.6 stand ein Streifen links neben dem Fenster: seine Pruefungen
 * gelten nicht mehr, das Fenster traegt die Plaetze jetzt selbst.)
 */
class EquipmentLayoutTest {

    /** Die Zahlen des Spiels (Inventarfenster 176 breit): Spielfigur bis 75, Bastelfeld ab 98, Ausgabe bei 154 */
    private static final int MODEL_RIGHT = 75;
    private static final int CRAFTING_LEFT = 98;
    private static final int CRAFTING_ROWS_BOTTOM = 54;

    @Test
    void theFourFramesSitInTheGapBetweenTheCharacterAndTheCraftingGrid() {
        assertEquals(76, EquipmentLayout.FRAME_X);
        assertTrue(EquipmentLayout.FRAME_X > MODEL_RIGHT, "rechts von der Spielfigur");
        assertTrue(EquipmentLayout.FRAME_X + EquipmentLayout.SLOT <= CRAFTING_LEFT, "und vor dem Bastelfeld, nicht darauf");
        assertEquals(7, EquipmentLayout.FRAME_Y);
        // Der Gegenstand steht einen Punkt im Rahmen: bei 77 / 8, wie der Zweithand-Platz des Spiels in der Breite, der Helm links in der Hoehe
        assertEquals(77, EquipmentLayout.FRAME_X + 1);
        assertEquals(8, EquipmentLayout.FRAME_Y + 1);
    }

    @Test
    void theFramesFollowTheWindowWhereverItSits() {
        assertEquals(300 + 76, EquipmentLayout.slotX(300));
        assertEquals(50 + 7, EquipmentLayout.slotY(50, 0));
        assertEquals(50 + 7 + 54, EquipmentLayout.slotY(50, 3));
        // Mit dem Rezeptbuch sitzt das Fenster weiter rechts: Es ist dieselbe Rechnung, ohne Sonderfall
        assertEquals(EquipmentLayout.slotX(250) + 80, EquipmentLayout.slotX(330));
    }

    @Test
    void theFourFramesTouchEachOtherInAColumn() {
        for (int i = 1; i < EquipmentRules.SLOTS; i++) {
            assertEquals(EquipmentLayout.SLOT, EquipmentLayout.slotY(0, i) - EquipmentLayout.slotY(0, i - 1));
        }
        // Der letzte endet bei 7 + 72 = 79, wie die Zeile des Zweithand-Platzes (61 bis 79)
        assertEquals(79, EquipmentLayout.slotY(0, 3) + EquipmentLayout.SLOT);
    }

    @Test
    void theFourthFrameLiesOnTheOldOffhandSlot() {
        // Der Zweithand-Platz des Spiels (77 / 62) ist genau der vierte Platz: Darum muss er weg
        assertEquals(EquipmentLayout.OFFHAND_X, EquipmentLayout.FRAME_X + 1);
        assertEquals(EquipmentLayout.OFFHAND_Y, EquipmentLayout.FRAME_Y + 1 + 3 * EquipmentLayout.SLOT);
    }

    @Test
    void theOffhandSlotMovesRightUnderTheCraftingGridAndKeepsItsRow() {
        int newX = EquipmentLayout.OFFHAND_X + EquipmentLayout.SHIFT;
        assertEquals(98, newX, "unter dem linken Platz des Bastelfeldes");
        assertEquals(newX, CRAFTING_LEFT);
        assertEquals(newX - 1, EquipmentLayout.movedOffhandFrameX());
        assertEquals(EquipmentLayout.OFFHAND_Y - 1, EquipmentLayout.offhandFrameY());
        assertTrue(EquipmentLayout.offhandFrameY() > CRAFTING_ROWS_BOTTOM, "unter den Plaetzen des Bastelfeldes, nicht auf ihnen");
        // Er stoesst nicht an den vierten Equipment-Platz
        assertTrue(EquipmentLayout.movedOffhandFrameX() >= EquipmentLayout.FRAME_X + EquipmentLayout.SLOT);
    }

    @Test
    void theRecipeBookButtonMovesBehindTheOffhandSlotAndStaysInsideTheWindow() {
        int x = EquipmentLayout.BUTTON_X + EquipmentLayout.SHIFT;
        assertEquals(125, x);
        assertTrue(x >= EquipmentLayout.movedOffhandFrameX() + EquipmentLayout.SLOT, "hinter dem Rahmen des Zweithand-Platzes");
        assertTrue(x + EquipmentLayout.BUTTON_WIDTH <= EquipmentLayout.IMAGE_WIDTH, "noch im Fenster");
        // Vorher lag der Knopf auf dem verschobenen Zweithand-Platz - das ist der Grund fuer die Verschiebung
        int oldX = EquipmentLayout.BUTTON_X;
        assertTrue(oldX < EquipmentLayout.movedOffhandFrameX() + EquipmentLayout.SLOT && oldX + EquipmentLayout.BUTTON_WIDTH > EquipmentLayout.movedOffhandFrameX());
    }

    @Test
    void thePlaceUnderThePointerIsFoundInsideTheFourFrames() {
        int left = 100;
        int top = 10;
        int sx = EquipmentLayout.slotX(left);
        int sy = EquipmentLayout.slotY(top, 0);
        assertEquals(0, EquipmentLayout.slotAt(left, top, sx, sy));
        assertEquals(0, EquipmentLayout.slotAt(left, top, sx + 17.9, sy + 17.9));
        assertEquals(1, EquipmentLayout.slotAt(left, top, sx, sy + 18));
        assertEquals(2, EquipmentLayout.slotAt(left, top, sx + 9, sy + 36 + 5));
        assertEquals(3, EquipmentLayout.slotAt(left, top, sx + 9, sy + 54 + 17.9));
    }

    @Test
    void outsideTheFramesThereIsNoPlace() {
        int left = 100;
        int top = 10;
        int sx = EquipmentLayout.slotX(left);
        int sy = EquipmentLayout.slotY(top, 0);
        assertEquals(-1, EquipmentLayout.slotAt(left, top, sx - 0.1, sy + 5), "links vom Rahmen (die Spielfigur)");
        assertEquals(-1, EquipmentLayout.slotAt(left, top, sx + 18, sy + 5), "rechts vom Rahmen (das Bastelfeld)");
        assertEquals(-1, EquipmentLayout.slotAt(left, top, sx + 5, sy - 0.1), "ueber dem ersten Platz");
        assertEquals(-1, EquipmentLayout.slotAt(left, top, sx + 5, sy + 72), "unter dem vierten Platz");
    }

    @Test
    void theMovedOffhandSlotIsNotAnEquipmentPlace() {
        int left = 100;
        int top = 10;
        // Mitte des verschobenen Zweithand-Platzes: Die Maus dort gehoert dem Spiel
        double x = left + EquipmentLayout.OFFHAND_X + EquipmentLayout.SHIFT + 8;
        double y = top + EquipmentLayout.OFFHAND_Y + 8;
        assertEquals(-1, EquipmentLayout.slotAt(left, top, x, y));
    }

    @Test
    void theHexagonStaysInsideTheItemCellAndIsSymmetric() {
        Set<Long> pixels = new HashSet<>();
        for (int[] part : EquipmentLayout.HEXAGON) {
            assertEquals(4, part.length);
            assertTrue(part[0] >= 0 && part[1] >= 0 && part[2] > 0 && part[3] > 0);
            assertTrue(part[0] + part[2] <= 16 && part[1] + part[3] <= 16, "im Feld von 16 mal 16");
            for (int dx = 0; dx < part[2]; dx++) {
                for (int dy = 0; dy < part[3]; dy++) {
                    assertTrue(pixels.add(((long) (part[0] + dx) << 32) | (part[1] + dy)), "kein Punkt zweimal");
                }
            }
        }
        for (long p : pixels) {
            int x = (int) (p >> 32);
            int y = (int) p;
            assertTrue(pixels.contains(((long) (15 - x) << 32) | y), "links wie rechts: " + x + "/" + y);
            assertTrue(pixels.contains(((long) x << 32) | (15 - y)), "oben wie unten: " + x + "/" + y);
        }
    }

    @Test
    void theHexagonIsAnOutlineNotAFilledShape() {
        Set<Long> pixels = new HashSet<>();
        for (int[] part : EquipmentLayout.HEXAGON) {
            for (int dx = 0; dx < part[2]; dx++) {
                for (int dy = 0; dy < part[3]; dy++) pixels.add(((long) (part[0] + dx) << 32) | (part[1] + dy));
            }
        }
        assertFalse(pixels.contains(((long) 7 << 32) | 8), "die Mitte bleibt leer");
        assertFalse(pixels.contains(((long) 8 << 32) | 7), "die Mitte bleibt leer");
        assertTrue(pixels.contains(((long) 7 << 32) | 0), "die obere Spitze");
        assertTrue(pixels.contains(((long) 8 << 32) | 15), "die untere Spitze");
        assertTrue(pixels.contains(((long) 1 << 32) | 8), "die linke Seite");
        assertTrue(pixels.contains(((long) 14 << 32) | 8), "die rechte Seite");
    }
}
