package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.util.ShopValue.Cost;
import com.shokiteufel.shokimod.util.ShopValue.Currency;
import com.shokiteufel.shokimod.util.ShopValue.Offer;
import com.shokiteufel.shokimod.util.ShopValue.Prices;
import com.shokiteufel.shokimod.util.ShopValue.Row;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NPC Shop Values: die Fenster der Haendler, das Lesen der Kosten und die Rechnung je Waehrung - alles ohne Spielklassen */
class ShopValueTest {

    /** Preise aus einer Tabelle: Kennung -> Wert, Name -> Wert; was fehlt, ist unbekannt */
    private record Table(Map<String, Double> byId, Map<String, Double> byName) implements Prices {
        @Override
        public double valueOfItem(List<String> candidates) {
            for (String id : candidates) {
                Double v = byId.get(id);
                if (v != null) return v;
            }
            return -1;
        }

        @Override
        public double valueOfName(String name) {
            Double v = byName.get(name);
            return v == null ? -1 : v;
        }
    }

    private static Offer offer(String name, int amount, String id, Cost... costs) {
        return new Offer(name, "§f", amount, List.of(id), List.of(costs));
    }

    private static final Prices PRICES = new Table(
            Map.of("SEEDS", 100.0, "WART", 4_000.0, "TRAP", 10_000.0, "LOG_ITEM", 50.0),
            Map.of("Enchanted Fig Log", 160.0, "Figstone", 5_000.0));

    // ------------------------------------------------------------------ Fenster

    @Test
    void thePageNumberInFrontOfTheTitleIsIgnored() {
        assertEquals("SkyMart Farming Essentials", ShopValue.stripPage("(1/2) SkyMart Farming Essentials"));
        assertEquals("Fear Mongerer", ShopValue.stripPage("Fear Mongerer"));
        assertEquals("Fear Mongerer", ShopValue.stripPage("§aFear Mongerer"));
        assertEquals("", ShopValue.stripPage(null));
    }

    @Test
    void everyShopOfTheFiveIsRecognisedByItsTitle() {
        assertEquals(List.of(Currency.COPPER), ShopValue.shopOf("(1/2) SkyMart Farming Essentials").currencies());
        assertEquals(List.of(Currency.COPPER), ShopValue.shopOf("SkyMart Pest Control").currencies());
        assertEquals(List.of(Currency.PESTS), ShopValue.shopOf("Pesthunter's Wares").currencies());
        assertEquals(List.of(Currency.GREEN_CANDY, Currency.PURPLE_CANDY), ShopValue.shopOf("Fear Mongerer").currencies());
        assertEquals(List.of(Currency.AGATHA_COUPON), ShopValue.shopOf("Agatha's Shop").currencies());
        assertEquals(List.of(Currency.MIRIA_COUPON), ShopValue.shopOf("Miria's Shop").currencies());
    }

    @Test
    void aCurlyApostropheInTheTitleDoesNotMatter() {
        assertNotNull(ShopValue.shopOf("Agatha’s Shop"));
        assertNotNull(ShopValue.shopOf("Pesthunter’s Wares"));
    }

    @Test
    void anyOtherWindowIsNoShop() {
        assertNull(ShopValue.shopOf("Bazaar ➜ Farming"));
        assertNull(ShopValue.shopOf("Chest"));
        assertNull(ShopValue.shopOf(""));
        assertNull(ShopValue.shopOf(null));
        assertNull(ShopValue.shopOf("Agatha's Power"));
    }

    // ------------------------------------------------------------------ Waehrungen

    @Test
    void aCurrencyMatchesWithAndWithoutPluralAndApostrophe() {
        assertTrue(Currency.COPPER.matches("Copper"));
        assertTrue(Currency.COPPER.matches("copper"));
        assertTrue(Currency.PESTS.matches("Pests"));
        assertTrue(Currency.PESTS.matches("Pest"));
        assertTrue(Currency.GREEN_CANDY.matches("Green Candy"));
        assertTrue(Currency.PURPLE_CANDY.matches("Purple Candy"));
        assertTrue(Currency.AGATHA_COUPON.matches("Agatha's Coupon"));
        assertTrue(Currency.AGATHA_COUPON.matches("Agatha's Coupons"));
        assertTrue(Currency.AGATHA_COUPON.matches("§eAgathas Coupon"));
        assertTrue(Currency.MIRIA_COUPON.matches("Miria's Coupon"));
    }

    @Test
    void aCurrencyDoesNotMatchAnotherOne() {
        assertFalse(Currency.GREEN_CANDY.matches("Purple Candy"));
        assertFalse(Currency.AGATHA_COUPON.matches("Miria's Coupon"));
        assertFalse(Currency.COPPER.matches("Enchanted Copper"));
        assertFalse(Currency.COPPER.matches(null));
    }

    // ------------------------------------------------------------------ Kosten lesen

    @Test
    void theCostLinesBelowTheHeaderAreRead() {
        List<Cost> costs = ShopValue.readCosts(List.of("Some text", "", "Cost", "250 Copper", "", "Click to trade!"));
        assertEquals(List.of(new Cost("Copper", 250)), costs);
    }

    @Test
    void severalCostLinesAreAllRead() {
        List<Cost> costs = ShopValue.readCosts(List.of("Cost", "1,500 Agatha's Coupon", "Enchanted Fig Log x24", "Figstone x3", "", "Click to trade!"));
        assertEquals(List.of(new Cost("Agatha's Coupon", 1500), new Cost("Enchanted Fig Log", 24), new Cost("Figstone", 3)), costs);
    }

    @Test
    void theAmountMayComeBeforeOrBehindTheName() {
        assertEquals(new Cost("Green Candy", 10), ShopValue.readCostLine("10x Green Candy"));
        assertEquals(new Cost("Green Candy", 10), ShopValue.readCostLine("10 Green Candy"));
        assertEquals(new Cost("Green Candy", 10), ShopValue.readCostLine("Green Candy x10"));
        assertEquals(new Cost("Green Candy", 1), ShopValue.readCostLine("Green Candy"));
        assertEquals(new Cost("Pests", 5_000), ShopValue.readCostLine("§25,000 Pests"));
    }

    @Test
    void aCostOnTheHeaderLineItselfIsRead() {
        assertEquals(List.of(new Cost("Copper", 80)), ShopValue.readCosts(List.of("Cost: 80 Copper")));
        assertEquals(List.of(new Cost("Copper", 80)), ShopValue.readCosts(List.of("Costs: 80 Copper")));
    }

    @Test
    void theLastCostHeaderCounts() {
        List<String> lore = List.of("Cost", "1 Copper", "", "Cost", "2 Copper", "");
        assertEquals(List.of(new Cost("Copper", 2)), ShopValue.readCosts(lore));
    }

    @Test
    void aCostListEndsAtTheBlankLineOrAtAClickLine() {
        assertEquals(List.of(new Cost("Copper", 7)), ShopValue.readCosts(List.of("Cost", "7 Copper", "Click to trade!", "9 Copper")));
        assertEquals(List.of(new Cost("Copper", 7)), ShopValue.readCosts(List.of("Cost", "7 Copper", "", "9 Copper")));
    }

    @Test
    void withoutACostLineThereAreNoCosts() {
        assertTrue(ShopValue.readCosts(List.of("Mana Cost: 100", "Some ability")).isEmpty());
        assertTrue(ShopValue.readCosts(List.of()).isEmpty());
        assertTrue(ShopValue.readCosts(null).isEmpty());
    }

    @Test
    void aLineWithNoReadableAmountIsLeftOut() {
        assertTrue(ShopValue.readCosts(List.of("Cost", "0 Copper")).isEmpty());
        assertNull(ShopValue.readCostLine(""));
        assertNull(ShopValue.readCostLine(null));
    }

    // ------------------------------------------------------------------ Rechnung

    @Test
    void theBestCoinsPerCurrencyComeFirst() {
        List<Row> rows = ShopValue.rank(Currency.COPPER, List.of(
                offer("Seeds", 1, "SEEDS", new Cost("Copper", 50)),          // 100 / 50 = 2
                offer("Wart", 1, "WART", new Cost("Copper", 400)),           // 4000 / 400 = 10
                offer("Trap", 1, "TRAP", new Cost("Copper", 2_000))), PRICES, true, 10); // 5
        assertEquals(List.of("Wart", "Trap", "Seeds"), rows.stream().map(Row::name).toList());
        assertEquals(10.0, rows.get(0).ratio(), 1e-9);
        assertEquals(400, rows.get(0).cost());
    }

    @Test
    void theListHoldsAtMostTheChosenNumber() {
        Offer[] many = new Offer[15];
        for (int i = 0; i < many.length; i++) many[i] = offer("Item " + (char) ('A' + i), 1, "SEEDS", new Cost("Copper", 10 + i));
        assertEquals(10, ShopValue.rank(Currency.COPPER, List.of(many), PRICES, true, 10).size());
        assertEquals(3, ShopValue.rank(Currency.COPPER, List.of(many), PRICES, true, 3).size());
        assertTrue(ShopValue.rank(Currency.COPPER, List.of(many), PRICES, true, 0).isEmpty());
    }

    @Test
    void anItemWithoutAnyPriceIsNotListed() {
        List<Row> rows = ShopValue.rank(Currency.COPPER, List.of(
                offer("Unknown", 1, "NOT_IN_ANY_LIST", new Cost("Copper", 10)),
                offer("Seeds", 1, "SEEDS", new Cost("Copper", 10))), PRICES, true, 10);
        assertEquals(List.of("Seeds"), rows.stream().map(Row::name).toList());
    }

    @Test
    void anItemWithoutACostLineIsNotListed() {
        List<Row> rows = ShopValue.rank(Currency.COPPER, List.of(offer("Glass pane", 1, "SEEDS")), PRICES, true, 10);
        assertTrue(rows.isEmpty());
    }

    @Test
    void theAmountOfTheStackMultipliesTheValue() {
        List<Row> rows = ShopValue.rank(Currency.COPPER, List.of(offer("Seeds", 16, "SEEDS", new Cost("Copper", 80))), PRICES, true, 10);
        assertEquals(1, rows.size());
        assertEquals(20.0, rows.get(0).ratio(), 1e-9);   // 16 * 100 / 80
        assertEquals(16, rows.get(0).amount());
    }

    @Test
    void theMaterialsAreTakenOffTheValue() {
        // 10000 - 24 * 160 = 6160 -> 6160 / 100 = 61.6
        Offer o = offer("Trap", 1, "TRAP", new Cost("Agatha's Coupon", 100), new Cost("Enchanted Fig Log", 24));
        List<Row> rows = ShopValue.rank(Currency.AGATHA_COUPON, List.of(o), PRICES, true, 10);
        assertEquals(1, rows.size());
        assertEquals(61.6, rows.get(0).ratio(), 1e-9);
    }

    @Test
    void withTheMaterialsSwitchedOffTheWholeValueCounts() {
        Offer o = offer("Trap", 1, "TRAP", new Cost("Agatha's Coupon", 100), new Cost("Enchanted Fig Log", 24));
        List<Row> rows = ShopValue.rank(Currency.AGATHA_COUPON, List.of(o), PRICES, false, 10);
        assertEquals(100.0, rows.get(0).ratio(), 1e-9);
    }

    @Test
    void aMaterialWithoutAPriceLeavesTheItemOut() {
        Offer o = offer("Trap", 1, "TRAP", new Cost("Agatha's Coupon", 100), new Cost("Mystery Stone", 2));
        assertTrue(ShopValue.rank(Currency.AGATHA_COUPON, List.of(o), PRICES, true, 10).isEmpty());
        // ohne Abzug stoert er nicht
        assertEquals(1, ShopValue.rank(Currency.AGATHA_COUPON, List.of(o), PRICES, false, 10).size());
    }

    @Test
    void coinsInTheCostsAreTakenOffToo() {
        Offer o = offer("Trap", 1, "TRAP", new Cost("Pests", 100), new Cost("Coins", 4_000));
        assertEquals(60.0, ShopValue.rank(Currency.PESTS, List.of(o), PRICES, true, 10).get(0).ratio(), 1e-9);
    }

    @Test
    void anItemWorthLessThanItsMaterialsIsNotListed() {
        Offer o = offer("Seeds", 1, "SEEDS", new Cost("Copper", 10), new Cost("Figstone", 1));
        assertTrue(ShopValue.rank(Currency.COPPER, List.of(o), PRICES, true, 10).isEmpty());
    }

    @Test
    void theTwoCandyListsAreKeptApart() {
        Offer green = offer("Seeds", 1, "SEEDS", new Cost("Green Candy", 10));
        Offer purple = offer("Wart", 1, "WART", new Cost("Purple Candy", 20));
        List<Offer> both = List.of(green, purple);
        assertEquals(List.of("Seeds"), ShopValue.rank(Currency.GREEN_CANDY, both, PRICES, true, 10).stream().map(Row::name).toList());
        assertEquals(List.of("Wart"), ShopValue.rank(Currency.PURPLE_CANDY, both, PRICES, true, 10).stream().map(Row::name).toList());
    }

    @Test
    void anItemThatCostsAnotherCurrencyAsWellCannotBeRatedAndIsLeftOut() {
        Offer o = offer("Wart", 1, "WART", new Cost("Green Candy", 10), new Cost("Purple Candy", 5));
        assertTrue(ShopValue.rank(Currency.GREEN_CANDY, List.of(o), PRICES, true, 10).isEmpty());
        assertEquals(1, ShopValue.rank(Currency.GREEN_CANDY, List.of(o), PRICES, false, 10).size());
    }

    @Test
    void equalRatiosAreOrderedByName() {
        List<Row> rows = ShopValue.rank(Currency.COPPER, List.of(
                offer("Bravo", 1, "SEEDS", new Cost("Copper", 10)),
                offer("Alpha", 1, "SEEDS", new Cost("Copper", 10))), PRICES, true, 10);
        assertEquals(List.of("Alpha", "Bravo"), rows.stream().map(Row::name).toList());
    }

    @Test
    void badInputGivesAnEmptyList() {
        assertTrue(ShopValue.rank(null, List.of(), PRICES, true, 10).isEmpty());
        assertTrue(ShopValue.rank(Currency.COPPER, null, PRICES, true, 10).isEmpty());
        assertTrue(ShopValue.rank(Currency.COPPER, List.of(), null, true, 10).isEmpty());
    }

    // ------------------------------------------------------------------ Anzeige

    @Test
    void theHeadingNamesTheCurrency() {
        assertEquals("§eCoins per Copper§f:", ShopValue.heading(Currency.COPPER));
        assertEquals("§eCoins per Agatha's Coupon§f:", ShopValue.heading(Currency.AGATHA_COUPON));
        assertEquals("§eCoins per Green Candy§f:", ShopValue.heading(Currency.GREEN_CANDY));
    }

    @Test
    void aLineShowsRankNameCostAndRatio() {
        Row row = new Row("Mutant Nether Wart", "§9", 1, 1_500, 420.0);
        assertEquals("§71. §9Mutant Nether Wart §7(§c1,500§7)§f: §6420", ShopValue.line(Currency.COPPER, row, 1, false));
    }

    @Test
    void anAmountOfTwoOrMoreShowsInTheLineAndOneDoesNot() {
        Row many = new Row("Enchanted Seeds", "§a", 16, 300, 310.0);
        assertTrue(ShopValue.line(Currency.COPPER, many, 2, false).contains("Enchanted Seeds §8x16"));
        Row one = new Row("Compost", "§a", 1, 120, 95.0);
        assertFalse(ShopValue.line(Currency.COPPER, one, 3, false).contains("x1"));
    }

    @Test
    void theRatioCanBeBold() {
        Row row = new Row("Wart", "§f", 1, 10, 1_234.0);
        assertTrue(ShopValue.line(Currency.PESTS, row, 1, true).endsWith("§6§l1.2k"));
        assertTrue(ShopValue.line(Currency.PESTS, row, 1, false).endsWith("§61.2k"));
    }
}
