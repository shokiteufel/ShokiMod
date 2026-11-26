package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.util.ItemValue.BazaarPrice;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Der frische Basarpreis (2.4.17): die Spitzen des Auftragsbuchs gehen nicht mehr in den Mitteln unter. */
class ItemValueCombineTest {

    @Test
    void theExactTopsReplaceTheAverages() {
        // Mitte: Sofortkauf 720, Sofortverkauf 540; Spitzen: billigstes Angebot 694.1, hoechster Auftrag 549.9
        BazaarPrice p = ItemValue.combine(new long[] {720, 540}, 694.1, 549.9);
        assertEquals(540.0, p.instantSell());
        assertEquals(720.0, p.sellOrder());
        assertEquals(694.1, p.cheapestOffer());
        assertEquals(549.9, p.highestBid());
    }

    @Test
    void withoutTopsTheAveragesStandIn() {
        BazaarPrice p = ItemValue.combine(new long[] {720, 540}, 0.0, 0.0);
        assertEquals(720.0, p.cheapestOffer());
        assertEquals(540.0, p.highestBid());
    }

    @Test
    void eachSideIsDecidedOnItsOwn() {
        BazaarPrice p = ItemValue.combine(new long[] {720, 540}, 694.1, 0.0);
        assertEquals(694.1, p.cheapestOffer());
        assertEquals(540.0, p.highestBid());
    }
}
