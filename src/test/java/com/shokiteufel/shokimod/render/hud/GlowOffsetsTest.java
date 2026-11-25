package com.shokiteufel.shokimod.render.hud;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Das Leuchten der HUD-Schrift zeichnet seinen Umriss seit 2.4.9 an vier statt acht Stellen. */
class GlowOffsetsTest {

    @Test
    void theOutlineIsDrawnFourTimes() {
        assertEquals(4, GlowOffsets.OFFSETS.length, "acht Durchgaenge kosteten bei einem grossen Panel die Haelfte der Spielzeit");
    }

    @Test
    void everyOffsetIsOnePixelAwayAboveBelowLeftOrRight() {
        Set<String> seen = new HashSet<>();
        for (int[] o : GlowOffsets.OFFSETS) {
            assertEquals(1, Math.abs(o[0]) + Math.abs(o[1]), "genau ein Bildpunkt, nur waagerecht oder senkrecht");
            assertFalse(!seen.add(o[0] + "," + o[1]), "kein Versatz doppelt");
        }
    }

    @Test
    void allFourDirectionsAreCovered() {
        int left = 0, right = 0, up = 0, down = 0;
        for (int[] o : GlowOffsets.OFFSETS) {
            if (o[0] < 0) left++;
            if (o[0] > 0) right++;
            if (o[1] < 0) up++;
            if (o[1] > 0) down++;
        }
        assertEquals(1, left);
        assertEquals(1, right);
        assertEquals(1, up);
        assertEquals(1, down);
    }
}
