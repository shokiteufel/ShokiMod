package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.render.hud.SafariHud;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NPC Shop Values: von Haus aus aus, speichern und laden, und - das Wichtige - keine Funktion der 26.3-Umstellung */
class ModConfigShopValueTest {

    @Test
    void itIsOffFromTheStartWithTheChosenDefaults() {
        ModConfig.ShopValueCategory c = new ModConfig().inventory.shopValue;
        assertFalse(c.enabled);
        assertEquals(10, c.entries);
        assertEquals(ModConfig.ShopValueSource.INSTANT_SELL, c.source);
        assertTrue(c.subtractMaterials);
        assertEquals(ModConfig.ShopValuePosition.LEFT_OF_WINDOW, c.position);
    }

    @Test
    void theChoicesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.inventory.shopValue.enabled = true;
        c.inventory.shopValue.entries = 5;
        c.inventory.shopValue.source = ModConfig.ShopValueSource.SELL_OFFER;
        c.inventory.shopValue.subtractMaterials = false;
        c.inventory.shopValue.position = ModConfig.ShopValuePosition.HUD_EDITOR;
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertTrue(again.inventory.shopValue.enabled);
        assertEquals(5, again.inventory.shopValue.entries);
        assertEquals(ModConfig.ShopValueSource.SELL_OFFER, again.inventory.shopValue.source);
        assertFalse(again.inventory.shopValue.subtractMaterials);
        assertEquals(ModConfig.ShopValuePosition.HUD_EDITOR, again.inventory.shopValue.position);
    }

    @Test
    void aFileFromBeforeGetsTheDefaults() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"inventory\":{\"auctions\":{},\"bazaar\":{}}}", ModConfig.class);
        ModConfig.repairInventory(loaded);
        assertNotNull(loaded.inventory.shopValue);
        assertFalse(loaded.inventory.shopValue.enabled);
        assertEquals(10, loaded.inventory.shopValue.entries);
    }

    @Test
    void anExplicitNullBlockOrValueIsRepairedOnLoading() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"inventory\":{\"shopValue\":null}}", ModConfig.class);
        ModConfig.repairInventory(loaded);
        assertNotNull(loaded.inventory.shopValue);

        ModConfig second = ModConfig.GSON.fromJson("{\"inventory\":{\"shopValue\":{\"source\":null,\"position\":null}}}", ModConfig.class);
        ModConfig.repairInventory(second);
        assertEquals(ModConfig.ShopValueSource.INSTANT_SELL, second.inventory.shopValue.source);
        assertEquals(ModConfig.ShopValuePosition.LEFT_OF_WINDOW, second.inventory.shopValue.position);
    }

    @Test
    void theNumberOfEntriesStaysBetweenThreeAndTwenty() {
        ModConfig c = new ModConfig();
        c.inventory.shopValue.entries = 0;
        ModConfig.repairInventory(c);
        assertEquals(3, c.inventory.shopValue.entries);
        c.inventory.shopValue.entries = 99;
        ModConfig.repairInventory(c);
        assertEquals(20, c.inventory.shopValue.entries);
        c.inventory.shopValue.entries = 7;
        ModConfig.repairInventory(c);
        assertEquals(7, c.inventory.shopValue.entries);
    }

    // ------------------------------------------------------------------ keine 26.3-Funktion

    @Test
    void theCategoryIsNotInTheListOfThe263Menu() throws NoSuchFieldException {
        Field field = ModConfig.InventoryCategory.class.getField("shopValue");
        assertFalse(Update263Scope.categoryFields().contains(field), "NPC Shop Values gehoert nicht zu 26.3: es darf mit dem Schalter nicht verschwinden");
    }

    @Test
    void theSwitchOf263DoesNotTouchIt() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig c = new ModConfig();
            c.inventory.shopValue.enabled = true;
            c.update263.enabled = false;
            ModConfig.INSTANCE = c;
            assertTrue(FeatureGate.shopValue(), "mit dem 26.3-Schalter aus bleibt NPC Shop Values an");
            c.update263.enabled = true;
            assertTrue(FeatureGate.shopValue());
            c.inventory.shopValue.enabled = false;
            assertFalse(FeatureGate.shopValue());
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }

    @Test
    void theBoxIsNoBoxOfThe263Functions() {
        assertFalse(SafariHud.Panel.SHOP_VALUE.from263());
    }

    @Test
    void theBoxCarriesItsOwnPositionAndSize() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            SafariHud.Panel panel = SafariHud.Panel.SHOP_VALUE;
            panel.setPosition(0.3f, 0.4f);
            panel.setScale(1.5f);
            panel.setAlpha(0.5f);
            assertEquals(0.3f, ModConfig.INSTANCE.inventory.shopValue.hudX);
            assertEquals(0.4f, ModConfig.INSTANCE.inventory.shopValue.hudY);
            assertEquals(1.5f, panel.scale());
            assertEquals(0.5f, panel.alpha());
            assertEquals(0.3f, panel.x());
            assertEquals(0.4f, panel.y());
            assertEquals("NPC Shop Values", panel.settingsSearch());
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }
}
