package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** NPC Shop Values, der Teil mit dem Fenster: ohne Fenster nichts zu sehen, und die Farbe eines Namens als Farbcode */
class ShopValuesTest {

    @Test
    void withoutAnOpenShopNothingIsActive() {
        assertFalse(ShopValues.active());
        assertEquals(0, ShopValues.sections().size());
    }

    @Test
    void theRarityColoursBecomeTheirColourCodes() {
        assertEquals("§f", ShopValues.legacyColour(0xFFFFFFFF));
        assertEquals("§a", ShopValues.legacyColour(0xFF55FF55));
        assertEquals("§9", ShopValues.legacyColour(0xFF5555FF));
        assertEquals("§5", ShopValues.legacyColour(0xFFAA00AA));
        assertEquals("§6", ShopValues.legacyColour(0xFFFFAA00));
        assertEquals("§d", ShopValues.legacyColour(0xFFFF55FF));
        assertEquals("§b", ShopValues.legacyColour(0xFF55FFFF));
        assertEquals("§c", ShopValues.legacyColour(0xFFFF5555));
    }

    @Test
    void aColourWithoutACodeTakesTheNearestOne() {
        // fast Gold
        assertEquals("§6", ShopValues.legacyColour(0xFFFFA500));
        // fast Gruen
        assertEquals("§a", ShopValues.legacyColour(0xFF50F050));
    }

    @Test
    void noColourIsWhite() {
        assertEquals("§f", ShopValues.legacyColour(0));
    }
}
