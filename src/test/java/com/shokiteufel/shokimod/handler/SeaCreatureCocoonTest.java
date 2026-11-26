package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.SeaCreatures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wunsch des Users (26.11.): Fishing > Cocoon settings wie bei SkyHanni - ein Titel "<Name> Has Been Cocooned" und eine Zeile "My <Name> has been
 * cocooned!" in den Gruppenchat, je Meeresbewohner einzeln. Ohne eigene Wahl melden die seltenen.
 */
class SeaCreatureCocoonTest {

    @BeforeEach
    void frisch() {
        ModConfig.INSTANCE = new ModConfig();
    }

    @Test
    void theChatLineYieldsTheCreatureName() {
        assertEquals("Yeti", SeaCreatureCocoon.nameIn("CAUGHT! You cocooned a Yeti!"));
        assertEquals("Water Hydra", SeaCreatureCocoon.nameIn("CAUGHT! You cocooned a Water Hydra!"));
        assertEquals("Emperor", SeaCreatureCocoon.nameIn("CAUGHT! You cocooned an Emperor!"));
    }

    @Test
    void otherLinesAreNotACocoon() {
        assertNull(SeaCreatureCocoon.nameIn("CAUGHT! You caught a Yeti!"));
        assertNull(SeaCreatureCocoon.nameIn("You cocooned a Yeti!"));
        assertNull(SeaCreatureCocoon.nameIn("[MVP+] Someone: CAUGHT! You cocooned a Yeti!"));
        assertNull(SeaCreatureCocoon.nameIn(null));
    }

    @Test
    void theTitleAndTheShareLineAreSkyHannisWording() {
        assertEquals("Yeti Has Been Cocooned", SeaCreatureCocoon.headline("Yeti"));
        assertEquals("My Yeti has been cocooned!", SeaCreatureCocoon.shareText("Yeti"));
    }

    @Test
    void withoutAChoiceOnlyTheRareOnesAreReported() {
        SeaCreatures.Creature rare = firstWhere(true);
        SeaCreatures.Creature common = firstWhere(false);
        assertTrue(SeaCreatureCocoon.wantsWarn(rare));
        assertTrue(SeaCreatureCocoon.wantsShare(rare));
        assertFalse(SeaCreatureCocoon.wantsWarn(common));
        assertFalse(SeaCreatureCocoon.wantsShare(common));
    }

    @Test
    void aChoiceOfYourOwnWins() {
        SeaCreatures.Creature common = firstWhere(false);
        SeaCreatures.Creature rare = firstWhere(true);
        ModConfig.INSTANCE.fishing.cocoon.warnPicks.put(common.name(), true);
        ModConfig.INSTANCE.fishing.cocoon.sharePicks.put(rare.name(), false);

        assertTrue(SeaCreatureCocoon.wantsWarn(common));
        assertFalse(SeaCreatureCocoon.wantsShare(rare));
        assertTrue(SeaCreatureCocoon.wantsWarn(rare), "die andere Spalte bleibt bei der Voreinstellung");
    }

    @Test
    void anUnknownCreatureNeverReports() {
        assertFalse(SeaCreatureCocoon.wantsWarn(null));
        assertFalse(SeaCreatureCocoon.wantsShare(null));
        assertNull(SeaCreatures.byName("Definitely Not A Sea Creature"));
    }

    @Test
    void everythingIsOffFromTheStart() {
        ModConfig.SeaCocoonCategory c = new ModConfig().fishing.cocoon;
        assertFalse(c.warn);
        assertFalse(c.share);
        assertNotNull(c.warnPicks);
        assertNotNull(c.sharePicks);
    }

    private static SeaCreatures.Creature firstWhere(boolean rare) {
        for (SeaCreatures.Creature c : SeaCreatures.all()) {
            if (c.rare() == rare) return c;
        }
        throw new AssertionError("no creature with rare=" + rare);
    }
}
