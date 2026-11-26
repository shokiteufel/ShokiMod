package com.shokiteufel.shokimod.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inventory (2.4.13): von Haus aus aus, speichern und laden, eine Datei von vorher. */
class ModConfigInventoryTest {

    @Test
    void everythingIsOffFromTheStart() {
        ModConfig c = new ModConfig();
        assertFalse(c.inventory.auctions.autoCopyUnderbid);
        assertEquals(-1, c.inventory.auctions.copyUnderbidKey);
    }

    @Test
    void theBazaarHelpersAreOffFromTheStart() {
        ModConfig.BazaarHelpersCategory b = new ModConfig().inventory.bazaar;
        assertFalse(b.purchaseHelper);
        assertFalse(b.orderHelper);
        assertFalse(b.bestSellMethod);
        assertFalse(b.cancelledBuyOrderClipboard);
    }

    @Test
    void theBazaarChoicesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.inventory.bazaar.orderHelper = true;
        c.inventory.bazaar.hudScale = 1.5f;
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertTrue(again.inventory.bazaar.orderHelper);
        assertEquals(1.5f, again.inventory.bazaar.hudScale);
        assertFalse(again.inventory.bazaar.purchaseHelper);
    }

    @Test
    void aFileFromBeforeTheBazaarGetsIt() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"inventory\":{\"auctions\":{},\"bazaar\":null}}", ModConfig.class);
        ModConfig.repairInventory(loaded);
        assertNotNull(loaded.inventory.bazaar);
    }

    @Test
    void aKeyBelowThirtyTwoIsNoKey() {
        ModConfig c = new ModConfig();
        c.inventory.auctions.copyUnderbidKey = 5;
        assertFalse(c.inventory.auctions.hasCopyKey());
        c.inventory.auctions.copyUnderbidKey = 75;
        assertTrue(c.inventory.auctions.hasCopyKey());
    }

    @Test
    void theChoicesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.inventory.auctions.autoCopyUnderbid = true;
        c.inventory.auctions.copyUnderbidKey = 75;
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertTrue(again.inventory.auctions.autoCopyUnderbid);
        assertEquals(75, again.inventory.auctions.copyUnderbidKey);
    }

    @Test
    void aFileWithoutTheNewPartsGetsThemOnLoading() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"inventory\":null}", ModConfig.class);
        ModConfig.repairInventory(loaded);
        assertNotNull(loaded.inventory);
        assertNotNull(loaded.inventory.auctions);
        ModConfig second = ModConfig.GSON.fromJson("{\"inventory\":{\"auctions\":null}}", ModConfig.class);
        ModConfig.repairInventory(second);
        assertNotNull(second.inventory.auctions);
    }
}
