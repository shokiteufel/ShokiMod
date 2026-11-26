package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.data.ModConfig.EivPriceSource;
import com.shokiteufel.shokimod.util.EivData.AlwaysActive;
import com.shokiteufel.shokimod.util.EivData.Endcap;
import com.shokiteufel.shokimod.util.EivData.Reforge;
import com.shokiteufel.shokimod.util.EivData.Upgrade;
import com.shokiteufel.shokimod.util.EivData.ValueData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Estimated Item Value (2.4.18): der Rechner mit erfundenen Preisen und Tabellen. */
class EivCalculatorTest {

    /** Preise, Namen und Tabellen aus Hash-Tabellen */
    static final class Fake implements EivCalculator.Env {
        final Map<String, Double> prices = new HashMap<>();
        final Map<String, Reforge> reforges = new HashMap<>();
        final Map<String, Map<Integer, Upgrade>> essence = new HashMap<>();
        final Map<String, Map<String, List<String>>> gemSlots = new HashMap<>();
        final Set<String> stacking = new HashSet<>();
        final Set<String> bazaar = new HashSet<>();
        ValueData values = new ValueData(Map.of(), Set.of(), Set.of(), Map.of(), Map.of(), Map.of(), Set.of());

        @Override
        public Double price(String id, EivPriceSource source) {
            return id.equals("SKYBLOCK_COIN") ? Double.valueOf(1.0) : prices.get(id);
        }

        @Override
        public String name(String id) {
            return "§f" + id;
        }

        @Override
        public Reforge reforge(String modifier) {
            return reforges.get(modifier);
        }

        @Override
        public Map<Integer, Upgrade> essence(String id) {
            return essence.get(id);
        }

        @Override
        public Set<String> essenceIds() {
            return essence.keySet();
        }

        @Override
        public boolean hasGemSlotData() {
            return !gemSlots.isEmpty();
        }

        @Override
        public Map<String, List<String>> gemSlots(String id) {
            return gemSlots.get(id);
        }

        @Override
        public ValueData values() {
            return values;
        }

        @Override
        public boolean stacking(String enchant) {
            return stacking.contains(enchant);
        }

        @Override
        public boolean bazaar(String id) {
            return bazaar.contains(id);
        }
    }

    private static EivCalculator.Options options() {
        return new EivCalculator.Options(EivPriceSource.INSTANT_SELL, 7, 3, false, false, false);
    }

    private static EivCalculator.Subject subject(String id, CompoundTag tag, String... lore) {
        tag.putString("id", id);
        return new EivCalculator.Subject(id, id, id, tag, List.of(lore), id);
    }

    private static boolean has(EivCalculator.Result r, String part) {
        return r.lines().stream().anyMatch(l -> l.contains(part));
    }

    // ------------------------------------------------------------------ Grundpreis

    @Test
    void anUnchangedItemIsWorthItsBasePrice() {
        Fake env = new Fake();
        env.prices.put("ASPECT_OF_THE_END", 100_000.0);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("ASPECT_OF_THE_END", new CompoundTag()));
        assertEquals(100_000.0, r.base());
        assertEquals(100_000.0, r.total());
        assertEquals("§7Base item: §fASPECT_OF_THE_END §7(§6100k§7)", r.lines().get(0));
    }

    @Test
    void aPetShowsItsLevelNextToTheName() {
        Fake env = new Fake();
        env.prices.put("ENDERMITE;5+100", 2_000_000.0);
        EivCalculator.Subject pet = new EivCalculator.Subject("ENDERMITE;5", "PET", "ENDERMITE;5+100", new CompoundTag(), List.of(), "[Lvl 100] Endermite");
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(pet);
        assertEquals(2_000_000.0, r.base());
        assertTrue(r.lines().get(0).startsWith("§7Base item: §fENDERMITE;5 LVL 100 "));
    }

    @Test
    void anItemWithoutAPriceHasABaseOfZero() {
        EivCalculator.Result r = new EivCalculator(new Fake(), options()).calculate(subject("NOBODY_SELLS_THIS", new CompoundTag()));
        assertEquals(0.0, r.base());
        assertEquals(0.0, r.total());
    }

    @Test
    void aBookBundleHasNoBasePriceOfItsOwn() {
        Fake env = new Fake();
        env.prices.put("ENCHANTED_BOOK_BUNDLE_SHARPNESS", 99.0);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("ENCHANTED_BOOK_BUNDLE_SHARPNESS", new CompoundTag()));
        assertEquals(0.0, r.base());
        assertEquals("§7Base item: §fENCHANTED_BOOK_BUNDLE_SHARPNESS", r.lines().get(0));
    }

    // ------------------------------------------------------------------ Umschmiedung

    private static Fake withWitheredReforge() {
        Fake env = new Fake();
        env.prices.put("HYPERION", 800_000_000.0);
        env.prices.put("WITHER_BLOOD", 300_000.0);
        env.reforges.put("withered", new Reforge("Withered", "withered", "WITHER_BLOOD",
                Map.of("EPIC", 500_000L, "LEGENDARY", 1_000_000L)));
        return env;
    }

    @Test
    void aReforgeStoneCountsWithItsApplyCost() {
        Fake env = withWitheredReforge();
        CompoundTag tag = new CompoundTag();
        tag.putString("modifier", "withered");
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("HYPERION", tag, "Wither Impact", "", "LEGENDARY SWORD"));
        assertEquals(800_000_000.0 + 300_000.0 + 1_000_000.0, r.total());
        assertTrue(has(r, "§7Reforge: §9Withered"));
        assertTrue(has(r, " §7Stone: §fWITHER_BLOOD §7(§6300k§7)"));
        assertTrue(has(r, " §7Apply cost: §7(§61M§7)"));
    }

    @Test
    void aRecombobulatorLowersTheRarityForTheApplyCost() {
        Fake env = withWitheredReforge();
        env.prices.put("RECOMBOBULATOR_3000", 6_000_000.0);
        CompoundTag tag = new CompoundTag();
        tag.putString("modifier", "withered");
        tag.putInt("rarity_upgrades", 1);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("HYPERION", tag, "a LEGENDARY SWORD a"));
        // Die Seltenheit ohne Recombobulator ist EPIC: 500k
        assertEquals(800_000_000.0 + 300_000.0 + 500_000.0 + 6_000_000.0, r.total());
        assertTrue(has(r, "§7Recombobulated: §a§l✔ §7(§66M§7)"));
    }

    @Test
    void aRarityAboveMythicCountsAsLegendary() {
        Fake env = withWitheredReforge();
        CompoundTag tag = new CompoundTag();
        tag.putString("modifier", "withered");
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("HYPERION", tag, "DIVINE SWORD"));
        assertEquals(800_000_000.0 + 300_000.0 + 1_000_000.0, r.total());
    }

    @Test
    void aReforgeWithoutTheRightRarityCostsNothingExtra() {
        Fake env = withWitheredReforge();
        CompoundTag tag = new CompoundTag();
        tag.putString("modifier", "withered");
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("HYPERION", tag, "COMMON SWORD"));
        assertEquals(800_000_000.0, r.total());
        assertFalse(has(r, "Reforge"));
    }

    @Test
    void theRarityIsReadFromTheLastLineOfTheLore() {
        assertEquals("LEGENDARY", EivCalculator.rarityOf(List.of("x", "", "a LEGENDARY BOW a")));
        assertEquals("UNCOMMON", EivCalculator.rarityOf(List.of("UNCOMMON ITEM")));
        assertEquals("VERY_SPECIAL", EivCalculator.rarityOf(List.of("VERY SPECIAL")));
        assertEquals("RARE", EivCalculator.rarityOf(List.of("RARE HELMET", "", "")), "leere Zeilen am Ende zaehlen nicht");
        assertNull(EivCalculator.rarityOf(List.of("no rarity here")));
        assertNull(EivCalculator.rarityOf(List.of()));
    }

    // ------------------------------------------------------------------ einzelne Zutaten

    @Test
    void oneTimeExtrasAreCountedWhenTheirFlagIsSet() {
        Fake env = new Fake();
        env.prices.put("SWORD", 1000.0);
        env.prices.put("THE_ART_OF_WAR", 10_000_000.0);
        env.prices.put("WOOD_SINGULARITY", 500_000.0);
        env.prices.put("ETHERWARP_CONDUIT", 100_000.0);
        env.prices.put("ETHERWARP_MERGER", 50_000.0);
        env.prices.put("MITHRIL_INFUSION", 300_000.0);
        CompoundTag tag = new CompoundTag();
        tag.putInt("art_of_war_count", 1);
        tag.putInt("wood_singularity_count", 1);
        tag.putByte("ethermerge", (byte) 1);
        tag.putByte("mithril_infusion", (byte) 1);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("SWORD", tag));
        assertEquals(1000.0 + 10_000_000.0 + 500_000.0 + 150_000.0 + 300_000.0, r.total());
        assertTrue(has(r, "§7The Art of War: §a§l✔"));
        assertTrue(has(r, "§7Etherwarp: §a§l✔ §7(§6150k§7)"));
    }

    @Test
    void hotPotatoBooksSplitIntoNormalAndFuming() {
        Fake env = new Fake();
        env.prices.put("HELMET", 1.0);
        env.prices.put("HOT_POTATO_BOOK", 100_000.0);
        env.prices.put("FUMING_POTATO_BOOK", 1_000_000.0);
        CompoundTag tag = new CompoundTag();
        tag.putInt("hot_potato_count", 13);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("HELMET", tag));
        assertEquals(1.0 + 10 * 100_000.0 + 3 * 1_000_000.0, r.total());
        assertTrue(has(r, "§7HPB's: §e10§7/§e10 §7(§61M§7)"));
        assertTrue(has(r, "§7Fuming: §e3§7/§e5 §7(§63M§7)"));
    }

    @Test
    void justTenOrFewerBooksHaveNoFumingLine() {
        Fake env = new Fake();
        env.prices.put("HELMET", 1.0);
        env.prices.put("HOT_POTATO_BOOK", 100_000.0);
        CompoundTag tag = new CompoundTag();
        tag.putInt("hot_potato_count", 4);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("HELMET", tag));
        assertEquals(1.0 + 400_000.0, r.total());
        assertFalse(has(r, "Fuming"));
    }

    @Test
    void silexCountsEfficiencyAboveFive() {
        Fake env = new Fake();
        env.prices.put("DIAMOND_PICKAXE", 1.0);
        env.prices.put("SIL_EX", 1_000_000.0);
        CompoundTag enchants = new CompoundTag();
        enchants.putInt("efficiency", 8);
        CompoundTag tag = new CompoundTag();
        tag.put("enchantments", enchants);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("DIAMOND_PICKAXE", tag));
        assertEquals(1.0 + 3_000_000.0, r.total(), "Wirksamkeit 8 minus 5 sind 3 Silex");
        assertTrue(has(r, "§7Silex: §e3§7/§e5"));
    }

    @Test
    void theStonkPickaxeHasOneSilexFromTheStart() {
        Fake env = new Fake();
        env.prices.put("STONK_PICKAXE", 1.0);
        env.prices.put("SIL_EX", 1_000_000.0);
        CompoundTag enchants = new CompoundTag();
        enchants.putInt("efficiency", 9);
        CompoundTag tag = new CompoundTag();
        tag.put("enchantments", enchants);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("STONK_PICKAXE", tag));
        assertEquals(1.0 + 3_000_000.0, r.total(), "9 - 5 - 1 = 3, und die Obergrenze ist 4");
        assertTrue(has(r, "§7Silex: §e3§7/§e4"));
    }

    // ------------------------------------------------------------------ Sterne

    private static Map<Integer, Upgrade> tiers(int stars, String type, int essence) {
        Map<Integer, Upgrade> out = new java.util.TreeMap<>();
        for (int i = 1; i <= stars; i++) out.put(i, new Upgrade(type, essence, i >= 4 ? 10_000L : 0L, Map.of()));
        return out;
    }

    @Test
    void starsCostEssenceAndCoinsUpToTheLevelOfTheItem() {
        Fake env = new Fake();
        env.prices.put("HYPERION", 1.0);
        env.prices.put("ESSENCE_WITHER", 1000.0);
        env.essence.put("HYPERION", tiers(5, "Wither", 100));
        CompoundTag tag = new CompoundTag();
        tag.putInt("upgrade_level", 4);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("HYPERION", tag, "LEGENDARY DUNGEON SWORD"));
        // 4 Sterne: 400 Essenz zu je 1000 und 10.000 Coins (die gibt es erst ab dem vierten Stern)
        assertEquals(1.0 + 400_000.0 + 10_000.0, r.total());
        assertTrue(has(r, "§7Stars: §e4§7/§e5"));
        assertTrue(has(r, "§8400x §r§fESSENCE_WITHER"), "die Essenz steht mit Menge in der Zeile darunter");
    }

    @Test
    void aStarItemOutsideTheDungeonUsesTheUpgradeLevel() {
        Fake env = new Fake();
        env.prices.put("CRIMSON_BOOTS", 1.0);
        env.prices.put("ESSENCE_CRIMSON", 50.0);
        env.essence.put("CRIMSON_BOOTS", tiers(10, "Crimson", 10));
        CompoundTag tag = new CompoundTag();
        tag.putInt("upgrade_level", 2);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("CRIMSON_BOOTS", tag, "EPIC BOOTS"));
        assertEquals(1.0 + 20 * 50.0, r.total());
        assertTrue(has(r, "§7Stars: §e2§7/§e10"));
    }

    @Test
    void onlyTheMostExpensiveMaterialsOfTheStarsAreListed() {
        Fake env = new Fake();
        env.prices.put("HYPERION", 1.0);
        env.prices.put("ESSENCE_WITHER", 10.0);
        env.prices.put("A", 5000.0);
        env.prices.put("B", 9000.0);
        env.prices.put("C", 100.0);
        Map<Integer, Upgrade> tiers = new java.util.TreeMap<>();
        tiers.put(1, new Upgrade("Wither", 10, 0L, Map.of("A", 1, "B", 1, "C", 1)));
        env.essence.put("HYPERION", tiers);
        CompoundTag tag = new CompoundTag();
        tag.putInt("upgrade_level", 1);
        EivCalculator.Result r = new EivCalculator(env, new EivCalculator.Options(EivPriceSource.INSTANT_SELL, 7, 2, false, false, false))
                .calculate(subject("HYPERION", tag, "DUNGEON SWORD"));
        assertTrue(has(r, " §r§fB §7("), "das teuerste");
        assertTrue(has(r, " §r§fA §7("), "das zweitteuerste");
        assertFalse(has(r, " §r§fC §7("), "bei Star Material Cap 2 fehlt das dritte");
        assertEquals(1.0 + 100 + 5000 + 9000 + 100, r.total());
    }

    @Test
    void masterStarsComeAfterTheFifthStar() {
        Fake env = new Fake();
        env.prices.put("HYPERION", 1.0);
        env.prices.put("FIRST_MASTER_STAR", 1_000_000.0);
        env.prices.put("SECOND_MASTER_STAR", 2_000_000.0);
        env.prices.put("THIRD_MASTER_STAR", 3_000_000.0);
        CompoundTag tag = new CompoundTag();
        tag.putInt("dungeon_item_level", 8);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("HYPERION", tag, "LEGENDARY DUNGEON SWORD"));
        assertEquals(1.0 + 6_000_000.0, r.total());
        assertTrue(has(r, "§7Master Stars: §e3§7/§e5 §7(§66M§7)"));
    }

    @Test
    void aKuudraArmorCountsTheStarsOfEveryTierBelowIt() {
        Fake env = new Fake();
        env.prices.put("CRIMSON_CHESTPLATE", 1.0);
        env.prices.put("ESSENCE_CRIMSON", 10.0);
        for (String prefix : new String[] {"", "HOT_", "BURNING_", "FIERY_", "INFERNAL_"}) {
            env.essence.put(prefix + "CRIMSON_CHESTPLATE", tiers(10, "Crimson", 10));
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("upgrade_level", 4);
        // FIERY ist die vierte Stufe: 4 Sterne plus 3 mal 10 = 34 von 50
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("FIERY_CRIMSON_CHESTPLATE", tag, "LEGENDARY CHESTPLATE"));
        assertTrue(has(r, "§7Stars: §e34§7/§e50"), r.lines().toString());
        // 340 Essenz zu 10 und Coins ab dem vierten Stern: je voller Stufe 7 mal 10.000, in der vierten ein Mal 10.000
        assertEquals(1.0 + 3_400.0 + 220_000.0, r.total());
    }

    @Test
    void kuudraTiersAreReadFromTheId() {
        assertEquals(1, EivCalculator.kuudraTier("CRIMSON_HELMET"));
        assertEquals(2, EivCalculator.kuudraTier("HOT_AURORA_BOOTS"));
        assertEquals(5, EivCalculator.kuudraTier("INFERNAL_TERROR_LEGGINGS"));
        assertEquals(0, EivCalculator.kuudraTier("HYPERION"));
        assertTrue(EivCalculator.isKuudraArmor("FIERY_HOLLOW_CHESTPLATE"));
        assertFalse(EivCalculator.isKuudraArmor("CRIMSON_ROD"));
        assertEquals("CRIMSON_BOOTS", EivCalculator.removeKuudraTier("BURNING_CRIMSON_BOOTS"));
        assertEquals("CRIMSON_BOOTS", EivCalculator.removeKuudraTier("CRIMSON_BOOTS"));
    }

    @Test
    void aPrestigeTierAddsTheCostsOfEveryTierUpToIt() {
        Fake env = new Fake();
        env.prices.put("CRIMSON_BOOTS", 1.0);
        env.prices.put("ESSENCE_CRIMSON", 10.0);
        env.prices.put("KUUDRA_TEETH", 1000.0);
        env.values = new ValueData(Map.of(), Set.of(), Set.of(), Map.of(), Map.of(),
                Map.of("HOT", Map.of("ESSENCE_CRIMSON", 150, "KUUDRA_TEETH", 10),
                        "BURNING", Map.of("ESSENCE_CRIMSON", 800, "KUUDRA_TEETH", 20)), Set.of());
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("BURNING_CRIMSON_BOOTS", new CompoundTag(), "LEGENDARY BOOTS"));
        // HOT und BURNING: 950 Essenz zu 10 und 30 Zaehne zu 1000
        assertEquals(1.0 + 9_500.0 + 30_000.0, r.total());
        assertTrue(has(r, "§7Prestige Tier: Burning"), r.lines().toString());
    }

    // ------------------------------------------------------------------ Schmuck, Schriftrollen, Teile

    @Test
    void helmetSkinsDyesAndRunesCountUnlessIgnored() {
        Fake env = new Fake();
        env.prices.put("HELMET", 1.0);
        env.prices.put("PET_SKIN_X", 1_000_000.0);
        env.prices.put("DYE_BLACK", 500_000.0);
        env.prices.put("BITE_RUNE;3", 100_000.0);
        CompoundTag runes = new CompoundTag();
        runes.putInt("bite", 3);
        CompoundTag tag = new CompoundTag();
        tag.putString("skin", "PET_SKIN_X");
        tag.putString("dye_item", "DYE_BLACK");
        tag.put("runes", runes);
        EivCalculator.Result all = new EivCalculator(env, options()).calculate(subject("HELMET", tag));
        assertEquals(1.0 + 1_600_000.0, all.total());
        assertTrue(has(all, "§7Skin: §fPET_SKIN_X §7(§61M§7)"));
        assertTrue(has(all, "§7Rune: §fBITE_RUNE;3"));

        EivCalculator.Result ignoring = new EivCalculator(env, new EivCalculator.Options(EivPriceSource.INSTANT_SELL, 7, 3, true, true, true))
                .calculate(subject("HELMET", tag));
        assertEquals(1.0, ignoring.total());
        assertTrue(has(ignoring, "§7Skin: §fPET_SKIN_X §7(§71M§7)"), "grau, aber noch zu sehen");
    }

    @Test
    void aRuneItselfHasNoRuneLine() {
        Fake env = new Fake();
        env.prices.put("BITE_RUNE;3", 100_000.0);
        CompoundTag runes = new CompoundTag();
        runes.putInt("bite", 3);
        CompoundTag tag = new CompoundTag();
        tag.put("runes", runes);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(new EivCalculator.Subject("BITE_RUNE;3", "RUNE", "BITE_RUNE;3", tag, List.of(), "Bite Rune"));
        assertEquals(100_000.0, r.total());
    }

    @Test
    void theUltimateWitherScrollStandsForThreeScrolls() {
        Fake env = new Fake();
        env.prices.put("HYPERION", 1.0);
        env.prices.put("IMPLOSION_SCROLL", 100_000_000.0);
        env.prices.put("WITHER_SHIELD_SCROLL", 50_000_000.0);
        env.prices.put("SHADOW_WARP_SCROLL", 30_000_000.0);
        ListTag scrolls = new ListTag();
        scrolls.add(StringTag.valueOf("ULTIMATE_WITHER_SCROLL"));
        CompoundTag tag = new CompoundTag();
        tag.put("ability_scroll", scrolls);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("HYPERION", tag));
        assertEquals(1.0 + 180_000_000.0, r.total());
        assertTrue(has(r, "§7Ability Scrolls: §6180M"));
    }

    @Test
    void anItemWithoutAPriceIsListedAsUnknownAndCountsNothing() {
        Fake env = new Fake();
        env.prices.put("DRILL", 1.0);
        ListTag boosters = new ListTag();
        boosters.add(StringTag.valueOf("MYSTERY"));
        CompoundTag tag = new CompoundTag();
        tag.put("boosters", boosters);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("DRILL", tag));
        assertEquals(1.0, r.total());
        assertTrue(has(r, "MYSTERY_BOOSTER §cUnknown price!"));
        assertTrue(has(r, "§7Boosters: §60"));
    }

    @Test
    void drillAndRodPartsAreListed() {
        Fake env = new Fake();
        env.prices.put("DIVANS_DRILL", 1.0);
        env.prices.put("TITANIUM_ENGINE", 4_000_000.0);
        env.prices.put("TITAN_LINE", 2_000_000.0);
        CompoundTag tag = new CompoundTag();
        tag.putString("drill_part_engine", "titanium_engine");
        CompoundTag line = new CompoundTag();
        line.putString("part", "titan_line");
        tag.put("line", line);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("DIVANS_DRILL", tag));
        assertEquals(1.0 + 6_000_000.0, r.total());
        assertTrue(has(r, "§7Drill upgrades: §64M"));
        assertTrue(has(r, "§7Rod upgrades: §62M"));
    }

    // ------------------------------------------------------------------ Edelsteine

    @Test
    void appliedGemstonesAreReadFromAllThreeShapes() {
        CompoundTag gems = new CompoundTag();
        gems.putString("JASPER_0", "FINE");
        CompoundTag ruby = new CompoundTag();
        ruby.putString("quality", "FLAWLESS");
        gems.put("RUBY_0", ruby);
        gems.putString("COMBAT_0", "PERFECT");
        gems.putString("COMBAT_0_gem", "JASPER");
        gems.putString("OPAL_0", "NOT_A_QUALITY");
        gems.put("unlocked_slots", new ListTag());
        List<String> ids = EivCalculator.gemstoneIds(gems);
        assertTrue(ids.contains("FINE_JASPER_GEM"));
        assertTrue(ids.contains("FLAWLESS_RUBY_GEM"));
        assertTrue(ids.contains("PERFECT_JASPER_GEM"), "der Platz COMBAT_0 traegt seinen Typ im Feld mit _gem");
        assertEquals(3, ids.size());
    }

    @Test
    void anUnlockedGemstoneSlotCountsItsCost() {
        Fake env = new Fake();
        env.prices.put("ABYSSAL_BOOTS", 1.0);
        env.prices.put("FINE_AQUAMARINE_GEM", 100_000.0);
        env.gemSlots.put("ABYSSAL_BOOTS", Map.of(
                "AQUAMARINE_0", List.of("FINE_AQUAMARINE_GEM:40", "SKYBLOCK_COIN:250000"),
                "CITRINE_0", List.of("FINE_CITRINE_GEM:40")));
        ListTag unlocked = new ListTag();
        unlocked.add(StringTag.valueOf("AQUAMARINE_0"));
        CompoundTag gems = new CompoundTag();
        gems.put("unlocked_slots", unlocked);
        CompoundTag tag = new CompoundTag();
        tag.put("gems", gems);
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(subject("ABYSSAL_BOOTS", tag));
        assertEquals(1.0 + 40 * 100_000.0 + 250_000.0, r.total());
        assertTrue(has(r, "§7Gemstone Slot Unlock Cost: §64.2M"), r.lines().toString());
        assertTrue(has(r, " §7Unlocked slots: §3Aquamarine Slot"));
        assertFalse(has(r, "Citrine"), "ein Platz, der nicht frei ist, zaehlt nicht");
    }

    // ------------------------------------------------------------------ Verzauberungen

    private static Fake withEnchantRules() {
        Fake env = new Fake();
        env.values = new ValueData(
                Map.of("scavenger", new AlwaysActive(5, Set.of("EARTH_SHARD"))),
                Set.of("ultimate_chimera"), Set.of("strong_mana"),
                Map.of("charm", List.of(new Endcap(5, "CHAIN_END_TIMES"))),
                Map.of("big_brain", 5, "pristine", 1), Map.of(), Set.of());
        env.stacking.add("compact");
        for (String id : new String[] {"ENCHANTMENT_SHARPNESS_6", "ENCHANTMENT_ULTIMATE_CHIMERA_1", "ENCHANTMENT_STRONG_MANA_5", "ENCHANTMENT_COMPACT_1",
                "ENCHANTMENT_CHARM_5", "ENCHANTMENT_SCAVENGER_5", "ENCHANTMENT_BIG_BRAIN_5", "ENCHANTMENT_PRISTINE_5"}) {
            env.bazaar.add(id);
        }
        return env;
    }

    @Test
    void efficiencyAndAlwaysActiveEnchantsAreLeftOut() {
        Fake env = withEnchantRules();
        EivCalculator calculator = new EivCalculator(env, options());
        Map<String, Integer> enchants = new java.util.LinkedHashMap<>();
        enchants.put("sharpness", 6);
        enchants.put("efficiency", 10);
        enchants.put("scavenger", 5);
        Map<String, Integer> items = calculator.enchantmentItems(enchants, "EARTH_SHARD", env.values);
        assertEquals(Map.of("ENCHANTMENT_SHARPNESS_6", 1), items);
        // Auf einem anderen Gegenstand zaehlt Scavenger
        Map<String, Integer> other = calculator.enchantmentItems(enchants, "SOME_SWORD", env.values);
        assertTrue(other.containsKey("ENCHANTMENT_SCAVENGER_5"));
    }

    @Test
    void onlyTheFirstOrFifthTierCountsForSomeEnchants() {
        Fake env = withEnchantRules();
        EivCalculator calculator = new EivCalculator(env, options());
        Map<String, Integer> enchants = new java.util.LinkedHashMap<>();
        enchants.put("ultimate_chimera", 4);
        enchants.put("strong_mana", 8);
        Map<String, Integer> items = calculator.enchantmentItems(enchants, "SOME_SWORD", env.values);
        assertEquals(8, items.get("ENCHANTMENT_ULTIMATE_CHIMERA_1"), "Stufe 4 sind acht Buecher der Stufe 1");
        assertEquals(8, items.get("ENCHANTMENT_STRONG_MANA_5"), "Stufe 8 sind acht Buecher der Stufe 5");
    }

    @Test
    void anEndcapAddsItsItemFromOneLevelAboveItsRequiredOne() {
        Fake env = withEnchantRules();
        EivCalculator calculator = new EivCalculator(env, options());
        Map<String, Integer> sixth = calculator.enchantmentItems(Map.of("charm", 6), "SOME_SWORD", env.values);
        assertEquals(1, sixth.get("CHAIN_END_TIMES"));
        assertEquals(1, sixth.get("ENCHANTMENT_CHARM_5"), "die Stufe zaehlt nur bis zur geforderten");
        Map<String, Integer> fifth = calculator.enchantmentItems(Map.of("charm", 5), "SOME_SWORD", env.values);
        assertFalse(fifth.containsKey("CHAIN_END_TIMES"));
    }

    @Test
    void stackingEnchantsCountAsTheirFirstLevel() {
        Fake env = withEnchantRules();
        Map<String, Integer> items = new EivCalculator(env, options()).enchantmentItems(Map.of("compact", 7), "SOME_PICKAXE", env.values);
        assertEquals(Map.of("ENCHANTMENT_COMPACT_1", 1), items);
    }

    @Test
    void aBookBundleCountsItsAmountPerEnchant() {
        Fake env = withEnchantRules();
        EivCalculator calculator = new EivCalculator(env, options());
        Map<String, Integer> enchants = new java.util.LinkedHashMap<>();
        enchants.put("big_brain", 5);
        enchants.put("pristine", 5);
        Map<String, Integer> items = calculator.enchantmentItems(enchants, "ENCHANTED_BOOK_BUNDLE_X", env.values);
        assertEquals(5, items.get("ENCHANTMENT_BIG_BRAIN_5"));
        assertEquals(1, items.get("ENCHANTMENT_PRISTINE_5"));
    }

    @Test
    void anEnchantTheBazaarDoesNotKnowIsSkipped() {
        Fake env = withEnchantRules();
        Map<String, Integer> items = new EivCalculator(env, options()).enchantmentItems(Map.of("unheard_of", 3), "SOME_SWORD", env.values);
        assertTrue(items.isEmpty());
    }

    @Test
    void theEnchantmentsOfAnItemAreListedWithTheMostExpensiveFirstAndCapped() {
        Fake env = withEnchantRules();
        env.prices.put("SOME_SWORD", 1.0);
        env.prices.put("ENCHANTMENT_SHARPNESS_6", 100_000.0);
        env.prices.put("ENCHANTMENT_COMPACT_1", 10_000.0);
        env.prices.put("ENCHANTMENT_ULTIMATE_CHIMERA_1", 5_000_000.0);
        CompoundTag enchants = new CompoundTag();
        enchants.putInt("sharpness", 6);
        enchants.putInt("compact", 3);
        enchants.putInt("ultimate_chimera", 1);
        CompoundTag tag = new CompoundTag();
        tag.put("enchantments", enchants);
        EivCalculator.Result r = new EivCalculator(env, new EivCalculator.Options(EivPriceSource.INSTANT_SELL, 2, 3, false, false, false))
                .calculate(subject("SOME_SWORD", tag));
        assertEquals(1.0 + 5_110_000.0, r.total());
        int header = r.lines().indexOf("§7Enchantments: §65.1M");
        assertTrue(header > 0, r.lines().toString());
        assertTrue(r.lines().get(header + 1).contains("ENCHANTMENT_ULTIMATE_CHIMERA_1"), "das teuerste zuerst");
        assertTrue(r.lines().get(header + 2).contains("ENCHANTMENT_SHARPNESS_6"));
        assertEquals(" §7§o1 more enchantments..", r.lines().get(header + 3));
    }

    @Test
    void anEnchantedBookItselfCountsOnlyItsEnchantment() {
        Fake env = withEnchantRules();
        env.prices.put("ENCHANTMENT_SHARPNESS_6", 100_000.0);
        CompoundTag enchants = new CompoundTag();
        enchants.putInt("sharpness", 6);
        CompoundTag tag = new CompoundTag();
        tag.put("enchantments", enchants);
        EivCalculator.Subject book = new EivCalculator.Subject("ENCHANTMENT_SHARPNESS_6", "ENCHANTED_BOOK", "ENCHANTMENT_SHARPNESS_6", tag, List.of(), "Enchanted Book");
        EivCalculator.Result r = new EivCalculator(env, options()).calculate(book);
        assertEquals(100_000.0, r.base());
        assertEquals(100_000.0, r.total(), "nicht doppelt: der Grundpreis geht nicht in die Summe");
    }
}
