package com.shokiteufel.shokimod.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inventory > Estimated Item Value (2.4.18): von Haus aus aus, speichern und laden, kaputte Werte. */
class ModConfigEstimatedItemValueTest {

    @Test
    void everythingIsOffFromTheStart() {
        ModConfig.EstimatedItemValueCategory c = new ModConfig().inventory.itemValue;
        assertFalse(c.enabled);
        assertFalse(c.showTooltip);
        assertFalse(c.armor);
        assertFalse(c.equipment);
        assertFalse(c.hasHotkey());
        assertEquals(-1, c.hotkey);
    }

    @Test
    void theOtherDefaultsFollowSkyHanni() {
        ModConfig.EstimatedItemValueCategory c = new ModConfig().inventory.itemValue;
        assertTrue(c.alwaysEnabled);
        assertEquals(500, c.hoverDelay);
        assertEquals(7, c.enchantmentsCap);
        assertEquals(3, c.starMaterialCap);
        assertFalse(c.exactPrice);
        assertFalse(c.ignoreHelmetSkins);
        assertFalse(c.ignoreArmorDyes);
        assertFalse(c.ignoreRunes);
        assertEquals(ModConfig.EivPriceSource.INSTANT_SELL, c.priceSource);
    }

    @Test
    void anyOfTheThreeFeaturesTurnsTheReaderOn() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            for (int which = 0; which < 4; which++) {
                ModConfig c = new ModConfig();
                switch (which) {
                    case 0 -> c.inventory.itemValue.enabled = true;
                    case 1 -> c.inventory.itemValue.showTooltip = true;
                    case 2 -> c.inventory.itemValue.armor = true;
                    default -> c.inventory.itemValue.equipment = true;
                }
                ModConfig.INSTANCE = c;
                assertTrue(FeatureGate.estimatedItemValue(), "Schalter " + which);
                assertTrue(FeatureGate.location(), "Schalter " + which + " braucht den Ort");
            }
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }

    @Test
    void theChoicesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.inventory.itemValue.enabled = true;
        c.inventory.itemValue.hotkey = 75;
        c.inventory.itemValue.priceSource = ModConfig.EivPriceSource.NPC_SELL;
        c.inventory.itemValue.enchantmentsCap = 12;
        c.inventory.itemValue.hudScale = 1.5f;
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertTrue(again.inventory.itemValue.enabled);
        assertTrue(again.inventory.itemValue.hasHotkey());
        assertEquals(75, again.inventory.itemValue.hotkey);
        assertEquals(ModConfig.EivPriceSource.NPC_SELL, again.inventory.itemValue.priceSource);
        assertEquals(12, again.inventory.itemValue.enchantmentsCap);
        assertEquals(1.5f, again.inventory.itemValue.hudScale);
    }

    @Test
    void aFileFromBeforeGetsTheNewPart() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"inventory\":{\"itemValue\":null}}", ModConfig.class);
        ModConfig.repairInventory(loaded);
        assertNotNull(loaded.inventory.itemValue);
    }

    @Test
    void brokenValuesAreRepaired() {
        ModConfig loaded = ModConfig.GSON.fromJson(
                "{\"inventory\":{\"itemValue\":{\"priceSource\":\"NOPE\",\"hoverDelay\":99999,\"enchantmentsCap\":0,\"starMaterialCap\":100}}}", ModConfig.class);
        ModConfig.repairInventory(loaded);
        ModConfig.EstimatedItemValueCategory c = loaded.inventory.itemValue;
        assertEquals(ModConfig.EivPriceSource.INSTANT_SELL, c.priceSource);
        assertEquals(2000, c.hoverDelay);
        assertEquals(1, c.enchantmentsCap);
        assertEquals(15, c.starMaterialCap);
    }
}
