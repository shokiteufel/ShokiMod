package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Misc > Fix item double clicks (2.4.16): bei welchen Gegenstaenden das Block-Paket entfaellt. */
class FixDoubleClicksTest {

    @Test
    void aFishingRodIsAffectedWhateverItsId() {
        assertTrue(FixDoubleClicks.affected(true, null));
        assertTrue(FixDoubleClicks.affected(true, "ROD_OF_THE_SEA"));
    }

    @Test
    void allSixBlazeDaggersAreAffected() {
        assertEquals(6, FixDoubleClicks.BLAZE_DAGGERS.size());
        for (String id : new String[] {"BURSTFIRE_DAGGER", "BURSTMAW_DAGGER", "FIREDUST_DAGGER", "HEARTFIRE_DAGGER", "HEARTMAW_DAGGER", "MAWDUST_DAGGER"}) {
            assertTrue(FixDoubleClicks.affected(false, id), id);
        }
    }

    @Test
    void everythingElseKeepsItsBlockClick() {
        assertFalse(FixDoubleClicks.affected(false, null));
        assertFalse(FixDoubleClicks.affected(false, "ASPECT_OF_THE_END"));
        assertFalse(FixDoubleClicks.affected(false, "BURSTFIRE_DAGGER_2"), "ganze Kennung, kein Anfang");
        assertFalse(FixDoubleClicks.affected(false, "burstfire_dagger"), "die Kennungen stehen gross");
    }
}
