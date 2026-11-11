package com.shokiteufel.shokimod.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Safari-Spezialitems: ohne Preis nie im Tracker, mit Preis (auch einem selbst eingetragenen) wie jede Ware */
class SafariSpecialsTest {

    @Test
    void theEightItemsFromTheTrackerBoxAreSpecialsOnAnyIsland() {
        for (String name : new String[] {"Critter Capsule", "Masterful Critter Capsule", "Icebreaker", "Lime Gem", "Orange Gem",
                "Purple Gem", "Shining Coin", "Soothing Incense"}) {
            assertTrue(SafariSpecials.keinFund("X", name, false, false, true), name);
            assertTrue(SafariSpecials.keinFund("X", name, false, false, false), name + " - die Namensliste braucht keine Preislisten");
        }
    }

    @Test
    void theNameIsMatchedWithoutRegardToCaseAndSpaces() {
        assertTrue(SafariSpecials.keinFund("X", " shining COIN ", false, false, true));
    }

    @Test
    void anItemWithAPriceIsAlwaysAFind() {
        // Auch ein Name aus der Liste: wer selbst einen Preis eintraegt, will die Ware im Tracker haben
        assertFalse(SafariSpecials.keinFund("X", "Critter Capsule", true, true, true));
        assertFalse(SafariSpecials.keinFund("ESSENCE_SAFARI", "Safari Essence", true, true, true));
    }

    @Test
    void onTheSafariIslandEveryItemWithoutAPriceIsASpecial() {
        // Auch eine Ware, die noch nicht in der Liste steht
        assertTrue(SafariSpecials.keinFund("NEW_SAFARI_THING", "New Safari Thing", true, false, true));
    }

    @Test
    void elsewhereAnUnknownItemWithoutAPriceStaysSoItCanGetItsOwnPrice() {
        assertFalse(SafariSpecials.keinFund("NEW_THING", "New Thing", false, false, true));
    }

    @Test
    void beforeThePriceListsAreLoadedNothingOnTheSafariIsHidden() {
        // Ohne Preislisten haette jede Ware "keinen Preis" - auch die Essence
        assertFalse(SafariSpecials.keinFund("ESSENCE_SAFARI", "Safari Essence", true, false, false));
    }

    @Test
    void aPetIsNeverASpecial() {
        assertFalse(SafariSpecials.keinFund("MOSQUITO;4", "Mosquito", true, false, true));
    }

    @Test
    void withoutAnIdThereIsNothingToHide() {
        assertFalse(SafariSpecials.keinFund(null, "Critter Capsule", true, false, true));
    }
}
