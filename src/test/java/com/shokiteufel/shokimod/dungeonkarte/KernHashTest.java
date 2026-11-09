package com.shokiteufel.shokimod.dungeonkarte;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Der Kern-Hash einer Blocksaeule. Die erwarteten Zahlen sind unabhaengig (Python) nach NoammAddons' Formel gerechnet. */
class KernHashTest {

    @Test
    void anEmptyColumnHasTheHashOfAllAir() {
        assertEquals(-181773796, KernHash.hashVonNamen(y -> "minecraft:air"));
    }

    @Test
    void oneStoneBlockChangesTheHash() {
        assertEquals(759611575, KernHash.hashVonNamen(y -> y == 70 ? "minecraft:stone" : "minecraft:air"));
    }

    @Test
    void planksChestsPistonsAndLiquidsCountAsAir() {
        int luft = KernHash.hashVonNamen(y -> "minecraft:air");
        for (String name : new String[] {"minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:chest", "minecraft:trapped_chest",
                "minecraft:piston_head", "minecraft:moving_piston", "minecraft:water", "minecraft:lava", "minecraft:fire",
                "minecraft:soul_fire"}) {
            assertEquals(luft, KernHash.hashVonNamen(y -> y == 70 ? name : "minecraft:air"), name);
        }
    }

    @Test
    void theHeightMattersNotOnlyTheBlocks() {
        assertNotEquals(KernHash.hashVonNamen(y -> y == 70 ? "minecraft:stone" : "minecraft:air"),
                KernHash.hashVonNamen(y -> y == 71 ? "minecraft:stone" : "minecraft:air"));
    }

    @Test
    void theColumnRunsFrom140DownTo12() {
        int[] aufrufe = {0};
        int[] erste = {-1};
        int[] letzte = {-1};
        KernHash.hashVonTokens(y -> {
            if (aufrufe[0]++ == 0) erste[0] = y;
            letzte[0] = y;
            return 0;
        });
        assertEquals(129, aufrufe[0]);
        assertEquals(140, erste[0]);
        assertEquals(12, letzte[0]);
    }
}
