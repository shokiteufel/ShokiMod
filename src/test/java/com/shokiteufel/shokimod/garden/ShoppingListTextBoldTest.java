package com.shokiteufel.shokimod.garden;

import com.shokiteufel.shokimod.render.hud.HudNumbers;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HUD > Bold numbers in der Einkaufsliste: Menge und Preise fett, nur wenn der Aufrufer es ueber die neue Schrift-Funktion verlangt - ohne sie (und mit dem
 * Schalter aus) ist der Text Byte fuer Byte der alte.
 */
class ShoppingListTextBoldTest {

    private static final Map<String, Double> PRICES = Map.of("Enchanted Carrot", 1000.0, "Enchanted Cocoa Bean", 2500.0);
    private static final ToDoubleFunction<String> PRICE = name -> PRICES.getOrDefault(name, -1.0);
    private static final DoubleFunction<String> FORMAT = value -> Long.toString(Math.round(value));
    private static final DoubleFunction<String> BOLD_FORMAT = value -> HudNumbers.bolden(FORMAT.apply(value));

    private static VisitorBook sample() {
        VisitorBook book = new VisitorBook();
        List<String> lore = Arrays.asList("Items Required:", "Enchanted Carrot x3", "Enchanted Cocoa Bean x2");
        VisitorBook.Entry entry = book.getOrCreate("Jacob");
        entry.applyOffer(lore, VisitorOffer.parse(lore), PRICE, 5.0, new com.shokiteufel.shokimod.data.ModConfig.GardenRewardWarningCategory());
        book.getOrCreate("Anita");
        return book;
    }

    @Test
    void withoutAStyleTheTextIsTheOldOne() {
        ShoppingListText.Built old = ShoppingListText.build(sample(), true, PRICE, FORMAT);
        ShoppingListText.Built identity = ShoppingListText.build(sample(), true, PRICE, FORMAT, UnaryOperator.identity());
        assertEquals(old.lines(), identity.lines());
        assertEquals(" §7- §fEnchanted Carrot §ex3 §7(§63000§7)", old.lines().get(1));
    }

    @Test
    void amountsAndPricesAreBoldAndNamesAreNot() {
        ShoppingListText.Built bold = ShoppingListText.build(sample(), true, PRICE, BOLD_FORMAT, HudNumbers::bolden);
        assertEquals(List.of(
                "§7Visitor Shopping List: §7(§6§l8000§7)",
                " §7- §fEnchanted Carrot §ex§l3 §7(§6§l3000§7)",
                " §7- §fEnchanted Cocoa Bean §ex§l2 §7(§6§l5000§7)",
                "",
                "§e1 §7New Visitor:",
                " §7- §fAnita",
                "",
                "§e1 §7Visitor:",
                " §7- §fJacob"), bold.lines());
    }

    @Test
    void theClickMapDoesNotMoveWhenTheTextIsBold() {
        ShoppingListText.Built plain = ShoppingListText.build(sample(), true, PRICE, FORMAT);
        ShoppingListText.Built bold = ShoppingListText.build(sample(), true, PRICE, BOLD_FORMAT, HudNumbers::bolden);
        assertEquals(plain.lines().size(), bold.lines().size());
        assertEquals(plain.visitorAt(), bold.visitorAt());
        assertEquals(plain.itemAt(), bold.itemAt());
    }

    @Test
    void withoutPricesOnlyTheAmountIsBold() {
        ShoppingListText.Built bold = ShoppingListText.build(sample(), false, PRICE, BOLD_FORMAT, HudNumbers::bolden);
        assertEquals("§7Visitor Shopping List:", bold.lines().get(0));
        assertEquals(" §7- §fEnchanted Carrot §ex§l3", bold.lines().get(1));
    }
}
