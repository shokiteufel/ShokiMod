package com.shokiteufel.shokimod.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.notenoughupdates.moulconfig.annotations.Category;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.4.15: der Hunting Tracker und die Kategorie Collections (mit dem Collection Tracker) sind weg, "Profit" heisst "Profit Tracker", und der
 * Bazaar-Handel (Steuer, Handel je Tag) wohnt unter Inventory statt in einem eigenen Reiter oben.
 */
class ModConfigAufraeumenTest {

    private static String kategorieName(Class<?> in, String feld) throws Exception {
        Field f = in.getField(feld);
        Category c = f.getAnnotation(Category.class);
        return c == null ? null : c.name();
    }

    @Test
    void noTabIsCalledCollectionsAnyMore() {
        for (Field f : ModConfig.class.getFields()) {
            Category c = f.getAnnotation(Category.class);
            if (c != null) assertNotEquals("Collections", c.name());
        }
    }

    @Test
    void huntingKeepsOnlyTheFusions() throws Exception {
        for (Field f : ModConfig.HuntingCategory.class.getFields()) {
            Category c = f.getAnnotation(Category.class);
            if (c != null) assertNotEquals("Hunting Tracker", c.name());
        }
        assertEquals("Fusions", kategorieName(ModConfig.HuntingCategory.class, "fusion"));
    }

    @Test
    void profitIsCalledProfitTracker() throws Exception {
        assertEquals("Profit Tracker", kategorieName(ModConfig.class, "profit"));
    }

    @Test
    void theBazaarFlipsAreASubTabOfInventoryAndNoTabOnTop() throws Exception {
        assertNull(kategorieName(ModConfig.class, "bazaar"), "oben gibt es keinen Reiter Bazaar mehr");
        assertEquals("Bazaar flips", kategorieName(ModConfig.InventoryCategory.class, "flips"));
        assertEquals("Bazaar", kategorieName(ModConfig.InventoryCategory.class, "bazaar"), "die Hilfen im Basar heissen weiter Bazaar");
    }

    @Test
    void aFreshConfigCarriesTheFlipsInInventory() {
        ModConfig c = new ModConfig();
        assertNull(c.bazaar, "das alte Feld ist nur zum Uebernehmen da");
        assertEquals(ModConfig.SellTax.PLAIN, c.inventory.flips.tax);
        assertEquals(1000f, c.inventory.flips.minPerDay);
    }

    @Test
    void theOldBazaarSettingsMoveIntoInventoryOnce() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"bazaar\":{\"tax\":\"COMMUNITY\",\"minPerDay\":2500.0}}", ModConfig.class);
        assertNotNull(loaded.bazaar);
        assertTrue(ModConfig.migrateBazaarFlips(loaded));
        assertEquals(ModConfig.SellTax.COMMUNITY, loaded.inventory.flips.tax);
        assertEquals(2500f, loaded.inventory.flips.minPerDay);
        assertNull(loaded.bazaar);
        assertFalse(ModConfig.migrateBazaarFlips(loaded), "ein zweiter Lauf findet nichts mehr");
        JsonObject root = JsonParser.parseString(ModConfig.GSON.toJson(loaded)).getAsJsonObject();
        assertFalse(root.has("bazaar"), "oben wird nichts mehr geschrieben");
        assertEquals("COMMUNITY", root.getAsJsonObject("inventory").getAsJsonObject("flips").get("tax").getAsString());
    }

    @Test
    void aFileWithTheFlipsAlreadyInInventoryIsLeftAlone() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"inventory\":{\"flips\":{\"tax\":\"FLIPPER\",\"minPerDay\":300.0}}}", ModConfig.class);
        assertFalse(ModConfig.migrateBazaarFlips(loaded));
        assertEquals(ModConfig.SellTax.FLIPPER, loaded.inventory.flips.tax);
        assertEquals(300f, loaded.inventory.flips.minPerDay);
    }

    @Test
    void anUnknownTaxIsRepairedToThePlainRate() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"inventory\":{\"flips\":{\"tax\":\"NOPE\"}}}", ModConfig.class);
        ModConfig.repairInventory(loaded);
        assertEquals(ModConfig.SellTax.PLAIN, loaded.inventory.flips.tax);
        ModConfig second = ModConfig.GSON.fromJson("{\"inventory\":{\"flips\":null}}", ModConfig.class);
        ModConfig.repairInventory(second);
        assertNotNull(second.inventory.flips);
        assertEquals(ModConfig.SellTax.PLAIN, second.inventory.flips.tax);
    }

    @Test
    void theSavedDataOfTheRemovedTrackersIsIgnoredAndNotWrittenAgain() {
        String old = "{\"hunting\":{\"tracker\":{\"enabled\":true,\"counts\":{\"SHARD_X\":3}}},"
                + "\"collections\":{\"tracker\":{\"enabled\":true,\"gains\":{\"WHEAT\":9}}}}";
        ModConfig loaded = ModConfig.GSON.fromJson(old, ModConfig.class);
        assertNotNull(loaded.hunting.fusion, "die Fusions bleiben");
        JsonObject root = JsonParser.parseString(ModConfig.GSON.toJson(loaded)).getAsJsonObject();
        assertFalse(root.has("collections"));
        assertFalse(root.getAsJsonObject("hunting").has("tracker"));
    }
}
