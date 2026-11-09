package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.scanner.CraftZeile.Gecraftet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CraftZeileTest {

    @Test
    void theRealSupercraftLineIsRead() {
        // Mitschnitt Schiggy 09.11., 14:00:06
        assertEquals(new Gecraftet("Whale Bait", 1298), CraftZeile.lesen("You Supercrafted Whale Bait x1,298!"));
    }

    @Test
    void aSingleItemHasNoAmount() {
        assertEquals(new Gecraftet("Enchanted Hay Bale", 1), CraftZeile.lesen("You Supercrafted Enchanted Hay Bale!"));
    }

    @Test
    void aPlainCraftLineIsReadToo() {
        assertEquals(new Gecraftet("Whale Bait", 64), CraftZeile.lesen("You crafted Whale Bait x64!"));
    }

    @Test
    void otherLinesAreNoCraftLines() {
        assertNull(CraftZeile.lesen("[Sacks] +1,298 items, -10,384 items. (Last 5s.)"));
        assertNull(CraftZeile.lesen("You bought Whale Bait!"));
        assertNull(CraftZeile.lesen("You Supercrafted"));
        assertNull(CraftZeile.lesen(""));
        assertNull(CraftZeile.lesen(null));
    }
}
