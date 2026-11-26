package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Misc > Shorten /warp und Replace /warp is (2.4.16): was aus einem getippten Befehl wird. */
class WarpCommandsTest {

    @Test
    void aWarpNameAloneBecomesAWarpCommand() {
        assertEquals("warp wizard", WarpCommands.replacement("wizard", true, false, false));
        assertEquals("warp hub", WarpCommands.replacement("hub", true, false, false));
        assertEquals("warp dungeon_hub", WarpCommands.replacement("dungeon_hub", true, false, false));
        assertEquals("warp nucleus", WarpCommands.replacement("nucleus", true, true, false));
    }

    @Test
    void capitalsAndTrailingSpacesDoNotMatter() {
        assertEquals("warp wizard", WarpCommands.replacement("Wizard", true, false, false));
        assertEquals("warp wizard", WarpCommands.replacement("wizard  ", true, false, false));
    }

    @Test
    void nothingChangesWhenTheSwitchIsOff() {
        assertNull(WarpCommands.replacement("wizard", false, false, false));
        assertNull(WarpCommands.replacement("wizard", false, true, false), "Replace /warp is gilt nur fuer /warp is");
    }

    @Test
    void anythingBehindTheNameKeepsTheCommandAsItIs() {
        assertNull(WarpCommands.replacement("wizard now", true, false, false));
        assertNull(WarpCommands.replacement("warp wizard", true, false, false), "der richtige Befehl bleibt");
        assertNull(WarpCommands.replacement("shoki", true, false, false), "kein Warp");
        assertNull(WarpCommands.replacement("", true, true, false));
        assertNull(WarpCommands.replacement(null, true, true, false));
    }

    @Test
    void jerryStaysJerryOnThePrivateIsland() {
        assertNull(WarpCommands.replacement("jerry", true, false, true));
        assertEquals("warp jerry", WarpCommands.replacement("jerry", true, false, false));
        assertEquals("warp wizard", WarpCommands.replacement("wizard", true, false, true), "nur jerry ist ausgenommen");
    }

    @Test
    void warpIsBecomesIs() {
        assertEquals("is", WarpCommands.replacement("warp is", false, true, false));
        assertEquals("is", WarpCommands.replacement("Warp IS", true, true, false));
        assertNull(WarpCommands.replacement("warp is", true, false, false), "ohne den zweiten Schalter bleibt es");
        assertNull(WarpCommands.replacement("warp island", false, true, false));
    }

    @Test
    void theListHasAllNinetySixWarps() {
        assertEquals(96, WarpCommands.WARPS.size());
        for (String name : new String[] {"arachne", "hub", "da", "nucleus", "workshop", "wizard", "dhub", "kuudra", "museum"}) {
            assertTrue(WarpCommands.WARPS.contains(name), name);
        }
        assertFalse(WarpCommands.WARPS.contains("is"), "is ist ein eigener Befehl");
        for (String name : WarpCommands.WARPS) {
            assertEquals(name, name.toLowerCase(), "die Liste steht klein, so wird verglichen");
        }
    }
}
