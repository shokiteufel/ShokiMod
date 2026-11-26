package com.shokiteufel.shokimod.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shokiteufel.shokimod.util.EivData.Reforge;
import com.shokiteufel.shokimod.util.EivData.Upgrade;
import com.shokiteufel.shokimod.util.EivData.ValueData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Estimated Item Value (2.4.18): die Tabellen aus dem Netz lesen. */
class EivDataTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void aReforgeStoneIsFoundByItsModifier() {
        Map<String, Reforge> r = EivData.parseReforges(json("""
                {"AMBER_MATERIAL": {"internalName": "AMBER_MATERIAL", "reforgeName": "Ambered", "reforgeCosts": {"COMMON": 250000, "LEGENDARY": 250000}},
                 "AOTE_STONE": {"internalName": "AOTE_STONE", "reforgeName": "Warped", "nbtModifier": "aote_stone", "reforgeCosts": {"EPIC": 100000}},
                 "NO_STONE": {"reforgeName": "Plain"}}"""));
        assertEquals(2, r.size(), "ohne Stein zaehlt ein Eintrag nicht");
        assertEquals("AMBER_MATERIAL", r.get("ambered").stone(), "ohne nbtModifier der Name klein");
        assertEquals("Ambered", r.get("ambered").name());
        assertEquals(250_000L, r.get("ambered").costs().get("LEGENDARY"));
        assertEquals("Warped", r.get("aote_stone").name(), "mit nbtModifier dieser");
    }

    @Test
    void theModifierWritesSpacesAndDashesAsUnderscores() {
        assertEquals("fabled", EivData.modifierOf("Fabled", null));
        assertEquals("bulky_beyond", EivData.modifierOf("Bulky Beyond", null));
        assertEquals("heavy_duty", EivData.modifierOf("Heavy-Duty!", ""));
        assertEquals("given", EivData.modifierOf("Whatever", "given"));
    }

    @Test
    void essenceCostsListEveryStarWithItsExtras() {
        Map<String, Map<Integer, Upgrade>> e = EivData.parseEssence(json("""
                {"ADAPTIVE_BELT": {"type": "Wither", "1": 10, "2": 20, "3": 35, "4": 50, "5": 75,
                                   "items": {"4": ["SKYBLOCK_COIN:10000"], "5": ["SKYBLOCK_COIN:25000", "RAREST_THING:2"]}}}"""));
        Map<Integer, Upgrade> tiers = e.get("ADAPTIVE_BELT");
        assertEquals(5, tiers.size());
        assertEquals("Wither", tiers.get(1).essenceType());
        assertEquals(35, tiers.get(3).essence());
        assertEquals(10_000L, tiers.get(4).coins());
        assertEquals(25_000L, tiers.get(5).coins());
        assertEquals(2, tiers.get(5).items().get("RAREST_THING"));
        assertEquals(0L, tiers.get(1).coins());
    }

    @Test
    void twoUpgradesAddUp() {
        Upgrade a = new Upgrade("Wither", 10, 100L, Map.of("X", 1));
        Upgrade b = new Upgrade("Wither", 5, 50L, Map.of("X", 2, "Y", 1));
        Upgrade sum = a.plus(b);
        assertEquals(15, sum.essence());
        assertEquals(150L, sum.coins());
        assertEquals(3, sum.items().get("X"));
        assertEquals(1, sum.items().get("Y"));
    }

    @Test
    void gemstoneSlotsListTheirIngredients() {
        Map<String, Map<String, List<String>>> g = EivData.parseGemSlots(json(
                "{\"ABYSSAL_BOOTS\": {\"AQUAMARINE_0\": [\"FINE_AQUAMARINE_GEM:40\", \"SKYBLOCK_COIN:250000\"]}}"));
        assertEquals(List.of("FINE_AQUAMARINE_GEM:40", "SKYBLOCK_COIN:250000"), g.get("ABYSSAL_BOOTS").get("AQUAMARINE_0"));
    }

    @Test
    void theValueRulesComeFromItemsJson() {
        Map<String, ValueData> v = EivData.parseValues(json("""
                {"book_bundle_amount": {"big_brain": 5, "pristine": 1},
                 "crimson_prestige_costs": {"HOT": {"ESSENCE_CRIMSON": 150, "KUUDRA_TEETH": 10}},
                 "has_legacy_gemstone_slots": ["GIANT_FISHING_ROD"],
                 "value_calculation_data": {
                   "always_active_enchants": {"scavenger": {"level": 5, "items": ["EARTH_SHARD"]}},
                   "only_tier_one_prices": ["ultimate_chimera"],
                   "only_tier_five_prices": ["strong_mana"],
                   "endcap_enchants_new": {"charm": [{"required_level": 5, "endcap_item": "CHAIN_END_TIMES"}]}}}"""));
        ValueData d = v.get("data");
        assertNotNull(d);
        assertEquals(5, d.alwaysActive().get("scavenger").level());
        assertTrue(d.alwaysActive().get("scavenger").items().contains("EARTH_SHARD"));
        assertTrue(d.onlyTierOne().contains("ultimate_chimera"));
        assertTrue(d.onlyTierFive().contains("strong_mana"));
        assertEquals("CHAIN_END_TIMES", d.endcaps().get("charm").get(0).item());
        assertEquals(5, d.endcaps().get("charm").get(0).requiredLevel());
        assertEquals(1, d.bookBundleAmount().get("pristine"));
        assertEquals(150, d.crimsonPrestige().get("HOT").get("ESSENCE_CRIMSON"));
        assertTrue(d.legacyGemSlots().contains("GIANT_FISHING_ROD"));
    }

    @Test
    void aFileWithoutRulesGivesNothing() {
        assertTrue(EivData.parseValues(json("{\"other\": 1}")).isEmpty());
    }

    @Test
    void stackingEnchantsAreTheKeysOfTheStackingGroup() {
        Map<String, Boolean> s = EivData.parseStacking(json("{\"NORMAL\": {\"angler\": {}}, \"STACKING\": {\"compact\": {}, \"cultivating\": {}}}"));
        assertEquals(2, s.size());
        assertTrue(s.containsKey("compact"));
        assertFalse(s.containsKey("angler"));
        assertNull(s.get("angler"));
    }
}
