package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.garden.PlotsState;
import com.shokiteufel.shokimod.garden.VisitorBook;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die zwei Kaesten des Gartens: die Karte der Plots (Ueberschrift, fuenf Zeilen, bei Befall eine Zaehlzeile) und die Einkaufsliste (ohne Besucher leer).
 */
class GardenBoxesTest {

    @AfterEach
    void reset() {
        PlotsState.reset();
        VisitorBook.INSTANCE.clear();
    }

    @Test
    void theMapHasAHeaderAndFiveRows() {
        PlotsState.reset();
        HudPanel panel = GardenPlotsHud.build();
        assertEquals(1 + 5, panel.rowCount());
        assertEquals(1, GardenPlotsHud.HEADER_ROWS, "die Klick-Rechnung zaehlt die Ueberschrift mit");
    }

    @Test
    void infestedPlotsAddACountRow() {
        PlotsState.onTabList(List.of(" Plots: 4, 12"));
        assertEquals(1 + 5 + 1, GardenPlotsHud.build().rowCount());
    }

    @Test
    void theShoppingListIsEmptyWithoutVisitors() {
        VisitorBook.INSTANCE.clear();
        assertTrue(VisitorShoppingHud.build().isEmpty());
    }

    @Test
    void theShoppingListHasOneRowPerLineOfTheText() {
        VisitorBook.INSTANCE.getOrCreate("Jacob");
        VisitorBook.INSTANCE.getOrCreate("Anita");
        // Ein neuer Besucher je Zeile plus Ueberschrift: zwei Besucher, keine Gegenstaende
        assertEquals(1 + 2, VisitorShoppingHud.build().rowCount());
    }
}
