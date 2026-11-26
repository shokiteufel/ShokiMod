package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Wunsch des Users (26.11.): der Koeder-Kasten wie bei SkyHanni ("Fishing Bait Display"): Der Koeder liegt im letzten Platz der Leiste, und in
 * seiner Beschreibung steht "Bait Remaining: 49".
 */
class FishingBaitDisplayTest {

    @Test
    void theAmountIsReadFromTheBaitRemainingLine() {
        assertEquals(49, FishingBaitDisplay.amountOf(List.of("Fishing Bait", "", "Bait Remaining: 49", "COMMON BAIT")));
    }

    @Test
    void thousandsSeparatorsAreIgnored() {
        assertEquals(1234, FishingBaitDisplay.amountOf(List.of("Bait Remaining: 1,234")));
        assertEquals(1_234_567, FishingBaitDisplay.amountOf(List.of("Bait Remaining: 1,234,567")));
    }

    @Test
    void anItemWithoutTheLineIsNoBait() {
        assertEquals(-1, FishingBaitDisplay.amountOf(List.of("Some Sword", "Damage: +100")));
        assertEquals(-1, FishingBaitDisplay.amountOf(List.of()));
        assertEquals(-1, FishingBaitDisplay.amountOf(null));
    }

    @Test
    void aLineWithOtherTextAroundItDoesNotCount() {
        assertEquals(-1, FishingBaitDisplay.amountOf(List.of("Your Bait Remaining: 49 are great")));
        assertEquals(-1, FishingBaitDisplay.amountOf(List.of("Bait Remaining: lots")));
    }

    @Test
    void theHotbarSlotIsTheLastOne() {
        assertEquals(8, FishingBaitDisplay.BAIT_SLOT);
    }
}
