package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.handler.BazaarHelpers.Order;
import com.shokiteufel.shokimod.util.ItemValue.BazaarPrice;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Order prices und der genauere Vergleich beim Order helper (2.4.17). */
class BazaarOrderPricesTest {

    @Test
    void coinsAreWrittenWithOneDecimalBelowAThousand() {
        assertEquals("550", BazaarHelpers.coins(550));
        assertEquals("4.5", BazaarHelpers.coins(4.5));
        assertEquals("694.1", BazaarHelpers.coins(694.1));
        assertEquals("12,346", BazaarHelpers.coins(12_345.6));
        assertEquals("57,128", BazaarHelpers.coins(57_127.6));
    }

    @Test
    void threeLinesWithTheNpcPrice() {
        BazaarPrice p = new BazaarPrice(550, 694, 694, 550);
        assertEquals(List.of("§6Bazaar Buy: §6550", "§6Bazaar Sell: §6694", "§6NPC Sell: §6560"), BazaarHelpers.priceLines(p, 560));
    }

    @Test
    void aMissingNumberLeavesItsLineOut() {
        BazaarPrice onlyOffers = new BazaarPrice(0, 694, 694, 0);
        assertEquals(List.of("§6Bazaar Sell: §6694"), BazaarHelpers.priceLines(onlyOffers, -1));
        assertEquals(List.of("§6NPC Sell: §6560"), BazaarHelpers.priceLines(null, 560));
        assertEquals(List.of(), BazaarHelpers.priceLines(null, -1));
    }

    @Test
    void linesOfAnotherModAreRecognised() {
        assertTrue(BazaarHelpers.hasPriceLines(List.of("SELL Enchanted Brown Mushroom", "Click to view options!", "Bazaar Buy: 550", "Bazaar Sell: 694")));
        assertTrue(BazaarHelpers.hasPriceLines(List.of("Bazaar Sell: 694")));
        assertFalse(BazaarHelpers.hasPriceLines(List.of("SELL Sprawl Shard", "Worth 19.2M coins", "Click to view options!")));
        assertFalse(BazaarHelpers.hasPriceLines(List.of("NPC Sell: 560")), "NPC allein zaehlt nicht");
        assertFalse(BazaarHelpers.hasPriceLines(List.of()));
    }

    @Test
    void anOfferJustAboveTheTopIsOutbid() {
        // Das billigste Angebot liegt bei 694.1, das Mittel bei 720: mit dem Mittel galt ein Angebot zu 700 noch nicht als ueberboten
        Order sell = new Order(false, "Enchanted Brown Mushroom", 70_000, false, 0, 700.0);
        assertEquals(WindowHighlights.GOLD, BazaarHelpers.colourFor(sell, new BazaarPrice(540, 720, 694.1, 549.9)));
        assertEquals(0, BazaarHelpers.colourFor(sell, new BazaarPrice(540, 720, 720, 540)), "mit dem Mittel war es unentdeckt");
    }

    @Test
    void aBuyOrderJustBelowTheTopIsOutbid() {
        Order buy = new Order(true, "Sprawl Shard", 339, false, 0, 57_127.6);
        assertEquals(WindowHighlights.GOLD, BazaarHelpers.colourFor(buy, new BazaarPrice(57_000, 58_000, 58_500, 57_127.7)));
        assertEquals(0, BazaarHelpers.colourFor(buy, new BazaarPrice(57_000, 58_000, 58_500, 57_127.6)), "gleichauf zaehlt nicht");
    }
}
