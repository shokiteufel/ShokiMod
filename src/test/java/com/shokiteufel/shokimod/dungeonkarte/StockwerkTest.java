package com.shokiteufel.shokimod.dungeonkarte;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StockwerkTest {

    @Test
    void theFloorStandsInTheBracketOfTheSidebarBox() {
        assertEquals(5, Stockwerk.aus(" The Catacombs (F5)"));
        assertEquals(1, Stockwerk.aus("The Catacombs (F1)"));
        assertEquals(7, Stockwerk.aus("The Catacombs (M7)"));
        assertEquals(0, Stockwerk.aus("The Catacombs (E)"));
    }

    @Test
    void withoutABracketThereIsNoFloor() {
        assertNull(Stockwerk.aus("The Catacombs"));
        assertNull(Stockwerk.aus("Purse: 1,000"));
        assertNull(Stockwerk.aus("(X5)"));
        assertNull(Stockwerk.aus(""));
        assertNull(Stockwerk.aus(null));
    }
}
