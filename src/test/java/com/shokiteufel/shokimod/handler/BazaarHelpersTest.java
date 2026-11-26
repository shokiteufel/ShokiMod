package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.BazaarPrice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wunsch des Users (26.11.): die Hilfen im Basar wie bei SkyHanni - Purchase helper, Order helper, Best sell method und die Zwischenablage nach einem
 * abgebrochenen Kaufauftrag, alles unter Inventory > Bazaar.
 */
class BazaarHelpersTest {

    @BeforeEach
    void frisch() {
        BazaarHelpers.resetForTest();
        ModConfig.INSTANCE = new ModConfig();
    }

    // ------------------------------------------------------------------ Auftraege

    @Test
    void aBuyOrderIsReadFromItsNameAndLines() {
        BazaarHelpers.Order o = BazaarHelpers.parseOrder("BUY Wheat", List.of(
                "Order amount: 50x", "Price per unit: 521.7 coins", "", "Filled: 26/50 (52%)", "You have 6 items to claim!"));
        assertNotNull(o);
        assertTrue(o.buy());
        assertEquals("Wheat", o.product());
        assertEquals(50, o.amount());
        assertEquals(521.7, o.pricePerUnit(), 1e-9);
        assertFalse(o.full());
        assertEquals(6, o.claimable());
    }

    @Test
    void aSellOfferWithBigNumbersAndAFullOrder() {
        BazaarHelpers.Order o = BazaarHelpers.parseOrder("SELL Enchanted Coal", List.of(
                "Offer amount: 2,000x", "Price per unit: 2,082.3 coins", "Filled: 2k/2k 100%!"));
        assertNotNull(o);
        assertFalse(o.buy());
        assertEquals("Enchanted Coal", o.product());
        assertEquals(2_000, o.amount());
        assertEquals(2_082.3, o.pricePerUnit(), 1e-9);
        assertTrue(o.full(), "100%! heisst fertig");
    }

    @Test
    void withoutAnyFilledLineNothingWasTradedYet() {
        BazaarHelpers.Order o = BazaarHelpers.parseOrder("BUY Coal", List.of("Order amount: 10x", "Price per unit: 4.4 coins"));
        assertNotNull(o);
        assertFalse(o.full());
        assertEquals(0, o.claimable());
    }

    @Test
    void anOrderWithoutAmountOrPriceIsNoOrder() {
        assertNull(BazaarHelpers.parseOrder("BUY Coal", List.of("Order amount: 10x")));
        assertNull(BazaarHelpers.parseOrder("BUY Coal", List.of("Price per unit: 4.4 coins")));
        assertNull(BazaarHelpers.parseOrder("Go Back", List.of("To Bazaar")));
        assertNull(BazaarHelpers.parseOrder(null, List.of()));
    }

    @Test
    void aFullOrderIsGreenWhateverThePrices() {
        BazaarHelpers.Order full = new BazaarHelpers.Order(true, "Coal", 10, true, 10, 1.0);
        assertEquals(WindowHighlights.GREEN, BazaarHelpers.colourFor(full, null));
        assertEquals(WindowHighlights.GREEN, BazaarHelpers.colourFor(full, new BazaarPrice(10, 10, 12, 10)));
    }

    @Test
    void aBuyOrderBelowTheBestBuyOrderIsOutbid() {
        BazaarPrice price = new BazaarPrice(100, 110, 112, 105);
        assertEquals(WindowHighlights.GOLD, BazaarHelpers.colourFor(new BazaarHelpers.Order(true, "Coal", 10, false, 0, 104.0), price));
        assertEquals(0, BazaarHelpers.colourFor(new BazaarHelpers.Order(true, "Coal", 10, false, 0, 105.0), price), "gleichauf ist nicht ueberboten");
        assertEquals(0, BazaarHelpers.colourFor(new BazaarHelpers.Order(true, "Coal", 10, false, 0, 106.0), price));
    }

    @Test
    void aSellOfferAboveTheCheapestOfferIsOutbid() {
        BazaarPrice price = new BazaarPrice(100, 110, 112, 105);
        assertEquals(WindowHighlights.GOLD, BazaarHelpers.colourFor(new BazaarHelpers.Order(false, "Coal", 10, false, 0, 113.0), price));
        assertEquals(0, BazaarHelpers.colourFor(new BazaarHelpers.Order(false, "Coal", 10, false, 0, 112.0), price));
        assertEquals(0, BazaarHelpers.colourFor(new BazaarHelpers.Order(false, "Coal", 10, false, 0, 111.0), price));
    }

    @Test
    void withoutPricesAnOpenOrderIsNotMarked() {
        assertEquals(0, BazaarHelpers.colourFor(new BazaarHelpers.Order(true, "Coal", 10, false, 0, 1.0), null));
        assertEquals(0, BazaarHelpers.colourFor(new BazaarHelpers.Order(true, "Coal", 10, false, 0, 1.0), new BazaarPrice(0, 0, 0, 0)));
    }

    // ------------------------------------------------------------------ Suche und Kauf

    @Test
    void theSearchIsReadFromTheCommand() {
        assertEquals("coal", BazaarHelpers.searchIn("bz coal"));
        assertEquals("Enchanted Coal", BazaarHelpers.searchIn("bazaar Enchanted Coal"));
        assertEquals("Wheat", BazaarHelpers.searchIn("BZ   Wheat  "));
        assertNull(BazaarHelpers.searchIn("bz"));
        assertNull(BazaarHelpers.searchIn("bazaarfoo"));
        assertNull(BazaarHelpers.searchIn("warp hub"));
        assertNull(BazaarHelpers.searchIn(null));
    }

    @Test
    void aListEntryMatchesTheSearchByNameWhateverTheCase() {
        assertTrue(BazaarHelpers.matchesSearch("§aEnchanted Coal", "enchanted coal"));
        assertTrue(BazaarHelpers.matchesSearch("Wheat", " Wheat "));
        assertFalse(BazaarHelpers.matchesSearch("Enchanted Coal", "coal"), "ganzer Name, nicht ein Stueck davon");
        assertFalse(BazaarHelpers.matchesSearch("Coal", null));
        assertFalse(BazaarHelpers.matchesSearch("Coal", ""));
    }

    @Test
    void theBoughtItemOfAChatLineIsRead() {
        assertEquals("Small Storage", BazaarHelpers.transactionItem("[Bazaar] Bought 1x Small Storage for 3,999.5 coins!"));
        assertEquals("Coal", BazaarHelpers.transactionItem("[Bazaar] Sold 1x Coal for 4.2 coins!"));
        assertEquals("Coal", BazaarHelpers.transactionItem("[Bazaar] Buy Order Setup! 1x Coal for 4.4 coins."));
        assertEquals("Spider Essence", BazaarHelpers.transactionItem("[Bazaar] Sell Offer Setup! 447,199x Spider Essence for 486,999,711 coins."));
        assertEquals("Coal", BazaarHelpers.transactionItem("[Bazaar] Order Flipped! 5x Coal for 13.0 coins of total expected profit."));
    }

    @Test
    void otherBazaarLinesAreNoTransaction() {
        assertNull(BazaarHelpers.transactionItem("[Bazaar] Claimed 24x Nessie Shard worth 4.8M coins bought for 202k each!"));
        assertNull(BazaarHelpers.transactionItem("[Bazaar] Cancelled! Refunded 12,345 coins from cancelling Buy Order!"));
        assertNull(BazaarHelpers.transactionItem("Someone: Bought 1x Coal for 4 coins"));
        assertNull(BazaarHelpers.transactionItem(null));
    }

    // ------------------------------------------------------------------ abgebrochener Kaufauftrag

    @Test
    void theMissingAmountIsReadFromTheCancelItem() {
        assertEquals(50, BazaarHelpers.missingAmount(List.of("Cancel your order.", "Refunds 12,345 coins from 50x missing items.")));
        assertEquals(50, BazaarHelpers.missingAmount(List.of("from 50x missing items.")));
        assertEquals(22, BazaarHelpers.missingAmount(List.of("22x missing items.")));
        assertEquals(2_000, BazaarHelpers.missingAmount(List.of("coins from 2,000x missing items.")));
    }

    @Test
    void nothingToCancelHasNoAmount() {
        assertEquals(-1, BazaarHelpers.missingAmount(List.of("Cannot cancel order while there are", "items to claim!")));
        assertEquals(-1, BazaarHelpers.missingAmount(List.of()));
    }

    @Test
    void theRefundIsReadFromTheCancelLine() {
        assertEquals("12,345", BazaarHelpers.refundedIn("[Bazaar] Cancelled! Refunded 12,345 coins from cancelling Buy Order!"));
        assertNull(BazaarHelpers.refundedIn("[Bazaar] Cancelled! Refunded 12,345 coins from cancelling Sell Offer!"));
        assertNull(BazaarHelpers.refundedIn("Cancelled!"));
        assertNull(BazaarHelpers.refundedIn(null));
    }

    @Test
    void theReorderLineNamesTheItemTheAmountAndTheClick() {
        String text = BazaarHelpers.reorderLine(new BazaarHelpers.Reorder("Wheat", 2_000, "12,345")).getString();
        assertTrue(text.contains("[Click to order again]"));
        assertTrue(text.contains("2,000x Wheat"));
        assertTrue(text.contains("12,345 coins"));
    }

    // ------------------------------------------------------------------ Kasten

    @Test
    void theSellDifferenceIsOfferMinusBidTimesWhatYouCarry() {
        String text = BazaarHelpers.sellDifferenceText("Enchanted Coal", 1_000.0, 900.0, 10);
        assertEquals("§fEnchanted Coal§7 sell difference: §6" + ItemValue.format(1_000.0) + " coins", text);
    }

    @Test
    void carryingNoneSaysNothing() {
        assertNull(BazaarHelpers.sellDifferenceText("Coal", 10.0, 9.0, 0));
        assertNull(BazaarHelpers.sellDifferenceText("Coal", 10.0, 9.0, -3));
    }

    // ------------------------------------------------------------------ Fenster

    @Test
    void theBazaarWindowsAreKnownByTheirTitles() {
        assertTrue(BazaarHelpers.isBazaarTitle("Bazaar ➜ Coal"));
        assertTrue(BazaarHelpers.isBazaarTitle("Bazaar ➜ \"coal\""));
        assertTrue(BazaarHelpers.isBazaarTitle("Your Bazaar Orders"));
        assertTrue(BazaarHelpers.isBazaarTitle("Co-op Bazaar Orders"));
        assertTrue(BazaarHelpers.isBazaarTitle("Order options"));
        assertTrue(BazaarHelpers.isBazaarTitle("How many do you want?"));
        assertFalse(BazaarHelpers.isBazaarTitle("Chest"));
        assertFalse(BazaarHelpers.isBazaarTitle("Auctions Browser"));
        assertFalse(BazaarHelpers.isBazaarTitle(null));
        assertTrue(BazaarHelpers.isOrdersTitle("Your Bazaar Orders"));
        assertFalse(BazaarHelpers.isOrdersTitle("Order options"));
    }

    @Test
    void theListSlotsAreSkyHannis() {
        assertEquals(9, BazaarHelpers.LIST_FIRST);
        assertEquals(44, BazaarHelpers.LIST_LAST);
    }

    @Test
    void everythingIsOffFromTheStartAndTheGateFollows() {
        ModConfig.BazaarHelpersCategory c = ModConfig.INSTANCE.inventory.bazaar;
        assertFalse(c.purchaseHelper);
        assertFalse(c.orderHelper);
        assertFalse(c.bestSellMethod);
        assertFalse(c.cancelledBuyOrderClipboard);
        assertFalse(FeatureGate.bazaarHelpers());
        c.orderHelper = true;
        assertTrue(FeatureGate.bazaarHelpers());
        c.orderHelper = false;
        c.cancelledBuyOrderClipboard = true;
        assertTrue(FeatureGate.bazaarHelpers());
    }
}
