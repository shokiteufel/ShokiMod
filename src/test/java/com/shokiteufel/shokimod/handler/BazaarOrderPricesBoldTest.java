package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.util.ItemValue.BazaarPrice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HUD > Bold numbers bei Order prices: Die Zahl in den Preiszeilen unter einem Auftrag steht fett, wenn der Aufrufer es verlangt; ohne ist der Text der alte
 * (BazaarOrderPricesTest prueft ihn weiter), und coins() selbst bekommt nie ein "§l".
 */
class BazaarOrderPricesBoldTest {

    private static final BazaarPrice PRICE = new BazaarPrice(550, 694, 694, 550);

    @Test
    void theNumbersAreBoldWhenAskedFor() {
        assertEquals(List.of("§6Bazaar Buy: §6§l550", "§6Bazaar Sell: §6§l694", "§6NPC Sell: §6§l560"), BazaarHelpers.priceLines(PRICE, 560, true));
    }

    @Test
    void withoutBoldTheLinesAreTheOldOnes() {
        assertEquals(BazaarHelpers.priceLines(PRICE, 560), BazaarHelpers.priceLines(PRICE, 560, false));
        assertEquals(List.of("§6Bazaar Buy: §6550", "§6Bazaar Sell: §6694", "§6NPC Sell: §6560"), BazaarHelpers.priceLines(PRICE, 560, false));
    }

    @Test
    void aMissingNumberStillLeavesItsLineOutInBold() {
        assertEquals(List.of("§6NPC Sell: §6§l560"), BazaarHelpers.priceLines(null, 560, true));
        assertEquals(List.of(), BazaarHelpers.priceLines(null, -1, true));
    }

    @Test
    void theCoinsFunctionStaysPlainBecauseOtherPartsUseItAsText() {
        assertEquals("12,346", BazaarHelpers.coins(12_345.6));
        assertEquals("550", BazaarHelpers.coins(550));
    }
}
