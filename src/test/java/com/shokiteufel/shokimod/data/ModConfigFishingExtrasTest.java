package com.shokiteufel.shokimod.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fishing > Hook display, Bait display und Cocoon settings (2.4.10): von Haus aus aus, und eine Datei von vorher oder von Hand beschaedigt
 * wird beim Laden zurechtgerueckt.
 */
class ModConfigFishingExtrasTest {

    @Test
    void theThreeNewFunctionsAreOffFromTheStart() {
        ModConfig c = new ModConfig();
        ModConfig.INSTANCE = c;
        assertFalse(c.fishing.hookDisplay.enabled);
        assertFalse(c.fishing.baitDisplay.enabled);
        assertFalse(c.fishing.cocoon.warn);
        assertFalse(c.fishing.cocoon.share);
        assertFalse(FeatureGate.hookDisplay());
        assertFalse(FeatureGate.baitDisplay());
        assertFalse(FeatureGate.seaCocoon());
    }

    @Test
    void theDefaultsAreSkyHannis() {
        ModConfig.HookDisplayCategory h = new ModConfig().fishing.hookDisplay;
        assertEquals("&c&l!!!", h.customAlertText);
        assertEquals(0f, h.customReelInTime);
        assertTrue(h.hideArmorStand);
        assertEquals(0f, h.hudAlpha, "nur die Schrift, kein Untergrund");
        assertTrue(new ModConfig().fishing.baitDisplay.showIcon);
    }

    @Test
    void aFileWithoutTheNewPartsGetsThemOnLoading() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"fishing\":{\"hookDisplay\":null,\"baitDisplay\":null,\"cocoon\":null}}", ModConfig.class);
        ModConfig.repairFishingExtras(loaded.fishing);
        assertNotNull(loaded.fishing.hookDisplay);
        assertNotNull(loaded.fishing.baitDisplay);
        assertNotNull(loaded.fishing.cocoon);
        assertFalse(loaded.fishing.hookDisplay.enabled);
    }

    @Test
    void damagedValuesAreStraightened() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"fishing\":{\"hookDisplay\":{\"customAlertText\":null,\"customReelInTime\":7.5},"
                + "\"cocoon\":{\"bannerDesign\":null,\"soundFile\":null,\"warnPicks\":null,\"sharePicks\":{\"Yeti\":null,\"Thunder\":true}}}}", ModConfig.class);
        ModConfig.repairFishingExtras(loaded.fishing);
        assertEquals("&c&l!!!", loaded.fishing.hookDisplay.customAlertText);
        assertEquals(1f, loaded.fishing.hookDisplay.customReelInTime, "die Obergrenze des Reglers");
        assertEquals(BannerDesign.ALERT_BAND, loaded.fishing.cocoon.bannerDesign);
        assertEquals("", loaded.fishing.cocoon.soundFile);
        assertNotNull(loaded.fishing.cocoon.warnPicks);
        assertFalse(loaded.fishing.cocoon.sharePicks.containsKey("Yeti"), "ein Eintrag ohne Wert faellt weg");
        assertEquals(Boolean.TRUE, loaded.fishing.cocoon.sharePicks.get("Thunder"));
    }

    @Test
    void aNegativeReelInTimeIsRaisedToZero() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"fishing\":{\"hookDisplay\":{\"customReelInTime\":-2}}}", ModConfig.class);
        ModConfig.repairFishingExtras(loaded.fishing);
        assertEquals(0f, loaded.fishing.hookDisplay.customReelInTime);
    }

    @Test
    void theChoicesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.fishing.hookDisplay.enabled = true;
        c.fishing.hookDisplay.customAlertText = "&aGO";
        c.fishing.cocoon.warnPicks.put("Yeti", true);
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertTrue(again.fishing.hookDisplay.enabled);
        assertEquals("&aGO", again.fishing.hookDisplay.customAlertText);
        assertEquals(Boolean.TRUE, again.fishing.cocoon.warnPicks.get("Yeti"));
    }
}
