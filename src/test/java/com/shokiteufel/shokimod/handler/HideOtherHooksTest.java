package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wo fremde Haken ausgeblendet werden: in SkyBlock, ausser in Gebieten ohne Angeln (Feeshs NO_FISHING_WORLDS). */
class HideOtherHooksTest {

    @Test
    void outsideSkyblockNothingIsHidden() {
        assertFalse(HideOtherHooks.fishingWorld(false, "Hub"));
        assertFalse(HideOtherHooks.fishingWorld(false, "Unknown"));
    }

    @Test
    void placesWhereFishingHappensCount() {
        for (String map : new String[] {"Hub", "Crimson Isle", "Backwater Bayou", "Crystal Hollows", "Dwarven Mines", "Jerry's Workshop",
                "Moonglade Marsh", "Torrhus Canyon", "Lotus Atoll", "Private Island", "The Park", "Spider's Den"}) {
            assertTrue(HideOtherHooks.fishingWorld(true, map), map);
        }
    }

    @Test
    void placesWhereNobodyFishesDoNotCountInEitherSpelling() {
        for (String map : new String[] {"The Rift", "Rift Dimension", "Garden", "Kuudra's Hollow", "Kuudra", "Dungeons", "Catacombs",
                "Dungeon Hub", "The End", "Glacite Mineshafts", "Glacite Mineshaft", "Safari"}) {
            assertFalse(HideOtherHooks.fishingWorld(true, map), map);
        }
    }

    @Test
    void caseAndSpacesDoNotMatter() {
        assertFalse(HideOtherHooks.fishingWorld(true, "  GARDEN "));
        assertFalse(HideOtherHooks.fishingWorld(true, "glacite mineshaft"));
        assertTrue(HideOtherHooks.fishingWorld(true, "hub"));
    }

    @Test
    void anAreaNotReadYetCountsAsAFishingPlaceSoTheSettingDoesNotLookBroken() {
        assertTrue(HideOtherHooks.fishingWorld(true, "Unknown"));
        assertTrue(HideOtherHooks.fishingWorld(true, null));
        assertTrue(HideOtherHooks.fishingWorld(true, ""));
    }
}
