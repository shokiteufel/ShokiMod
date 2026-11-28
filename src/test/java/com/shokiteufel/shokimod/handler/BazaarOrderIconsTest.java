package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inventory > Bazaar > Order icons (2.6.10): welches kleine Zeichen ein Auftrag bekommt - die Regeln von Skyblockers BazaarHelper. */
class BazaarOrderIconsTest {

    private static int icon(String... lore) {
        return BazaarHelpers.iconFor(List.of(lore));
    }

    // ---- gekauft und zum Abholen

    @Test
    void partOfItWasBoughtAndWaitsToBePickedUp() {
        assertEquals(WindowHighlights.ICON_PART, icon("Order amount: 64x", "Filled: 32/64 50%", "Price per unit: 5.0 coins", "", "You have 32 items to claim!", "",
                "Click to claim!"));
    }

    @Test
    void aFullyFilledOrderGetsTheCheckMark() {
        assertEquals(WindowHighlights.ICON_FULL, icon("Order amount: 64x", "Filled: 64/64 100%!", "Price per unit: 5.0 coins", "", "You have 64 items to claim!", "",
                "Click to claim!"));
    }

    @Test
    void thePercentIsReadWithAndWithoutBracketsAndMark() {
        assertEquals(WindowHighlights.ICON_PART, icon("Filled: 1,200/2,000 (62%)", "Click to claim!"));
        assertEquals(WindowHighlights.ICON_PART, icon("Filled: 12/64 18%", "Click to claim!"));
        assertEquals(WindowHighlights.ICON_PART, icon("Filled: 63/64 98.4%", "Click to claim!"));
        assertEquals(WindowHighlights.ICON_FULL, icon("Filled: 64/64 (100%)!", "Click to claim!"));
        assertEquals(WindowHighlights.ICON_FULL, icon("Filled: 64/64 100%", "Click to claim!"));
    }

    @Test
    void emptyLinesAfterTheClaimLineDoNotMatter() {
        assertEquals(WindowHighlights.ICON_FULL, icon("Filled: 64/64 100%!", "Click to claim!", "", ""));
    }

    @Test
    void withNothingToPickUpThereIsNoFilledSymbol() {
        // Teilweise gekauft, aber noch nichts abzuholen: die letzte Zeile ist nicht "Click to claim!"
        assertEquals(0, icon("Filled: 0/64 0%", "Click to view options!"));
        assertEquals(0, icon("Filled: 32/64 50%"));
    }

    @Test
    void theClaimLineMustBeTheLastOne() {
        assertEquals(0, icon("Filled: 32/64 50%", "Click to claim!", "Something after it"));
    }

    // ---- Ablauf

    @Test
    void anExpiredOrderGetsTheRedClock() {
        assertEquals(WindowHighlights.ICON_EXPIRED, icon("Order amount: 64x", "Expired!", "Click to view options!"));
    }

    @Test
    void anOrderThatExpiresSoonGetsTheYellowClock() {
        assertEquals(WindowHighlights.ICON_EXPIRING, icon("Order amount: 64x", "Expires in: 6d 23h", "Click to view options!"));
        assertEquals(WindowHighlights.ICON_EXPIRING, icon("Expires in 5m"));
    }

    @Test
    void whatWaitsToBePickedUpBeatsTheExpiry() {
        // Wie bei Skyblocker: erst "Filled", dann der Ablauf
        assertEquals(WindowHighlights.ICON_FULL, icon("Filled: 64/64 100%!", "Expires in: 1h", "Click to claim!"));
        assertEquals(WindowHighlights.ICON_PART, icon("Filled: 10/64 15%", "Expired!", "Click to claim!"));
    }

    @Test
    void anExpiredOrderWithNothingToPickUpIsExpired() {
        assertEquals(WindowHighlights.ICON_EXPIRED, icon("Filled: 10/64 15%", "Expired!", "Click to view options!"));
        assertEquals(WindowHighlights.ICON_EXPIRING, icon("Filled: 10/64 15%", "Expires in: 2h", "Click to view options!"));
    }

    @Test
    void anExpiredOrderBeatsOneThatOnlyExpiresSoon() {
        assertEquals(WindowHighlights.ICON_EXPIRED, icon("Expires in: 1h", "Expired!"));
    }

    // ---- nichts

    @Test
    void anythingElseGetsNoSymbol() {
        assertEquals(0, icon());
        assertEquals(0, icon("Order amount: 64x", "Price per unit: 5.0 coins", "Click to view options!"));
        assertEquals(0, BazaarHelpers.iconFor(null));
        assertEquals(0, BazaarHelpers.iconFor(List.of()));
        assertEquals(0, icon("Filled: 1.2.3/64 1.2.3%", "Click to claim!"), "eine Zahl, die keine ist, gibt kein Zeichen und keinen Fehler");
    }

    // ---- welche Plaetze ueberhaupt

    @Test
    void onlyOrdersAreChecked() {
        assertTrue(BazaarHelpers.isOrderName("BUY Wheat"));
        assertTrue(BazaarHelpers.isOrderName("SELL Enchanted Coal"));
        assertTrue(BazaarHelpers.isOrderName("  BUY Wheat  "));
        assertFalse(BazaarHelpers.isOrderName("Go Back"));
        assertFalse(BazaarHelpers.isOrderName("Close"));
        assertFalse(BazaarHelpers.isOrderName(" "));
        assertFalse(BazaarHelpers.isOrderName(null));
        assertFalse(BazaarHelpers.isOrderName("Buy Wheat"), "die Schreibweise von Hypixel ist BUY und SELL");
    }
}
