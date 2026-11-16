package com.shokiteufel.shokimod.scanner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ein Leichen-Schluessel ist nur als Beute einer Leiche ein Fund. Der Skeleton Key stand bis 2.3.7 nicht im Filter: Ein Zugang im Schacht
 * zaehlte als Gewinn und hob den Abzug an der Vanguard-Leiche auf (User 16.11.: "bei Skeleton sieht es aus, als wuerde nicht abgezogen").
 */
class SchluesselKeinFundTest {

    @Test
    void allThreeCorpseKeysAreNoFindOutsideACorpse() {
        for (String key : new String[] {"UMBER_KEY", "TUNGSTEN_KEY", "SKELETON_KEY"}) {
            assertTrue(ItemChanges.schluesselKeinFund(key, "sacks"), key + " aus dem Sack ist kein Fund");
            assertTrue(ItemChanges.schluesselKeinFund(key, "server (source unknown)"), key + " aus dem Inventar ist kein Fund");
        }
    }

    @Test
    void aKeyFromACorpsesOwnLootStillCounts() {
        for (String key : new String[] {"UMBER_KEY", "TUNGSTEN_KEY", "SKELETON_KEY"}) {
            assertFalse(ItemChanges.schluesselKeinFund(key, "corpse loot"), key + " aus der Beute einer Leiche ist ein Fund");
        }
    }

    @Test
    void otherWaresAreNotAffected() {
        assertFalse(ItemChanges.schluesselKeinFund("FINE_JADE_GEM", "sacks"));
        assertFalse(ItemChanges.schluesselKeinFund("JUNGLE_KEY", "sacks"));
        assertFalse(ItemChanges.schluesselKeinFund(null, "sacks"));
    }
}
