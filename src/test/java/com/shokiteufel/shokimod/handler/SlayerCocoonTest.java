package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wunsch des Users (26.11.): Slayer Cocoon Title und Notification Sound wie bei SkyHanni - Hypixel schreibt "YOU COCOONED YOUR SLAYER BOSS",
 * dann kommt der Titel "Slayer Boss Cocooned!" und zehn Toene.
 */
class SlayerCocoonTest {

    @Test
    void theCocoonLineIsRecognisedWithItsIndent() {
        assertTrue(SlayerCocoon.isCocoonLine("  YOU COCOONED YOUR SLAYER BOSS"));
        assertTrue(SlayerCocoon.isCocoonLine("YOU COCOONED YOUR SLAYER BOSS"));
        assertTrue(SlayerCocoon.isCocoonLine("                    YOU COCOONED YOUR SLAYER BOSS   "));
    }

    @Test
    void otherLinesAreNot() {
        assertFalse(SlayerCocoon.isCocoonLine("NICE! SLAYER BOSS SLAIN!"));
        assertFalse(SlayerCocoon.isCocoonLine("CAUGHT! You cocooned a Yeti!"));
        assertFalse(SlayerCocoon.isCocoonLine("Someone: YOU COCOONED YOUR SLAYER BOSS"));
        assertFalse(SlayerCocoon.isCocoonLine(""));
        assertFalse(SlayerCocoon.isCocoonLine(null));
    }

    @Test
    void tenPingsOneHundredMillisecondsApart() {
        assertEquals(10, SlayerCocoon.PINGS);
        assertEquals(2, SlayerCocoon.TICKS_BETWEEN_PINGS, "zwei Ticks sind hundert Millisekunden");
    }

    @Test
    void everythingIsOffFromTheStart() {
        ModConfig.SlayerCategory c = new ModConfig().slayer;
        assertFalse(c.cocoonTitle);
        assertFalse(c.cocoonDing);
        assertFalse(c.anySpawnParticle());
    }
}
