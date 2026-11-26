package com.shokiteufel.shokimod.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kennungen aus dem Namen einer Ware im Basar (2.4.17): Shards, Essenzen und Buecher stehen nicht in der Liste der Items. */
class BazaarIdsTest {

    @Test
    void aShardNameGivesTheShardId() {
        List<String> ids = BazaarIds.derived("Sprawl Shard");
        assertEquals("SHARD_SPRAWL", ids.get(0));
        assertTrue(ids.contains("SPRAWL_SHARD"), "und die Schreibweise mit dem Wort hinten (Prismarine Shard)");
        assertEquals("SHARD_QUEEN_BEE", BazaarIds.derived("Queen Bee Shard").get(0));
        assertEquals("SHARD_ABYSSAL_LANTERN", BazaarIds.derived("Abyssal Lantern Shard").get(0));
    }

    @Test
    void anEssenceNameGivesTheEssenceId() {
        assertEquals("ESSENCE_GOLD", BazaarIds.derived("Gold Essence").get(0));
        assertEquals("ESSENCE_WITHER", BazaarIds.derived("Wither Essence").get(0));
        assertEquals("ESSENCE_UNDEAD", BazaarIds.derived("Undead Essence").get(0));
    }

    @Test
    void aBookNameGivesTheEnchantmentIdWithAndWithoutUltimate() {
        List<String> sharpness = BazaarIds.derived("Sharpness VI");
        assertEquals("ENCHANTMENT_SHARPNESS_6", sharpness.get(0));
        assertTrue(sharpness.contains("ENCHANTMENT_ULTIMATE_SHARPNESS_6"));
        List<String> wisdom = BazaarIds.derived("Wisdom V");
        assertTrue(wisdom.contains("ENCHANTMENT_ULTIMATE_WISDOM_5"), "bei den Ultimates fehlt das Wort im Namen");
        assertTrue(BazaarIds.derived("Ultimate Wise V").contains("ENCHANTMENT_ULTIMATE_WISE_5"));
        assertTrue(BazaarIds.derived("One For All I").contains("ENCHANTMENT_ULTIMATE_ONE_FOR_ALL_1"));
        assertTrue(BazaarIds.derived("Turbo-Wheat V").contains("ENCHANTMENT_TURBO_WHEAT_5"), "der Bindestrich wird ein Unterstrich");
        assertTrue(BazaarIds.derived("Efficiency X").contains("ENCHANTMENT_EFFICIENCY_10"));
        assertTrue(BazaarIds.derived("Looting 5").contains("ENCHANTMENT_LOOTING_5"), "auch eine gewoehnliche Zahl");
    }

    @Test
    void anOrdinaryNameGivesItsOwnIdAsAFallback() {
        assertEquals(List.of("ENCHANTED_BROWN_MUSHROOM"), BazaarIds.derived("Enchanted Brown Mushroom"));
        assertTrue(BazaarIds.derived("Jerry's Stick").contains("JERRYS_STICK"));
    }

    @Test
    void colourCodesAndSpacesDoNotMatter() {
        assertEquals("SHARD_SPRAWL", BazaarIds.derived("§aSprawl Shard  ").get(0));
        assertEquals(List.of(), BazaarIds.derived(""));
        assertEquals(List.of(), BazaarIds.derived(null));
        assertEquals(List.of(), BazaarIds.derived("  "));
    }

    @Test
    void aWordThatEndsInLowerCaseLettersIsNoLevel() {
        List<String> ids = BazaarIds.derived("Gold Ingot");
        assertEquals(List.of("GOLD_INGOT"), ids);
        List<String> mixed = BazaarIds.derived("Vial");
        assertEquals(List.of("VIAL"), mixed);
    }

    @Test
    void theKeyIsWrittenLikeTheBazaarDoes() {
        assertEquals("TURBO_WHEAT", BazaarIds.key("Turbo-Wheat"));
        assertEquals("ONE_FOR_ALL", BazaarIds.key(" One For All "));
        assertEquals("", BazaarIds.key("---"));
        assertEquals("", BazaarIds.key(null));
    }
}
