package com.shokiteufel.shokimod.garden;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleFunction;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Text der Einkaufsliste (Summe, Gegenstaende mit Menge und Preis, neue und bekannte Besucher) und wo sie steht.
 */
class ShoppingListTextTest {

    private static final Map<String, Double> PRICES = Map.of("Enchanted Carrot", 1000.0, "Enchanted Cocoa Bean", 2500.0);
    private static final ToDoubleFunction<String> PRICE = name -> PRICES.getOrDefault(name, -1.0);
    private static final DoubleFunction<String> FORMAT = value -> Long.toString(Math.round(value));

    private static void learn(VisitorBook book, String name, String... itemLines) {
        List<String> lore = new java.util.ArrayList<>();
        lore.add("Items Required:");
        lore.addAll(Arrays.asList(itemLines));
        VisitorBook.Entry entry = book.getOrCreate(name);
        entry.applyOffer(lore, VisitorOffer.parse(lore), PRICE, 5.0, new com.shokiteufel.shokimod.data.ModConfig.GardenRewardWarningCategory());
    }

    private static VisitorBook sample() {
        VisitorBook book = new VisitorBook();
        learn(book, "Jacob", "Enchanted Carrot x3", "Enchanted Cocoa Bean x2");
        learn(book, "Spaceman", "Enchanted Carrot x5");
        book.getOrCreate("Anita");
        return book;
    }

    @Test
    void theListShowsItemsPricesAndTheVisitors() {
        ShoppingListText.Built built = ShoppingListText.build(sample(), true, PRICE, FORMAT);
        assertEquals(List.of(
                "§7Visitor Shopping List: §7(§613000§7)",
                " §7- §fEnchanted Carrot §ex8 §7(§68000§7)",
                " §7- §fEnchanted Cocoa Bean §ex2 §7(§65000§7)",
                "",
                "§e1 §7New Visitor:",
                " §7- §fAnita",
                "",
                "§e2 §7Visitors:",
                " §7- §fJacob",
                " §7- §fSpaceman"), built.lines());
    }

    @Test
    void everyLineKnowsWhereAClickGoes() {
        ShoppingListText.Built built = ShoppingListText.build(sample(), true, PRICE, FORMAT);
        assertEquals(built.lines().size(), built.visitorAt().size());
        assertEquals(built.lines().size(), built.itemAt().size());
        assertEquals(Arrays.asList(null, null, null, null, null, "Anita", null, null, "Jacob", "Spaceman"), built.visitorAt());
        assertEquals(Arrays.asList(null, "Enchanted Carrot", "Enchanted Cocoa Bean", null, null, null, null, null, null, null), built.itemAt());
    }

    @Test
    void withoutPricesThereAreNoPricesAndNoSum() {
        ShoppingListText.Built built = ShoppingListText.build(sample(), false, PRICE, FORMAT);
        assertEquals("§7Visitor Shopping List:", built.lines().get(0));
        assertEquals(" §7- §fEnchanted Carrot §ex8", built.lines().get(1));
    }

    @Test
    void anItemWithoutAPriceHasNoneAndAddsNothingToTheSum() {
        VisitorBook book = new VisitorBook();
        learn(book, "Jacob", "Enchanted Carrot x3", "Mystery Item x1");
        ShoppingListText.Built built = ShoppingListText.build(book, true, PRICE, FORMAT);
        assertEquals("§7Visitor Shopping List: §7(§63000§7)", built.lines().get(0));
        assertEquals(" §7- §fMystery Item §ex1", built.lines().get(2), "ohne Preis steht dort keiner");
    }

    @Test
    void anIgnoredVisitorIsStruckThroughAndLeftOutOfTheSum() {
        VisitorBook book = sample();
        book.toggleIgnored("Spaceman");
        ShoppingListText.Built built = ShoppingListText.build(book, true, PRICE, FORMAT);
        // Nur Jacob zaehlt: drei Karotten und zwei Kakao
        assertEquals("§7Visitor Shopping List: §7(§68000§7)", built.lines().get(0));
        assertEquals(" §7- §fEnchanted Carrot §ex3 §7(§63000§7)", built.lines().get(1));
        assertTrue(built.lines().contains(" §7- §7§mSpaceman"), "durchgestrichen, aber noch da");
    }

    @Test
    void anAcceptedVisitorIsGoneFromTheList() {
        VisitorBook book = sample();
        book.get("Jacob").status(VisitorBook.Status.ACCEPTED);
        ShoppingListText.Built built = ShoppingListText.build(book, true, PRICE, FORMAT);
        assertFalse(built.lines().contains(" §7- §fJacob"));
        assertEquals(" §7- §fEnchanted Carrot §ex5 §7(§65000§7)", built.lines().get(1));
        assertTrue(built.lines().contains("§e1 §7Visitor:"), "ein Besucher: Einzahl");
    }

    @Test
    void onlyNewVisitorsGiveNoShoppingListJustTheirNames() {
        VisitorBook book = new VisitorBook();
        book.getOrCreate("Anita");
        book.getOrCreate("Jacob");
        ShoppingListText.Built built = ShoppingListText.build(book, true, PRICE, FORMAT);
        assertEquals(List.of("§e2 §7New Visitors:", " §7- §fAnita", " §7- §fJacob"), built.lines(), "keine Kopfzeile, keine Leerzeile davor");
    }

    @Test
    void anEmptyBookGivesAnEmptyList() {
        assertTrue(ShoppingListText.build(new VisitorBook(), true, PRICE, FORMAT).isEmpty());
    }

    @Test
    void theListStandsInTheGardenAndOnlyNearTheBarnWhenAsked() {
        // Garden: ueberall, mit "Only when Close" nur auf dem Plot der Scheune
        assertTrue(ShoppingListText.locationAllows(false, false, false, "Garden", null, false));
        assertFalse(ShoppingListText.locationAllows(false, false, true, "Garden", null, false));
        assertTrue(ShoppingListText.locationAllows(false, false, true, "Garden", null, true));
        assertTrue(ShoppingListText.locationAllows(false, false, false, "The Garden", null, false));
    }

    @Test
    void theHubZonesAndTheFarmingIslandsNeedTheirSwitches() {
        assertTrue(ShoppingListText.locationAllows(true, false, false, "Hub", "Bazaar Alley", false));
        assertFalse(ShoppingListText.locationAllows(false, false, false, "Hub", "Bazaar Alley", false));
        assertFalse(ShoppingListText.locationAllows(true, false, false, "Hub", "Farm", false));
        assertTrue(ShoppingListText.locationAllows(false, true, false, "Hub", "Farm", false));
        assertFalse(ShoppingListText.locationAllows(true, true, false, "Hub", "Village", false));
        assertFalse(ShoppingListText.locationAllows(true, true, false, "Hub", null, false));
        assertTrue(ShoppingListText.locationAllows(false, true, false, "The Farming Islands", null, false));
        assertFalse(ShoppingListText.locationAllows(true, false, false, "The Farming Islands", null, false));
        // Anderswo nie, auch nicht mit "Only when Close"
        assertFalse(ShoppingListText.locationAllows(true, true, false, "Private Island", null, true));
        assertFalse(ShoppingListText.locationAllows(true, true, false, null, null, false));
    }

    @Test
    void theZoneIsOnlyReadInTheHubWhenOneOfItsSwitchesIsOn() {
        assertTrue(ShoppingListText.needsZone(true, false, "Hub"));
        assertTrue(ShoppingListText.needsZone(false, true, "Hub"));
        assertFalse(ShoppingListText.needsZone(false, false, "Hub"));
        assertFalse(ShoppingListText.needsZone(true, true, "Garden"));
        assertFalse(ShoppingListText.needsZone(true, true, null));
    }
}
