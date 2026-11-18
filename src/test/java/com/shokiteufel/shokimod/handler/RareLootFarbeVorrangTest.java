package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.RareLootHandler.DyeRoute;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wunsch des Users (18.11.): Faellt eine Farbe ueber der Schwelle (zum Beispiel ueber 100M), reagierten der Farb-Post und der Rare-Loot-Post
 * mit dem Wert. Die Farbe soll immer Vorrang haben und nur einmal gepostet werden.
 */
class RareLootFarbeVorrangTest {

    private static ModConfig.RareLootCategory cfg(boolean drops, boolean alert, boolean share) {
        ModConfig.RareLootCategory c = new ModConfig().chat.rareLoot;
        c.dyeDrops = drops;
        c.dyeAlert = alert;
        c.dyeShare = share;
        return c;
    }

    private static void assertRoute(DyeRoute route, boolean dyeAlert, boolean tierAlert, boolean dyeShare, boolean ordinaryShare) {
        assertEquals(new DyeRoute(dyeAlert, tierAlert, dyeShare, ordinaryShare), route);
    }

    @Test
    void chatLineWithBothDyeSwitchesOnIsPostedOnlyAsDye() {
        assertRoute(RareLootHandler.dyeRoute(cfg(true, true, true), false), true, false, true, false);
    }

    @Test
    void dyeSharingNoLongerNeedsTheDyeAlert() {
        // Vorher stand das Farb-Teilen im Zweig des Farb-Alarms: ohne den teilte die Farbe gar nicht als Farbe
        assertRoute(RareLootHandler.dyeRoute(cfg(true, false, true), false), false, true, true, false);
    }

    @Test
    void aSwitchThatIsOffFallsBackToTheOrdinaryRule() {
        assertRoute(RareLootHandler.dyeRoute(cfg(true, true, false), false), true, false, false, true);
        assertRoute(RareLootHandler.dyeRoute(cfg(true, false, false), false), false, true, false, true);
    }

    @Test
    void theInventoryLeavesTheDyeToItsChatLineWhenBothSwitchesAreOn() {
        assertRoute(RareLootHandler.dyeRoute(cfg(true, true, true), true), false, false, false, false);
    }

    @Test
    void theInventoryKeepsTheOrdinaryRuleForWhatTheDyeDoesNotOwn() {
        assertRoute(RareLootHandler.dyeRoute(cfg(true, true, false), true), false, false, false, true);
        assertRoute(RareLootHandler.dyeRoute(cfg(true, false, true), true), false, true, false, false);
    }

    @Test
    void withoutReadingDyeLinesTheInventoryWorksAsBefore() {
        assertRoute(RareLootHandler.dyeRoute(cfg(false, true, true), true), false, true, false, true);
    }

    @Test
    void aDyeIsNeverPostedTwiceInAnyCombination() {
        boolean[] both = {false, true};
        for (boolean drops : both) {
            for (boolean alert : both) {
                for (boolean share : both) {
                    String was = "drops=" + drops + " alert=" + alert + " share=" + share;
                    ModConfig.RareLootCategory c = cfg(drops, alert, share);
                    DyeRoute chat = RareLootHandler.dyeRoute(c, false);
                    DyeRoute inventory = RareLootHandler.dyeRoute(c, true);

                    // Auf einem Weg: Farbe oder Stufe, Farb-Post oder gewoehnlicher Post - nie beides
                    assertFalse(chat.dyeAlert() && chat.tierAlert(), "Alarm doppelt im Chat: " + was);
                    assertFalse(chat.dyeShare() && chat.ordinaryShare(), "Post doppelt im Chat: " + was);

                    // Ueber beide Wege: was die Farbe uebernimmt, darf der Inventarweg nicht noch einmal tun (die Farbzeile gibt es nur,
                    // wenn die Farbzeilen gelesen werden)
                    if (drops && alert) assertFalse(inventory.tierAlert(), "Stufen-Alarm zusaetzlich zum Farb-Alarm: " + was);
                    if (drops && share) assertFalse(inventory.ordinaryShare(), "gewoehnlicher Post zusaetzlich zum Farb-Post: " + was);
                    assertFalse(inventory.dyeAlert() || inventory.dyeShare(), "der Inventarweg meldet nie als Farbe: " + was);
                }
            }
        }
    }

    @Test
    void recognisesDyesByTheirItemId() {
        assertTrue(RareLootHandler.isDyeItem(List.of("DYE_SANGRIA", "SANGRIA_DYE")));
        assertTrue(RareLootHandler.isDyeItem(List.of("DYE_NECRON")));
        assertFalse(RareLootHandler.isDyeItem(List.of("COMPACTED_DYE_SANGRIA")), "gecraftete Form");
        assertFalse(RareLootHandler.isDyeItem(List.of("DYE_SANGRIA_BLOCK")), "gecraftete Form");
        assertFalse(RareLootHandler.isDyeItem(List.of("HYPERION", "SHARD_WITHER_SPECTER")));
        assertFalse(RareLootHandler.isDyeItem(List.of()));
        assertFalse(RareLootHandler.isDyeItem(null));
        assertFalse(RareLootHandler.isDyeItem(Arrays.asList(null, "BONE")));
    }
}
